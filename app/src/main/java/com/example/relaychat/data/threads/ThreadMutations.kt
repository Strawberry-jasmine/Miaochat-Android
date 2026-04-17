package com.example.relaychat.data.threads

import com.example.relaychat.core.model.ChatMessage
import com.example.relaychat.core.model.ChatRole
import com.example.relaychat.core.model.ChatThread
import com.example.relaychat.core.model.LegacyDefaultThreadTitle
import com.example.relaychat.core.model.isDefaultThreadTitle

object ThreadMutations {
    fun appendMessage(
        thread: ChatThread,
        message: ChatMessage,
        defaultTitle: String = LegacyDefaultThreadTitle,
    ): ChatThread {
        val updatedMessages = thread.messages + message
        val updatedTitle = if (
            isDefaultThreadTitle(thread.title, defaultTitle) &&
            message.role == ChatRole.USER &&
            message.text.trim().isNotEmpty()
        ) {
            message.text.trim().take(28)
        } else {
            thread.title
        }

        return thread.copy(
            title = updatedTitle,
            messages = updatedMessages,
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun removeLastTurn(
        thread: ChatThread,
        defaultTitle: String = LegacyDefaultThreadTitle,
    ): ChatThread {
        val mutableMessages = thread.messages.toMutableList()
        while (mutableMessages.lastOrNull()?.role == ChatRole.ASSISTANT) {
            mutableMessages.removeAt(mutableMessages.lastIndex)
        }
        if (mutableMessages.lastOrNull()?.role == ChatRole.USER) {
            mutableMessages.removeAt(mutableMessages.lastIndex)
        }

        return thread.copy(
            title = if (mutableMessages.isEmpty()) defaultTitle else thread.title,
            messages = mutableMessages,
            updatedAt = System.currentTimeMillis(),
            lastResponseId = mutableMessages.lastOrNull { it.role == ChatRole.ASSISTANT }?.remoteResponseId,
        )
    }

    fun branchThread(
        thread: ChatThread,
        throughMessageId: String,
        branchSuffix: String = " Branch",
        defaultTitle: String = LegacyDefaultThreadTitle,
    ): ChatThread? {
        val index = thread.messages.indexOfFirst { it.id == throughMessageId }
        if (index < 0) {
            return null
        }

        val branchMessages = thread.messages.take(index + 1)
        val baseTitle = if (thread.title.isBlank()) defaultTitle else thread.title
        return ChatThread(
            title = baseTitle + branchSuffix,
            messages = branchMessages,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            lastResponseId = branchMessages.lastOrNull { it.role == ChatRole.ASSISTANT }?.remoteResponseId,
        )
    }

    fun duplicateThread(
        thread: ChatThread,
        copySuffix: String = " Copy",
        defaultTitle: String = LegacyDefaultThreadTitle,
    ): ChatThread = thread.copy(
        id = java.util.UUID.randomUUID().toString(),
        title = thread.title.ifBlank { defaultTitle } + copySuffix,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
    )

    fun removeTrailingEmptyAssistantMessages(
        thread: ChatThread,
        defaultTitle: String = LegacyDefaultThreadTitle,
    ): ChatThread {
        val trimmedMessages = thread.messages.toMutableList()
        while (
            trimmedMessages.lastOrNull()?.let { message ->
                message.role == ChatRole.ASSISTANT &&
                    message.text.isBlank() &&
                    message.attachments.isEmpty()
            } == true
        ) {
            trimmedMessages.removeAt(trimmedMessages.lastIndex)
        }

        if (trimmedMessages.size == thread.messages.size) {
            return thread
        }

        return thread.copy(
            title = if (trimmedMessages.isEmpty()) defaultTitle else thread.title,
            messages = trimmedMessages,
            updatedAt = System.currentTimeMillis(),
            lastResponseId = trimmedMessages.lastOrNull { it.role == ChatRole.ASSISTANT }?.remoteResponseId,
        )
    }
}
