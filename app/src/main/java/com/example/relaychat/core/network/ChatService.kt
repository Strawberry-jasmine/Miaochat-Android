package com.example.relaychat.core.network

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.example.relaychat.core.model.ChatSendResult
import com.example.relaychat.core.model.ChatStreamEvent
import com.example.relaychat.core.model.ChatThread
import com.example.relaychat.core.model.ChatStreamLifecycleStage
import com.example.relaychat.core.model.ProviderApiStyle
import com.example.relaychat.core.model.ProviderProfile
import com.example.relaychat.core.model.RequestControls
import com.example.relaychat.core.model.RuntimeChatConfiguration
import com.example.relaychat.core.model.isIntelallocProvider
import com.google.android.gms.net.CronetProviderInstaller
import com.google.android.gms.tasks.Tasks
import com.google.net.cronet.okhttptransport.CronetCallFactory
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.IOException
import org.chromium.net.CronetEngine
import kotlinx.coroutines.suspendCancellableCoroutine

sealed class ChatServiceException(message: String) : IllegalStateException(message) {
    class InvalidJson(detail: String) : ChatServiceException("Invalid JSON configuration: $detail")

    object InvalidUrl : ChatServiceException("The configured endpoint URL is invalid.")

    object InvalidResponse : ChatServiceException("The server response was not a valid HTTP response.")

    class RequestFailed(
        val status: Int,
        detail: String,
    ) : ChatServiceException("Request failed with status $status: $detail")

    object EmptyAssistantMessage : ChatServiceException("The server response did not contain an assistant message.")

    object StreamingUnsupported : ChatServiceException("Streaming is not supported for the current provider configuration.")
}

