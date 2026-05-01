package com.example.relaychat.core.network

import com.example.relaychat.core.model.ChatAttachment
import com.example.relaychat.core.model.ChatSendResult
import com.example.relaychat.core.model.ImageGenerationMetadata
import com.example.relaychat.core.model.ImageGenerationOptions
import com.example.relaychat.core.model.RuntimeChatConfiguration
import java.io.File
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

sealed class ImageGenerationException(message: String) : IllegalStateException(message) {
    object InvalidUrl : ImageGenerationException("The configured Images API endpoint URL is invalid.")

    object InvalidResponse : ImageGenerationException("The Images API response was not a valid HTTP response.")

    class RequestFailed(
        val status: Int,
        detail: String,
    ) : ImageGenerationException(detail)

    object MissingImageData : ImageGenerationException(
        "The Images API response did not contain base64 image data.",
    )
}

data class GeneratedImageFile(
    val path: String,
    val data: ByteArray,
)

interface GeneratedImageStore {
    fun savePng(
        data: ByteArray,
        createdAt: Long,
    ): GeneratedImageFile
}

class FileGeneratedImageStore(
    private val directory: File,
) : GeneratedImageStore {
    override fun savePng(
        data: ByteArray,
        createdAt: Long,
    ): GeneratedImageFile {
        directory.mkdirs()
        val file = File(directory, "image-$createdAt-${UUID.randomUUID()}.png")
        file.writeBytes(data)
        return GeneratedImageFile(path = file.absolutePath, data = data)
    }
}

