package com.example.relaychat.core.network

import com.example.relaychat.core.model.AppSettings
import com.example.relaychat.core.model.ImageGenerationOptions
import com.example.relaychat.core.model.ProviderPreset
import com.example.relaychat.core.model.RuntimeChatConfiguration
import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class ImageGenerationServiceTest {
    private lateinit var server: MockWebServer
    private lateinit var imageDirectory: Path

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        imageDirectory = Files.createTempDirectory("relaychat-image-test")
    }

    @After
    fun tearDown() {
        server.shutdown()
        imageDirectory.toFile().deleteRecursively()
    }

    @Test
    fun generatePostsImagesRequestSavesPngAndReturnsAssistantAttachment() = runTest {
        val pngBytes = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("x-request-id", "req_img_123")
                .setBody(
                    """
                    {
                      "created": 1760000000,
                      "data": [
                        { "b64_json": "${Base64.getEncoder().encodeToString(pngBytes)}" }
                      ]
                    }
                    """.trimIndent()
                )
        )
        val service = ImageGenerationService(
            client = OkHttpClient(),
            imageStore = FileGeneratedImageStore(imageDirectory.toFile()),
        )
        val runtime = imageRuntime(
            size = "1536x1024",
            quality = "high",
        )

        val result = service.generate(
            prompt = "A clean product render of a glass teapot on a white table",
            runtime = runtime,
        )

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/v1/images/generations")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-api-key")
        assertThat(request.getHeader("Content-Type")).isEqualTo("application/json; charset=utf-8")
        assertThat(request.getHeader("Accept")).isEqualTo("text/event-stream")
        assertThat(request.getHeader("Cache-Control")).isEqualTo("no-cache")

        val payload = parseJsonObject(request.body.readUtf8())
        assertThat(payload["model"]).isEqualTo(JsonPrimitive("gpt-image-2"))
        assertThat(payload["prompt"]).isEqualTo(JsonPrimitive("A clean product render of a glass teapot on a white table"))
        assertThat(payload["n"]).isEqualTo(JsonPrimitive(1))
        assertThat(payload["size"]).isEqualTo(JsonPrimitive("1536x1024"))
        assertThat(payload["quality"]).isEqualTo(JsonPrimitive("high"))
        assertThat(payload["output_format"]).isEqualTo(JsonPrimitive("png"))
        assertThat(payload["stream"]).isEqualTo(JsonPrimitive(true))
        assertThat(payload["partial_images"]).isEqualTo(JsonPrimitive(2))
        assertThat(payload["background"]).isEqualTo(JsonPrimitive("auto"))

        assertThat(result.assistantText).contains("Image generated")
        assertThat(result.requestId).isEqualTo("req_img_123")
        assertThat(result.model).isEqualTo("gpt-image-2")
        assertThat(result.attachments).hasSize(1)
        assertThat(result.attachments.single().mimeType).isEqualTo("image/png")
        assertThat(result.attachments.single().data.asList()).containsExactlyElementsIn(pngBytes.asList()).inOrder()
        assertThat(result.attachments.single().filePath).endsWith(".png")
        assertThat(result.imageGeneration?.prompt).isEqualTo("A clean product render of a glass teapot on a white table")
        assertThat(result.imageGeneration?.model).isEqualTo("gpt-image-2")
        assertThat(result.imageGeneration?.size).isEqualTo("1536x1024")
        assertThat(result.imageGeneration?.quality).isEqualTo("high")
        assertThat(result.imageGeneration?.imagePath).isEqualTo(result.attachments.single().filePath)
        assertThat(Files.readAllBytes(Path.of(result.imageGeneration!!.imagePath)).asList())
            .containsExactlyElementsIn(pngBytes.asList())
            .inOrder()
    }

    @Test
    fun generateParsesBase64ImageFromEventStreamResponse() = runTest {
        val pngBytes = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    event: response.output_item.done
                    data: {"type":"response.output_item.done","item":{"b64_json":"${Base64.getEncoder().encodeToString(pngBytes)}"}}

                    event: response.completed
                    data: {"type":"response.completed"}

                    """.trimIndent()
                )
        )
        val service = ImageGenerationService(
            client = OkHttpClient(),
            imageStore = FileGeneratedImageStore(imageDirectory.toFile()),
        )

        val result = service.generate(
            prompt = "a small white ceramic cat on a wooden desk, soft studio lighting",
            runtime = imageRuntime(),
        )

        assertThat(server.takeRequest().getHeader("Accept")).isEqualTo("text/event-stream")
        assertThat(result.attachments.single().mimeType).isEqualTo("image/png")
        assertThat(result.attachments.single().data.asList())
            .containsExactlyElementsIn(pngBytes.asList())
            .inOrder()
    }

    @Test
    fun generateRedactsApiKeyFromEventStreamErrorMessage() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    event: error
                    data: {"type":"error","message":"upstream rejected bearer test-api-key"}

                    """.trimIndent()
                )
        )
        val service = ImageGenerationService(
            client = OkHttpClient(),
            imageStore = FileGeneratedImageStore(imageDirectory.toFile()),
        )

        val error = catchImageRequestFailure {
            service.generate(prompt = "test prompt", runtime = imageRuntime())
        }

        assertThat(error.message).contains("[redacted]")
        assertThat(error.message).doesNotContain("test-api-key")
    }

    @Test
    fun defaultImageClientAllowsLongRunningImageStreams() {
        val client = defaultImageGenerationClient()

        assertThat(client.connectTimeoutMillis).isEqualTo(30_000)
        assertThat(client.readTimeoutMillis).isEqualTo(0)
    }

    @Test
    fun generateMaps401And403ToHelpfulAccessMessages() = runTest {
        val service = ImageGenerationService(
            client = OkHttpClient(),
            imageStore = FileGeneratedImageStore(imageDirectory.toFile()),
        )

        server.enqueue(errorResponse(401, "Incorrect API key provided: test-api-key"))
        val unauthorized = catchImageRequestFailure {
            service.generate(prompt = "test prompt", runtime = imageRuntime())
        }
        assertThat(unauthorized.status).isEqualTo(401)
        assertThat(unauthorized.message).contains("API key")
        assertThat(unauthorized.message).doesNotContain("test-api-key")

        server.enqueue(errorResponse(403, "Project does not have access to model gpt-image-2."))
        val forbidden = catchImageRequestFailure {
            service.generate(prompt = "test prompt", runtime = imageRuntime())
        }
        assertThat(forbidden.status).isEqualTo(403)
        assertThat(forbidden.message).contains("gpt-image-2")
        assertThat(forbidden.message).contains("access")
    }

    @Test
    fun generateMaps429ToRateLimitMessage() = runTest {
        val service = ImageGenerationService(
            client = OkHttpClient(),
            imageStore = FileGeneratedImageStore(imageDirectory.toFile()),
        )
        server.enqueue(errorResponse(429, "Rate limit reached."))

        val error = catchImageRequestFailure {
            service.generate(prompt = "test prompt", runtime = imageRuntime())
        }

        assertThat(error.status).isEqualTo(429)
        assertThat(error.message).contains("rate limit")
    }

    @Test
    fun generateMaps5xxToRetryLaterMessage() = runTest {
        val service = ImageGenerationService(
            client = OkHttpClient(),
            imageStore = FileGeneratedImageStore(imageDirectory.toFile()),
        )
        server.enqueue(errorResponse(502, "Bad gateway."))

        val error = catchImageRequestFailure {
            service.generate(prompt = "test prompt", runtime = imageRuntime())
        }

        assertThat(error.status).isEqualTo(502)
        assertThat(error.message).contains("server error")
        assertThat(error.message).contains("try again")
    }

    @Test
    fun generateFailsWhenResponseHasNoBase64ImageData() = runTest {
        val service = ImageGenerationService(
            client = OkHttpClient(),
            imageStore = FileGeneratedImageStore(imageDirectory.toFile()),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"created":1760000000,"data":[{}]}""")
        )

        val error = catchMissingImageData {
            service.generate(prompt = "test prompt", runtime = imageRuntime())
        }

        assertThat(error.message).contains("image data")
    }

    private fun imageRuntime(
        size: String = "1024x1024",
        quality: String = "auto",
    ): RuntimeChatConfiguration {
        val baseUrl = server.url("/v1").toString().trimEnd('/')
        return RuntimeChatConfiguration(
            settings = AppSettings(
                provider = ProviderPreset.OPENAI_IMAGE.profile.copy(baseUrl = baseUrl),
                imageGeneration = ImageGenerationOptions(size = size, quality = quality),
            ),
            apiKey = "test-api-key",
        )
    }

    private fun errorResponse(
        status: Int,
        message: String,
    ): MockResponse = MockResponse()
        .setResponseCode(status)
        .setBody("""{"error":{"message":"$message"}}""")

    private suspend fun catchImageRequestFailure(
        block: suspend () -> Unit,
    ): ImageGenerationException.RequestFailed {
        return try {
            block()
            error("Expected ImageGenerationException.RequestFailed")
        } catch (error: ImageGenerationException.RequestFailed) {
            error
        }
    }

    private suspend fun catchMissingImageData(
        block: suspend () -> Unit,
    ): ImageGenerationException.MissingImageData {
        return try {
            block()
            error("Expected ImageGenerationException.MissingImageData")
        } catch (error: ImageGenerationException.MissingImageData) {
            error
        }
    }
}