class ChatService(
    context: Context? = null,
    private val client: OkHttpClient = OkHttpClient(),
) {
    private val appContext = context?.applicationContext
    private val compatibilityClients = mutableMapOf<List<Protocol>, OkHttpClient>()
    @Volatile
    private var cronetCallFactory: Call.Factory? = null

    suspend fun send(
        thread: ChatThread,
        runtime: RuntimeChatConfiguration,
        controls: RequestControls,
    ): ChatSendResult = withContext(Dispatchers.IO) {
        val requestStartedAtMs = SystemClock.elapsedRealtime()
        val preparedRequest = prepareRequest(thread, runtime, controls, stream = false)
        Log.i(
            TAG,
            "send request mode=non_stream transport=${preparedRequest.transport.name} " +
                "target=${transportTarget(runtime.settings.provider, preparedRequest.transport)} " +
                "payload=${payloadSummary(preparedRequest.payload)}"
        )
        when (preparedRequest.transport) {
            NetworkTransport.URL_CONNECTION -> sendWithUrlConnection(preparedRequest, runtime, requestStartedAtMs)
            NetworkTransport.OKHTTP, NetworkTransport.CRONET -> sendWithCallFactory(
                preparedRequest,
                runtime,
                requestStartedAtMs,
            )
        }
    }

    fun stream(
        thread: ChatThread,
        runtime: RuntimeChatConfiguration,
        controls: RequestControls,
    ): Flow<ChatStreamEvent> {
        val provider = runtime.settings.provider
        if (!provider.supportsStreaming || provider.apiStyle != ProviderApiStyle.RESPONSES) {
            throw ChatServiceException.StreamingUnsupported
        }

        val telemetryTracker = RequestTelemetryTracker(SystemClock.elapsedRealtime())
        val preparedRequest = prepareRequest(thread, runtime, controls, stream = true)
        Log.i(
            TAG,
            "send request mode=stream transport=${preparedRequest.transport.name} " +
                "target=${transportTarget(provider, preparedRequest.transport)} " +
                "payload=${payloadSummary(preparedRequest.payload)}"
        )
        return when (preparedRequest.transport) {
            NetworkTransport.URL_CONNECTION -> streamWithUrlConnection(preparedRequest, provider, telemetryTracker)
            NetworkTransport.OKHTTP, NetworkTransport.CRONET -> streamWithCallFactory(
                preparedRequest,
                provider,
                telemetryTracker,
            )
        }.flowOn(Dispatchers.IO)
    }

    private suspend fun sendWithCallFactory(
        preparedRequest: PreparedRequest,
        runtime: RuntimeChatConfiguration,
        requestStartedAtMs: Long,
    ): ChatSendResult {
        val response = callFactoryFor(runtime.settings.provider, preparedRequest.transport)
            .newCall(preparedRequest.request)
            .await()
        response.use { httpResponse ->
            val headersAtMs = SystemClock.elapsedRealtime()
            val requestId = headerValue("x-request-id", httpResponse) ?: headerValue("request-id", httpResponse)
            val bodyText = httpResponse.body?.string().orEmpty()
            val parsedElement = runCatching { parseJsonElement(bodyText) }.getOrNull()
            Log.i(
                TAG,
                "send response mode=non_stream transport=${preparedRequest.transport.name} " +
                    "code=${httpResponse.code} requestId=${requestId ?: "none"} " +
                    "headersMs=${headersAtMs - requestStartedAtMs}"
            )

            if (!httpResponse.isSuccessful) {
                val message = parsedElement?.let(::extractErrorMessage)
                    ?: bodyText.ifBlank { "Unknown server error" }
                Log.w(
                    TAG,
                    "send failed mode=non_stream transport=${preparedRequest.transport.name} " +
                        "code=${httpResponse.code} detail=${message.take(400)}"
                )
                throw ChatServiceException.RequestFailed(httpResponse.code, message)
            }

            val result = parsedElement?.let(ChatResponseParser::parse)
                ?: throw ChatServiceException.InvalidResponse
            if (result.assistantText.trim().isEmpty()) {
                throw ChatServiceException.EmptyAssistantMessage
            }

            Log.i(
                TAG,
                "send timing mode=non_stream transport=${preparedRequest.transport.name} " +
                    "totalMs=${SystemClock.elapsedRealtime() - requestStartedAtMs}"
            )
            return result.copy(
                requestId = requestId,
                model = result.model ?: runtime.settings.provider.model,
            )
        }
    }

    private fun streamWithCallFactory(
        preparedRequest: PreparedRequest,
        provider: ProviderProfile,
        telemetryTracker: RequestTelemetryTracker,
    ): Flow<ChatStreamEvent> = flow {
        callFactoryFor(provider, preparedRequest.transport).newCall(preparedRequest.request).execute().use { response ->
            val headersAtMs = SystemClock.elapsedRealtime()
            telemetryTracker.markHeaders(headersAtMs)
            Log.i(
                TAG,
                "send response mode=stream transport=${preparedRequest.transport.name} code=${response.code} " +
                    "headersMs=${headersAtMs - telemetryTracker.requestStartedAtMs}"
            )
            if (!response.isSuccessful) {
                telemetryTracker.markCompleted(SystemClock.elapsedRealtime())
                val detail = response.body?.string().orEmpty().ifBlank {
                    "Streaming request failed before a valid event stream was established."
                }
                Log.w(
                    TAG,
                    "send failed mode=stream transport=${preparedRequest.transport.name} " +
                        "code=${response.code} detail=${detail.take(400)}"
                )
                throw ChatServiceException.RequestFailed(
                    status = response.code,
                    detail = detail,
                )
            }

            val requestId = headerValue("x-request-id", response) ?: headerValue("request-id", response)
            val source = response.body?.source() ?: throw ChatServiceException.InvalidResponse
            emitStreamEvents(
                provider = provider,
                requestId = requestId,
                nextLine = { if (source.exhausted()) null else source.readUtf8Line() },
                errorStatus = response.code,
                transport = preparedRequest.transport,
                telemetryTracker = telemetryTracker,
            )
        }
    }

    private suspend fun sendWithUrlConnection(
        preparedRequest: PreparedRequest,
        runtime: RuntimeChatConfiguration,
        requestStartedAtMs: Long,
    ): ChatSendResult {
        val connection = openUrlConnection(preparedRequest)
        return try {
            val status = connection.responseCode
            val headersAtMs = SystemClock.elapsedRealtime()
            val requestId = connection.getHeaderField("x-request-id") ?: connection.getHeaderField("request-id")
            val bodyText = readConnectionBody(connection)
            val parsedElement = runCatching { parseJsonElement(bodyText) }.getOrNull()
            Log.i(
                TAG,
                "send response mode=non_stream transport=${preparedRequest.transport.name} " +
                    "code=$status requestId=${requestId ?: "none"} " +
                    "headersMs=${headersAtMs - requestStartedAtMs}"
            )

            if (status !in 200..299) {
                val message = parsedElement?.let(::extractErrorMessage)
                    ?: bodyText.ifBlank { "Unknown server error" }
                Log.w(
                    TAG,
                    "send failed mode=non_stream transport=${preparedRequest.transport.name} " +
                        "code=$status detail=${message.take(400)}"
                )
                throw ChatServiceException.RequestFailed(status, message)
            }

            val result = parsedElement?.let(ChatResponseParser::parse)
                ?: throw ChatServiceException.InvalidResponse
            if (result.assistantText.trim().isEmpty()) {
                throw ChatServiceException.EmptyAssistantMessage
            }

            Log.i(
                TAG,
                "send timing mode=non_stream transport=${preparedRequest.transport.name} " +
                    "totalMs=${SystemClock.elapsedRealtime() - requestStartedAtMs}"
            )
            result.copy(
                requestId = requestId,
                model = result.model ?: runtime.settings.provider.model,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun streamWithUrlConnection(
        preparedRequest: PreparedRequest,
        provider: ProviderProfile,
        telemetryTracker: RequestTelemetryTracker,
    ): Flow<ChatStreamEvent> = flow {
        val connection = openUrlConnection(preparedRequest)
        try {
            val status = connection.responseCode
            val headersAtMs = SystemClock.elapsedRealtime()
            telemetryTracker.markHeaders(headersAtMs)
            Log.i(
                TAG,
                "send response mode=stream transport=${preparedRequest.transport.name} code=$status " +
                    "headersMs=${headersAtMs - telemetryTracker.requestStartedAtMs}"
            )
            if (status !in 200..299) {
                telemetryTracker.markCompleted(SystemClock.elapsedRealtime())
                val detail = readConnectionBody(connection).ifBlank {
                    "Streaming request failed before a valid event stream was established."
                }
                Log.w(
                    TAG,
                    "send failed mode=stream transport=${preparedRequest.transport.name} " +
                        "code=$status detail=${detail.take(400)}"
                )
                throw ChatServiceException.RequestFailed(status, detail)
            }

            val requestId = connection.getHeaderField("x-request-id") ?: connection.getHeaderField("request-id")
            connection.inputStream.bufferedReader().use { reader ->
                emitStreamEvents(
                    provider = provider,
                    requestId = requestId,
                    nextLine = reader::readLine,
                    errorStatus = status,
                    transport = preparedRequest.transport,
                    telemetryTracker = telemetryTracker,
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<ChatStreamEvent>.emitStreamEvents(
        provider: ProviderProfile,
        requestId: String?,
        nextLine: () -> String?,
        errorStatus: Int,
        transport: NetworkTransport,
        telemetryTracker: RequestTelemetryTracker,
    ) {
        var accumulatedText = ""
        var completed = false

        while (true) {
            val line = nextLine() ?: break
            if (!line.startsWith("data:")) {
                continue
            }

            val payload = line.removePrefix("data:").trim()
            if (payload.isEmpty()) {
                continue
            }

            if (payload == "[DONE]") {
                if (!completed && accumulatedText.isNotEmpty()) {
                    telemetryTracker.markCompleted(SystemClock.elapsedRealtime())
                    logStreamTimingSummary(transport, telemetryTracker)
                    emit(
                        ChatStreamEvent.Completed(
                            ChatSendResult(
                                assistantText = accumulatedText,
                                responseId = null,
                                requestId = requestId,
                                model = provider.model,
                            )
                        )
                    )
                }
                break
            }

            val event = runCatching { parseJsonElement(payload) as JsonObject }.getOrNull() ?: continue
            val eventType = event["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (eventType.isNotBlank()) {
                val eventAtMs = SystemClock.elapsedRealtime()
                telemetryTracker.markEvent(eventType, eventAtMs)
                logLifecycleEvent(
                    transport = transport,
                    telemetryTracker = telemetryTracker,
                    type = eventType,
                )
                val stage = classifyLifecycleStage(eventType)
                if (stage == ChatStreamLifecycleStage.REASONING || stage == ChatStreamLifecycleStage.TOOL) {
                    emit(
                        ChatStreamEvent.Lifecycle(
                            type = eventType,
                            stage = stage,
                            detail = lifecycleDetailForEvent(eventType),
                        )
                    )
                }
            }

            when (eventType) {
                "response.output_text.delta" -> {
                    val delta = event["delta"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (delta.isNotEmpty()) {
                        accumulatedText += delta
                        emit(ChatStreamEvent.TextDelta(delta))
                    }
                }

                "response.completed" -> {
                    val responseObject = event["response"] ?: event
                    val parsed = ChatResponseParser.parse(responseObject)
                    val finalText = parsed.assistantText.ifBlank { accumulatedText }
                    if (finalText.isBlank()) {
                        throw ChatServiceException.EmptyAssistantMessage
                    }
                    telemetryTracker.markCompleted(SystemClock.elapsedRealtime())
                    logStreamTimingSummary(transport, telemetryTracker)
                    emit(
                        ChatStreamEvent.Completed(
                            parsed.copy(
                                assistantText = finalText,
                                requestId = requestId,
                                model = parsed.model ?: provider.model,
                            )
                        )
                    )
                    completed = true
                    break
                }

                "error" -> {
                    telemetryTracker.markCompleted(SystemClock.elapsedRealtime())
                    throw ChatServiceException.RequestFailed(
                        status = errorStatus,
                        detail = extractErrorMessage(event) ?: "Unknown streaming error",
                    )
                }
            }
        }
    }

    private fun resolvedTransportFor(provider: ProviderProfile): NetworkTransport {
        val preferred = preferredTransportFor(provider)
        return when (preferred) {
            NetworkTransport.OKHTTP -> NetworkTransport.OKHTTP
            NetworkTransport.URL_CONNECTION -> NetworkTransport.URL_CONNECTION

            NetworkTransport.CRONET -> {
                val cronetFactory = cronetCallFactoryOrNull()
                if (cronetFactory != null) {
                    NetworkTransport.CRONET
                } else {
                    Log.w(TAG, "Cronet unavailable at request time, falling back to OkHttp.")
                    NetworkTransport.OKHTTP
                }
            }
        }
    }

    private fun callFactoryFor(
        provider: ProviderProfile,
        transport: NetworkTransport,
    ): Call.Factory = when (transport) {
        NetworkTransport.OKHTTP -> okHttpClientFor(provider)
        NetworkTransport.CRONET -> cronetCallFactoryOrNull() ?: okHttpClientFor(provider)
        NetworkTransport.URL_CONNECTION -> error("HttpURLConnection transport does not expose an OkHttp Call.Factory.")
    }

    private fun okHttpClientFor(provider: ProviderProfile): OkHttpClient {
        val protocols = protocolsFor(provider) ?: return client
        return synchronized(compatibilityClients) {
            compatibilityClients.getOrPut(protocols) {
                client.newBuilder()
                    .protocols(protocols)
                    .build()
            }
        }
    }

    private fun cronetCallFactoryOrNull(): Call.Factory? {
        cronetCallFactory?.let { return it }
        val context = appContext ?: return null

        return synchronized(this) {
            cronetCallFactory?.let { return@synchronized it }

            runCatching {
                Tasks.await(CronetProviderInstaller.installProvider(context), 20, TimeUnit.SECONDS)
                buildCronetCallFactory(context)
            }.onFailure { error ->
                Log.w(TAG, "Google Play Services Cronet unavailable, trying embedded Cronet.", error)
            }.recoverCatching {
                buildCronetCallFactory(context)
            }.onFailure { error ->
                Log.w(TAG, "Embedded Cronet initialization failed, falling back to OkHttp.", error)
            }.getOrNull()?.also { factory ->
                Log.i(TAG, "Initialized Cronet call factory: ${factory.javaClass.name}")
                cronetCallFactory = factory
            }
        }
    }

    private fun buildCronetCallFactory(context: Context): Call.Factory {
        val engine = CronetEngine.Builder(context).build()
        return CronetCallFactory.newBuilder(engine).build()
    }

    private fun prepareRequest(
        thread: ChatThread,
        runtime: RuntimeChatConfiguration,
        controls: RequestControls,
        stream: Boolean,
    ): PreparedRequest {
        val provider = runtime.settings.provider
        val url = EndpointResolver.buildUrl(provider.baseUrl, provider.path)
            ?: throw ChatServiceException.InvalidUrl
        val payload = runCatching {
            ChatPayloadBuilder.build(thread, provider, controls, stream)
        }.getOrElse { error ->
            throw ChatServiceException.InvalidJson(error.message.orEmpty())
        }

        val bodyBytes = RelayChatJson.instance
            .encodeToString(JsonObject.serializer(), payload)
            .toByteArray()
        val body = bodyBytes.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .header("Content-Type", "application/json")
            .apply {
                if (runtime.apiKey.isNotBlank()) {
                    header("Authorization", "Bearer ${runtime.apiKey}")
                }
                parseHeaders(provider.extraHeaders).forEach { (name, value) ->
                    header(name, value)
                }
            }
            .build()

        val resolvedTransport = resolvedTransportFor(provider)
        return PreparedRequest(
            request = request,
            payload = payload,
            transport = resolvedTransport,
            bodyBytes = bodyBytes,
        )
    }

    private fun transportTarget(
        provider: ProviderProfile,
        transport: NetworkTransport,
    ): String = when (transport) {
        NetworkTransport.URL_CONNECTION -> HttpURLConnection::class.java.name
        NetworkTransport.OKHTTP, NetworkTransport.CRONET -> callFactoryFor(provider, transport)::class.java.name
    }

    private fun openUrlConnection(preparedRequest: PreparedRequest): HttpURLConnection {
        val connection = URL(preparedRequest.request.url.toString()).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 30_000
        connection.readTimeout = 120_000
        connection.instanceFollowRedirects = true
        connection.useCaches = false
        connection.doOutput = true
        connection.setFixedLengthStreamingMode(preparedRequest.bodyBytes.size)
        preparedRequest.request.headers.names().forEach { name ->
            connection.setRequestProperty(name, preparedRequest.request.header(name))
        }
        connection.outputStream.use { output ->
            output.write(preparedRequest.bodyBytes)
        }
        return connection
    }

    private fun readConnectionBody(connection: HttpURLConnection): String {
        val stream = connection.errorStream ?: runCatching { connection.inputStream }.getOrNull() ?: return ""
        return stream.bufferedReader().use { it.readText() }
    }

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

    private fun headerValue(
        name: String,
        response: Response,
    ): String? = response.header(name)

    private fun extractErrorMessage(value: JsonElement?): String? {
        val dictionary = value as? JsonObject ?: return null
        val error = dictionary["error"] as? JsonObject
        if (error != null) {
            error["message"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { return it }
            flattenTextContent(error)?.let { return it }
        }
        return flattenTextContent(dictionary)
    }

    private fun flattenTextContent(value: JsonElement?): String? = when (value) {
        null -> null
        is JsonPrimitive -> value.contentOrNull?.takeIf { it.isNotEmpty() }
        is JsonObject -> {
            value["text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }
                ?: value["delta"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }
                ?: flattenTextContent(value["content"])
                ?: value["output_text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }
                ?: value["message"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }
        }

        is JsonArray -> {
            val parts = value.mapNotNull { flattenTextContent(it) }
            if (parts.isEmpty()) null else parts.joinToString("\n")
        }
    }

    private fun payloadSummary(payload: JsonObject): String {
        val inputText = flattenTextContent(payload["input"])?.replace('\n', ' ')?.take(120).orEmpty()
        val tools = (payload["tools"] as? JsonArray)?.size ?: 0
        val reasoning = (payload["reasoning"] as? JsonObject)?.let { reasoning ->
            buildList {
                add(reasoning["effort"]?.jsonPrimitive?.contentOrNull ?: "none")
                reasoning["summary"]?.jsonPrimitive?.contentOrNull?.let(::add)
            }.joinToString("/")
        } ?: "none"
        val verbosity = ((payload["text"] as? JsonObject)?.get("verbosity"))?.jsonPrimitive?.contentOrNull ?: "none"

        return buildList {
            add("model=${payload["model"]?.jsonPrimitive?.contentOrNull ?: "unknown"}")
            add("store=${payload["store"]?.jsonPrimitive?.contentOrNull ?: "unknown"}")
            add("reasoning=$reasoning")
            add("verbosity=$verbosity")
            add("tools=$tools")
            add("previousResponse=${payload["previous_response_id"] != null}")
            add("input=\"$inputText\"")
        }.joinToString(" ")
    }

    private fun logLifecycleEvent(
        transport: NetworkTransport,
        telemetryTracker: RequestTelemetryTracker,
        type: String,
    ) {
        val stage = classifyLifecycleStage(type)
        if (stage == ChatStreamLifecycleStage.OTHER) {
            return
        }
        val detail = lifecycleDetailForEvent(type) ?: "none"
        val elapsedMs = SystemClock.elapsedRealtime() - telemetryTracker.requestStartedAtMs
        Log.i(
            TAG,
            "stream event transport=${transport.name} t=${elapsedMs}ms type=$type " +
                "stage=${stage.name.lowercase()} detail=$detail"
        )
    }

    private fun logStreamTimingSummary(
        transport: NetworkTransport,
        telemetryTracker: RequestTelemetryTracker,
    ) {
        val snapshot = telemetryTracker.snapshot()
        Log.i(
            TAG,
            "stream timing transport=${transport.name} " +
                "totalMs=${formatMs(snapshot.totalDurationMs)} " +
                "requestToHeadersMs=${formatMs(snapshot.requestToHeadersMs)} " +
                "headersToFirstEventMs=${formatMs(snapshot.headersToFirstEventMs)} " +
                "headersToFirstTextMs=${formatMs(snapshot.headersToFirstTextMs)} " +
                "reasoningWindowMs=${formatMs(snapshot.reasoningWindowMs)} " +
                "toolWindowMs=${formatMs(snapshot.toolWindowMs)} " +
                "events=${snapshot.eventTypes.joinToString("|").take(240)}"
        )
    }
}

internal enum class NetworkTransport {
    OKHTTP,
    CRONET,
    URL_CONNECTION,
}

private data class PreparedRequest(
    val request: Request,
    val payload: JsonObject,
    val transport: NetworkTransport,
    val bodyBytes: ByteArray,
)

internal fun preferredTransportFor(provider: ProviderProfile): NetworkTransport =
    if (provider.isIntelallocProvider()) {
        NetworkTransport.URL_CONNECTION
    } else {
        NetworkTransport.OKHTTP
    }

internal fun protocolsFor(provider: ProviderProfile): List<Protocol>? =
    if (provider.isIntelallocProvider()) {
        listOf(Protocol.HTTP_1_1)
    } else {
        null
    }

private const val TAG = "RelayChatNetwork"

private fun formatMs(value: Long?): String = value?.toString() ?: "n/a"

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(
        object : Callback {
            override fun onFailure(
                call: Call,
                e: IOException,
            ) {
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
            }

            override fun onResponse(
                call: Call,
                response: Response,
            ) {
                continuation.resume(response)
            }
        }
    )

    continuation.invokeOnCancellation {
        cancel()
    }
}
