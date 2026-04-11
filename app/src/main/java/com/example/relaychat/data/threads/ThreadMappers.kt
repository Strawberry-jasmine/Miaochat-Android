package com.example.relaychat.data.threads

import com.example.relaychat.core.model.ChatAttachment
import com.example.relaychat.core.model.ChatMessage
import com.example.relaychat.core.model.ChatRole
import com.example.relaychat.core.model.ChatThread

internal fun ThreadWithMessages.toModel(): ChatThread = ChatThread(
    id = thread.id,
    title = thread.title,
    messages = messages
        .sortedBy { it.message.position }
        .map { messageWithAttachments ->
            ChatMessage(
                id = messageWithAttachments.message.id,
                role = ChatRole.valueOf(messageWithAttachments.message.role),
                text = messageWithAttachments.message.text,
                attachments = messageWithAttachments.attachments
                    .sortedBy { it.position }
                    .map { attachment ->
                        ChatAttachment(
                            id = attachment.id,
                            mimeType = attachment.mimeType,
                            data = attachment.data,
                        )
                    },
                createdAt = messageWithAttachments.message.createdAt,
                remoteResponseId = messageWithAttachments.message.remoteResponseId,
                requestId = messageWithAttachments.message.requestId,
                model = messageWithAttachments.message.model,
            )
        },
    createdAt = thread.createdAt,
    updatedAt = thread.updatedAt,
    lastResponseId = thread.lastResponseId,
)

internal fun ChatThread.toEntity(): ThreadEntity = ThreadEntity(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastResponseId = lastResponseId,
)

internal fun ChatThread.toMessageEntities(): List<MessageEntity> = messages.mapIndexed { index, message ->
    MessageEntity(
        id = message.id,
        threadId = id,
        position = index,
        role = message.role.name,
        text = message.text,
        createdAt = message.createdAt,
        remoteResponseId = message.remoteResponseId,
        requestId = message.requestId,
        model = message.model,
    )
}

internal fun ChatThread.toAttachmentEntities(): List<AttachmentEntity> = messages.flatMap { message ->
    message.attachments.mapIndexed { index, attachment ->
        AttachmentEntity(
            id = attachment.id,
            messageId = message.id,
            position = index,
            mimeType = attachment.mimeType,
            data = attachment.data,
        )
    }
}
