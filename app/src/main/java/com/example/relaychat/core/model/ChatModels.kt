package com.example.relaychat.core.model

import java.util.UUID

enum class ChatRole(val wireValue: String) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
    ;
}

data class ChatAttachment(
    val id: String = UUID.randomUUID().toString(),
    val mimeType: String,
    val data: ByteArray,
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatRole,
    val text: String,
    val attachments: List<ChatAttachment> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val remoteResponseId: String? = null,
    val requestId: String? = null,
    val model: String? = null,
)

data class ChatThread(
    val id: String = UUID.randomUUID().toString(),
    val title: String = LegacyDefaultThreadTitle,
    val messages: List<ChatMessage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastResponseId: String? = null,
) {
    companion object {
        val Empty = ChatThread()
    }
}

data class ChatSendResult(
    val assistantText: String,
    val responseId: String?,
    val requestId: String?,
    val model: String?,
)

enum class ChatStreamLifecycleStage {
    REASONING,
    TOOL,
    OUTPUT,
    OTHER,
}

sealed interface ChatStreamEvent {
    data class Lifecycle(
        val type: String,
        val stage: ChatStreamLifecycleStage,
        val detail: String? = null,
    ) : ChatStreamEvent

    data class TextDelta(val delta: String) : ChatStreamEvent

    data class Completed(val result: ChatSendResult) : ChatStreamEvent
}
