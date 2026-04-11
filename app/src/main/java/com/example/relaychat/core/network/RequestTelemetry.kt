package com.example.relaychat.core.network

import com.example.relaychat.core.model.ChatStreamLifecycleStage

internal data class RequestTelemetry(
    val requestStartedAtMs: Long,
    val headersAtMs: Long? = null,
    val firstEventAtMs: Long? = null,
    val firstReasoningAtMs: Long? = null,
    val firstToolAtMs: Long? = null,
    val lastToolAtMs: Long? = null,
    val firstTextAtMs: Long? = null,
    val completedAtMs: Long? = null,
    val eventTypes: List<String> = emptyList(),
) {
    val requestToHeadersMs: Long?
        get() = headersAtMs?.minus(requestStartedAtMs)

    val headersToFirstEventMs: Long?
        get() = if (headersAtMs != null && firstEventAtMs != null) firstEventAtMs - headersAtMs else null

    val requestToFirstTextMs: Long?
        get() = firstTextAtMs?.minus(requestStartedAtMs)

    val headersToFirstTextMs: Long?
        get() = if (headersAtMs != null && firstTextAtMs != null) firstTextAtMs - headersAtMs else null

    val reasoningWindowMs: Long?
        get() = firstReasoningAtMs?.let { reasoningStart ->
            listOfNotNull(firstToolAtMs, firstTextAtMs, completedAtMs)
                .firstOrNull { it >= reasoningStart }
                ?.minus(reasoningStart)
        }

    val toolWindowMs: Long?
        get() = if (firstToolAtMs != null && lastToolAtMs != null) lastToolAtMs - firstToolAtMs else null

    val totalDurationMs: Long?
        get() = completedAtMs?.minus(requestStartedAtMs)
}

internal class RequestTelemetryTracker(
    val requestStartedAtMs: Long,
) {
    private var headersAtMs: Long? = null
    private var firstEventAtMs: Long? = null
    private var firstReasoningAtMs: Long? = null
    private var firstToolAtMs: Long? = null
    private var lastToolAtMs: Long? = null
    private var firstTextAtMs: Long? = null
    private var completedAtMs: Long? = null
    private val eventTypes = mutableListOf<String>()

    fun markHeaders(atMs: Long) {
        if (headersAtMs == null) {
            headersAtMs = atMs
        }
    }

    fun markEvent(
        type: String,
        atMs: Long,
    ) {
        if (firstEventAtMs == null) {
            firstEventAtMs = atMs
        }
        eventTypes += type

        when (classifyLifecycleStage(type)) {
            ChatStreamLifecycleStage.REASONING -> {
                if (firstReasoningAtMs == null) {
                    firstReasoningAtMs = atMs
                }
            }

            ChatStreamLifecycleStage.TOOL -> {
                if (firstToolAtMs == null) {
                    firstToolAtMs = atMs
                }
                lastToolAtMs = atMs
            }

            ChatStreamLifecycleStage.OUTPUT -> {
                if (firstTextAtMs == null) {
                    firstTextAtMs = atMs
                }
            }

            ChatStreamLifecycleStage.OTHER -> Unit
        }
    }

    fun markCompleted(atMs: Long) {
        if (completedAtMs == null) {
            completedAtMs = atMs
        }
    }

    fun snapshot(): RequestTelemetry = RequestTelemetry(
        requestStartedAtMs = requestStartedAtMs,
        headersAtMs = headersAtMs,
        firstEventAtMs = firstEventAtMs,
        firstReasoningAtMs = firstReasoningAtMs,
        firstToolAtMs = firstToolAtMs,
        lastToolAtMs = lastToolAtMs,
        firstTextAtMs = firstTextAtMs,
        completedAtMs = completedAtMs,
        eventTypes = eventTypes.toList(),
    )
}

internal fun classifyLifecycleStage(type: String): ChatStreamLifecycleStage {
    val normalized = type.lowercase()
    return when {
        "reasoning" in normalized -> ChatStreamLifecycleStage.REASONING
        "web_search" in normalized ||
            "file_search" in normalized ||
            "function_call" in normalized ||
            ".tool" in normalized ||
            "tool_" in normalized -> ChatStreamLifecycleStage.TOOL
        "output_text" in normalized ||
            "content_part" in normalized ||
            normalized.endsWith(".completed") -> ChatStreamLifecycleStage.OUTPUT
        else -> ChatStreamLifecycleStage.OTHER
    }
}

internal fun lifecycleDetailForEvent(type: String): String? {
    val normalized = type.lowercase()
    return when (classifyLifecycleStage(type)) {
        ChatStreamLifecycleStage.REASONING -> "reasoning"
        ChatStreamLifecycleStage.TOOL -> if ("web_search" in normalized) "searching web" else "running tool"
        ChatStreamLifecycleStage.OUTPUT -> "drafting reply"
        ChatStreamLifecycleStage.OTHER -> null
    }
}
