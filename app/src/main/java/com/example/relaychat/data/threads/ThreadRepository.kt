package com.example.relaychat.data.threads

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.example.relaychat.core.model.ChatMessage
import com.example.relaychat.core.model.ChatRole
import com.example.relaychat.core.model.ChatThread
import com.example.relaychat.data.settings.PreferenceKeys
import com.example.relaychat.data.settings.relayChatPreferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ThreadRepository(
    private val context: Context,
    private val database: ThreadDatabase = ThreadDatabase.getInstance(context),
) {
    private val dao = database.threadDao()
    private val mutex = Mutex()

    val threadsFlow: Flow<List<ChatThread>> = dao.observeThreads().map { threads ->
        threads.map { it.toModel() }
    }

    val selectedThreadIdFlow: Flow<String?> = context.relayChatPreferencesDataStore.data.map { preferences ->
        preferences[PreferenceKeys.selectedThreadId]
    }

    suspend fun getThread(threadId: String): ChatThread? = dao.getThread(threadId)?.toModel()

    suspend fun ensureSelectedThread(): String = mutex.withLock {
        val threads = loadThreads()
        val selectedThreadId = readSelectedThreadId()
        when {
            selectedThreadId != null && threads.any { it.id == selectedThreadId } -> selectedThreadId
            threads.isNotEmpty() -> {
                val threadId = threads.first().id
                writeSelectedThreadId(threadId)
                threadId
            }
            else -> createThreadLocked(title = "New Chat", select = true)
        }
    }

    suspend fun createThread(
        title: String = "New Chat",
        select: Boolean = true,
    ): String = mutex.withLock {
        createThreadLocked(title, select)
    }

    suspend fun selectThread(threadId: String) {
        mutex.withLock {
            if (dao.threadExists(threadId)) {
                writeSelectedThreadId(threadId)
            }
        }
    }

    suspend fun deleteThread(threadId: String) {
        mutex.withLock {
            dao.deleteThread(threadId)
            val remainingThreads = loadThreads()
            val selectedThreadId = readSelectedThreadId()
            when {
                remainingThreads.isEmpty() -> createThreadLocked("New Chat", true)
                selectedThreadId == threadId -> writeSelectedThreadId(remainingThreads.first().id)
            }
        }
    }

    suspend fun renameThread(
        threadId: String,
        title: String,
    ) {
        val normalized = title.trim()
        if (normalized.isEmpty()) {
            return
        }

        mutex.withLock {
            val thread = getThread(threadId) ?: return@withLock
            replaceThreadLocked(
                thread.copy(
                    title = normalized,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    suspend fun duplicateThread(
        threadId: String,
        select: Boolean = true,
    ): String? = mutex.withLock {
        val thread = getThread(threadId) ?: return@withLock null
        val duplicated = ThreadMutations.duplicateThread(thread)
        replaceThreadLocked(duplicated)
        if (select) {
            writeSelectedThreadId(duplicated.id)
        }
        duplicated.id
    }

    suspend fun branchThread(
        threadId: String,
        throughMessageId: String,
        select: Boolean = true,
    ): String? = mutex.withLock {
        val thread = getThread(threadId) ?: return@withLock null
        val branched = ThreadMutations.branchThread(thread, throughMessageId) ?: return@withLock null
        replaceThreadLocked(branched)
        if (select) {
            writeSelectedThreadId(branched.id)
        }
        branched.id
    }

    suspend fun trimThread(
        threadId: String,
        throughMessageId: String?,
    ) {
        mutex.withLock {
            val thread = getThread(threadId) ?: return@withLock
            val updatedThread = if (throughMessageId == null) {
                thread.copy(
                    title = "New Chat",
                    messages = emptyList(),
                    updatedAt = System.currentTimeMillis(),
                    lastResponseId = null,
                )
            } else {
                val index = thread.messages.indexOfFirst { it.id == throughMessageId }
                if (index < 0) {
                    return@withLock
                }
                val messages = thread.messages.take(index + 1)
                thread.copy(
                    messages = messages,
                    updatedAt = System.currentTimeMillis(),
                    lastResponseId = messages.lastOrNull { it.role == ChatRole.ASSISTANT }?.remoteResponseId,
                )
            }
            replaceThreadLocked(updatedThread)
        }
    }

    suspend fun removeLastTurn(threadId: String) {
        mutex.withLock {
            val thread = getThread(threadId) ?: return@withLock
            replaceThreadLocked(ThreadMutations.removeLastTurn(thread))
        }
    }

    suspend fun appendMessage(
        message: ChatMessage,
        threadId: String,
    ) {
        mutex.withLock {
            val thread = getThread(threadId) ?: return@withLock
            replaceThreadLocked(ThreadMutations.appendMessage(thread, message))
        }
    }

    suspend fun updateMessage(
        threadId: String,
        messageId: String,
        transform: (ChatMessage) -> ChatMessage,
    ) {
        mutex.withLock {
            val thread = getThread(threadId) ?: return@withLock
            val index = thread.messages.indexOfFirst { it.id == messageId }
            if (index < 0) {
                return@withLock
            }

            val updatedMessages = thread.messages.toMutableList()
            updatedMessages[index] = transform(updatedMessages[index])
            replaceThreadLocked(
                thread.copy(
                    messages = updatedMessages,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    suspend fun setLastResponseId(
        threadId: String,
        responseId: String?,
    ) {
        mutex.withLock {
            val thread = getThread(threadId) ?: return@withLock
            replaceThreadLocked(
                thread.copy(
                    lastResponseId = responseId,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    suspend fun cleanupIncompleteAssistantMessages() {
        mutex.withLock {
            loadThreads().forEach { thread ->
                val sanitized = ThreadMutations.removeTrailingEmptyAssistantMessages(thread)
                if (sanitized != thread) {
                    replaceThreadLocked(sanitized)
                }
            }
        }
    }

    private suspend fun createThreadLocked(
        title: String,
        select: Boolean,
    ): String {
        val normalizedTitle = title.trim().ifEmpty { "New Chat" }
        val thread = ChatThread(title = normalizedTitle)
        replaceThreadLocked(thread)
        if (select) {
            writeSelectedThreadId(thread.id)
        }
        return thread.id
    }

    private suspend fun replaceThreadLocked(thread: ChatThread) {
        dao.upsertThread(thread.toEntity())
        dao.deleteMessagesByThreadId(thread.id)

        val messages = thread.toMessageEntities()
        if (messages.isNotEmpty()) {
            dao.insertMessages(messages)
        }

        val attachments = thread.toAttachmentEntities()
        if (attachments.isNotEmpty()) {
            dao.insertAttachments(attachments)
        }
    }

    private suspend fun loadThreads(): List<ChatThread> = dao.getThreads().map { it.toModel() }

    private suspend fun readSelectedThreadId(): String? =
        context.relayChatPreferencesDataStore.data.first()[PreferenceKeys.selectedThreadId]

    private suspend fun writeSelectedThreadId(threadId: String?) {
        context.relayChatPreferencesDataStore.edit { preferences ->
            if (threadId == null) {
                preferences.remove(PreferenceKeys.selectedThreadId)
            } else {
                preferences[PreferenceKeys.selectedThreadId] = threadId
            }
        }
    }
}
