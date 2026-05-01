package com.example.relaychat.core.model

import java.util.UUID
import kotlinx.serialization.Serializable

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
    val filePath: String? = null,
)

@Serializable
data class ImageGenerationOptions(
    val size: String = "1024x1024",
    val quality: String = "auto",
    val background: String = "auto",
    val outputFormat: String = "png",
)

@Serializable
data class ImageGenerationMetadata(
    val prompt: String,
    val model: String,
    val size: String,
    val quality: String,
    val imagePath: String,
    val createdAt: Long,
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
    val imageGeneration: ImageGenerationMetadata? = null,
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
    val attachments: List<ChatAttachment> = emptyList(),
    val imageGeneration: ImageGenerationMetadata? = null,
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