class ImageGenerationService(
    private val client: OkHttpClient = defaultImageGenerationClient(),
    private val imageStore: GeneratedImageStore,
) {
    suspend fun generate(
        prompt: String,
        runtime: RuntimeChatConfiguration,
    ): ChatSendResult = withContext(Dispatchers.IO) {
        val provider = runtime.settings.provider
        val url = EndpointResolver.buildUrl(provider.baseUrl, provider.path)
            ?: throw ImageGenerationException.InvalidUrl
        val normalizedPrompt = prompt.trim()
        if (normalizedPrompt.isEmpty()) {
            throw ImageGenerationException.MissingImageData
        }

        val options = runtime.settings.imageGeneration.normalized()
        val payload = buildImagePayload(
            prompt = normalizedPrompt,
            model = provider.model.ifBlank { DEFAULT_IMAGE_MODEL },
            options = options,
        )
        val bodyText = RelayChatJson.instance.encodeToString(JsonObject.serializer(), payload)
        val request = Request.Builder()
            .url(url)
            .post(bodyText.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Content-Type", "application/json; charset=utf-8")
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .apply {
                if (runtime.apiKey.isNotBlank()) {
                    header("Authorization", "Bearer ${runtime.apiKey}")
                }
                parseHeaders(provider.extraHeaders).forEach { (name, value) ->
                    header(name, value)
                }
            }
            .build()

        client.newCall(request).execute().use { response ->
            val requestId = response.header("x-request-id") ?: response.header("request-id")
            val body = response.body?.string().orEmpty()
            val parsed = runCatching { parseJsonElement(body) }.getOrNull()

            if (!response.isSuccessful) {
                val detail = sanitizeDetail(
                    detail = parsed?.let(::extractErrorMessage) ?: body.ifBlank { "Unknown server error" },
                    apiKey = runtime.apiKey,
                )
                throw ImageGenerationException.RequestFailed(
                    status = response.code,
                    detail = userFacingFailureMessage(
                        status = response.code,
                        model = provider.model.ifBlank { DEFAULT_IMAGE_MODEL },
                        detail = detail,
                    ),
                )
            }

            val base64Image = parsed?.let(::extractBase64ImageData)
                ?: extractBase64ImageDataFromEventStream(
                    body = body,
                    apiKey = runtime.apiKey,
                )
                ?: throw ImageGenerationException.MissingImageData
            val imageBytes = runCatching { Base64.getDecoder().decode(base64Image) }
                .getOrElse { throw ImageGenerationException.MissingImageData }
            val createdAt = System.currentTimeMillis()
            val saved = imageStore.savePng(imageBytes, createdAt)
            val model = provider.model.ifBlank { DEFAULT_IMAGE_MODEL }
            val metadata = ImageGenerationMetadata(
                prompt = normalizedPrompt,
                model = model,
                size = options.size,
                quality = options.quality,
                imagePath = saved.path,
                createdAt = createdAt,
            )

            ChatSendResult(
                assistantText = assistantSummary(metadata),
                responseId = null,
                requestId = requestId,
                model = model,
                attachments = listOf(
                    ChatAttachment(
                        mimeType = "image/png",
                        data = saved.data,
                        filePath = saved.path,
                    )
                ),
                imageGeneration = metadata,
            )
        }
    }

    private fun buildImagePayload(
        prompt: String,
        model: String,
        options: ImageGenerationOptions,
    ): JsonObject = buildJsonObject {
        put("model", model)
        put("prompt", prompt)
        put("n", 1)
        put("size", options.size)
        put("quality", options.quality)
        put("output_format", options.outputFormat)
        put("stream", true)
        put("partial_images", 2)
        options.background.takeIf { it.isNotBlank() }?.let { put("background", it) }
    }

    private fun extractBase64ImageData(value: JsonElement): String? {
        val root = value as? JsonObject ?: return null
        return extractBase64ImageDataFromObject(root)
    }

    private fun extractBase64ImageDataFromObject(root: JsonObject): String? {
        firstNonBlankString(root, "b64_json", "partial_image_b64", "partial_image", "image")?.let {
            return it
        }

        val data = root["data"] as? JsonArray
        data?.firstOrNullNotNull { item ->
            (item as? JsonObject)?.let { firstNonBlankString(it, "b64_json", "image") }
        }?.let {
            return it
        }

        val output = root["output"] as? JsonArray
        return output?.firstOrNullNotNull { item ->
            (item as? JsonObject)?.let { candidate ->
                firstNonBlankString(candidate, "b64_json", "image")
                    ?: (candidate["item"] as? JsonObject)?.let { firstNonBlankString(it, "b64_json", "image") }
            }
        }
    }

    private fun extractBase64ImageDataFromEventStream(
        body: String,
        apiKey: String,
    ): String? {
        if (!body.looksLikeEventStream()) {
            return null
        }

        val eventData = StringBuilder()
        body.lineSequence().forEach { line ->
            if (line.isBlank()) {
                processStreamEventData(
                    rawData = eventData.toString(),
                    apiKey = apiKey,
                )?.let { return it }
                eventData.clear()
            } else if (line.regionMatches(0, "data:", 0, 5, ignoreCase = true)) {
                eventData.appendLine(line.substring(5).trim())
            }
        }
        return processStreamEventData(
            rawData = eventData.toString(),
            apiKey = apiKey,
        )
    }

    private fun processStreamEventData(
        rawData: String,
        apiKey: String,
    ): String? {
        val data = rawData.trim()
        if (data.isBlank() || data == "[DONE]") {
            return null
        }
        val root = runCatching { parseJsonElement(data) as? JsonObject }.getOrNull() ?: return null
        val type = root["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (type.contains("partial", ignoreCase = true)) {
            return null
        }
        if (type.contains("error", ignoreCase = true) || type.contains("failed", ignoreCase = true)) {
            val detail = sanitizeDetail(
                detail = extractErrorMessage(root) ?: "Streaming image generation failed.",
                apiKey = apiKey,
            )
            throw ImageGenerationException.RequestFailed(
                status = 200,
                detail = "Streaming image generation failed. $detail",
            )
        }
        return extractBase64ImageDataFromObject(root)
            ?: (root["item"] as? JsonObject)?.let(::extractBase64ImageDataFromObject)
    }

    private fun extractErrorMessage(value: JsonElement): String? {
        val root = value as? JsonObject ?: return null
        val error = root["error"] as? JsonObject
        return error?.get("message")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: root["message"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: (value as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    }

    private fun userFacingFailureMessage(
        status: Int,
        model: String,
        detail: String,
    ): String = when (status) {
        401 -> "Image generation rejected the API key (401). Check the API key in Settings."
        403 -> "Image generation returned 403. This account may not have access to $model or image generation. $detail"
        429 -> "Image generation rate limit reached (429). Wait and try again. $detail"
        in 500..599 -> "Image generation server error ($status). Please try again later. $detail"
        else -> "Image generation request failed with status $status. $detail"
    }

    private fun assistantSummary(metadata: ImageGenerationMetadata): String =
        "Image generated.\nPrompt: ${metadata.prompt}\nSize: ${metadata.size}\nQuality: ${metadata.quality}"

    private fun ImageGenerationOptions.normalized(): ImageGenerationOptions = copy(
        size = size.trim().ifBlank { "1024x1024" },
        quality = quality.trim().ifBlank { "auto" },
        background = background.trim().ifBlank { "auto" },
        outputFormat = "png",
    )

    private fun parseHeaders(raw: String): List<Pair<String, String>> =
        raw.lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf(':')
                if (separator < 0) {
                    return@mapNotNull null
                }
                val name = line.substring(0, separator).trim()
                val value = line.substring(separator + 1).trim()
                if (name.isEmpty()) null else name to value
            }
            .toList()

    private fun sanitizeDetail(
        detail: String,
        apiKey: String,
    ): String {
        val trimmedKey = apiKey.trim()
        return if (trimmedKey.isBlank()) {
            detail
        } else {
            detail.replace(trimmedKey, "[redacted]")
        }
    }

    private companion object {
        private const val DEFAULT_IMAGE_MODEL = "gpt-image-2"
    }
}

private fun firstNonBlankString(
    root: JsonObject,
    vararg keys: String,
): String? = keys.firstNotNullOfOrNull { key ->
    root[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}

private fun String.looksLikeEventStream(): Boolean {
    val trimmed = trimStart()
    return trimmed.startsWith("event:") || trimmed.startsWith("data:")
}

fun defaultImageGenerationClient(): OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

private inline fun <T, R : Any> Iterable<T>.firstOrNullNotNull(transform: (T) -> R?): R? {
    for (item in this) {
        val transformed = transform(item)
        if (transformed != null) {
            return transformed
        }
    }
    return null
}
