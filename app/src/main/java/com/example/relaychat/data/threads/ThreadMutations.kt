package com.example.relaychat.data.threads

import com.example.relaychat.core.model.ChatMessage
import com.example.relaychat.core.model.ChatRole
import com.example.relaychat.core.model.ChatThread

object ThreadMutations {
    fun appendMessage(
        thread: ChatThread,
        message: ChatMessage,
    ): ChatThread {
        val updatedMessages = thread.messages + message
        val updatedTitle = if (
            thread.title == "New Chat" &&
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

    fun removeLastTurn(thread: ChatThread): ChatThread {
        val mutableMessages = thread.messages.toMutableList()
        while (mutableMessages.lastOrNull()?.role == ChatRole.ASSISTANT) {
            mutableMessages.removeAt(mutableMessages.lastIndex)
        }
        if (mutableMessages.lastOrNull()?.role == ChatRole.USER) {
            mutableMessages.removeAt(mutableMessages.lastIndex)
        }

        return thread.copy(
            title = if (mutableMessages.isEmpty()) "New Chat" else thread.title,
            messages = mutableMessages,
            updatedAt = System.currentTimeMillis(),
            lastResponseId = mutableMessages.lastOrNull { it.role == ChatRole.ASSISTANT }?.remoteResponseId,
        )
    }

    fun branchThread(
        thread: ChatThread,
        throughMessageId: String,
    ): ChatThread? {
        val index = thread.messages.indexOfFirst { it.id == throughMessageId }
        if (index < 0) {
            return null
        }

        val branchMessages = thread.messages.take(index + 1)
        return ChatThread(
            title = thread.title + " Branch",
            messages = branchMessages,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            lastResponseId = branchMessages.lastOrNull { it.role == ChatRole.ASSISTANT }?.remoteResponseId,
        )
    }

    fun duplicateThread(thread: ChatThread): ChatThread = thread.copy(
        id = java.util.UUID.randomUUID().toString(),
        title = thread.title + " Copy",
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
    )

    fun removeTrailingEmptyAssistantMessages(thread: ChatThread): ChatThread {
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
            title = if (trimmedMessages.isEmpty()) "New Chat" else thread.title,
            messages = trimmedMessages,
            updatedAt = System.currentTimeMillis(),
            lastResponseId = trimmedMessages.lastOrNull { it.role == ChatRole.ASSISTANT }?.remoteResponseId,
        )
    }
}
