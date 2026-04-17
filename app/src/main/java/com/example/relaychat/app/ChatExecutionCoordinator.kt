package com.example.relaychat.app

import android.content.Context
import android.util.Log
import com.example.relaychat.core.model.ChatMessage
import com.example.relaychat.core.model.ChatRole
import com.example.relaychat.core.model.ChatSendResult
import com.example.relaychat.core.model.ChatStreamEvent
import com.example.relaychat.core.model.ChatStreamLifecycleStage
import com.example.relaychat.core.model.ChatThread
import com.example.relaychat.core.model.RequestControls
import com.example.relaychat.core.model.RuntimeChatConfiguration
import com.example.relaychat.core.network.ChatService
import com.example.relaychat.R
import com.example.relaychat.data.settings.SettingsRepository
import com.example.relaychat.data.threads.ThreadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface ChatExecutionStartResult {
    data class Started(val reply: InFlightAssistantReply) : ChatExecutionStartResult

    data class Rejected(val message: String) : ChatExecutionStartResult
}

data class ChatExecutionFailure(
    val threadId: String,
    val message: String,
)

class ChatExecutionCoordinator internal constructor(
    private val scope: CoroutineScope,
    private val loadRuntimeSnapshot: suspend () -> RuntimeChatConfiguration,
    private val loadThread: suspend (String) -> ChatThread?,
    private val saveAssistantResult: suspend (String, ChatSendResult) -> Unit,
    private val streamRequest: (ChatThread, RuntimeChatConfiguration, RequestControls) -> Flow<ChatStreamEvent>,
    private val sendRequest: suspend (ChatThread, RuntimeChatConfiguration, RequestControls) -> ChatSendResult,
    private val requestInProgressMessage: String = "Another reply is already in progress.",
    private val requestFailedFallbackMessage: String = "Request failed.",
    private val missingActiveThreadMessage: String = "No active thread was available.",
) {
    private val lock = Any()
    private var activeJob: Job? = null

    private val _activeReply = MutableStateFlow<InFlightAssistantReply?>(null)
    val activeReply: StateFlow<InFlightAssistantReply?> = _activeReply.asStateFlow()

    private val _lastFailure = MutableStateFlow<ChatExecutionFailure?>(null)
    val lastFailure: StateFlow<ChatExecutionFailure?> = _lastFailure.asStateFlow()

    fun start(
        threadId: String,
        controls: RequestControls,
    ): ChatExecutionStartResult {
        synchronized(lock) {
            val current = _activeReply.value
            if (current != null) {
                return ChatExecutionStartResult.Rejected(requestInProgressMessage)
            }

            val initialReply = beginInFlightAssistantReply(threadId = threadId)
            _lastFailure.value = null
            _activeReply.value = initialReply
            safeLogInfo(COORDINATOR_TAG, "thread=$threadId phase=thinking detail=${initialReply.detail}")

            activeJob = scope.launch {
                runExecution(
                    threadId = threadId,
                    controls = controls,
                )
            }

            return ChatExecutionStartResult.Started(initialReply)
        }
    }

    fun clearFailure() {
        _lastFailure.value = null
    }

    internal suspend fun awaitIdle() {
        val job = synchronized(lock) { activeJob }
        job?.join()
    }

    private suspend fun runExecution(
        threadId: String,
        controls: RequestControls,
    ) {
        try {
            val runtime = loadRuntimeSnapshot()
            val thread = loadThread(threadId) ?: error(missingActiveThreadMessage)

            executeWithStreamingFallback(
                provider = runtime.settings.provider,
                streamRequest = {
                    sendStreaming(
                        thread = thread,
                        runtime = runtime,
                        controls = controls,
                        threadId = threadId,
                    )
                },
                sendRequest = {
                    val result = sendRequest(thread, runtime, controls)
                    saveAssistantResult(threadId, result)
                },
            )

            finish(threadId = threadId, error = null)
        } catch (error: Exception) {
            finish(threadId = threadId, error = error)
        }
    }

    private suspend fun sendStreaming(
        thread: ChatThread,
        runtime: RuntimeChatConfiguration,
        controls: RequestControls,
        threadId: String,
    ) {
        streamRequest(thread, runtime, controls).collect { event ->
            when (event) {
                is ChatStreamEvent.Lifecycle -> {
                    updateInFlightLifecycle(
                        threadId = threadId,
                        stage = event.stage,
                        detail = event.detail ?: "thinking",
                    )
                }

                is ChatStreamEvent.TextDelta -> {
                    safeLogDebug(
                        STREAM_TAG,
                        "delta thread=$threadId text=${event.delta.replace('\n', ' ').take(120)}",
                    )
                    appendInFlightReplyText(threadId, event.delta)
                }

                is ChatStreamEvent.Completed -> {
                    safeLogInfo(
                        STREAM_TAG,
                        "completed thread=$threadId text=${event.result.assistantText.replace('\n', ' ').take(200)}",
                    )
                    saveAssistantResult(threadId, event.result)
                }
            }
        }
    }

    private fun updateInFlightLifecycle(
        threadId: String,
        stage: ChatStreamLifecycleStage,
        detail: String,
    ) {
        val current = _activeReply.value?.takeIf { it.threadId == threadId } ?: return
        val nextStage = when (stage) {
            ChatStreamLifecycleStage.TOOL -> InFlightAssistantStage.SEARCHING
            ChatStreamLifecycleStage.REASONING,
            ChatStreamLifecycleStage.OUTPUT,
            ChatStreamLifecycleStage.OTHER,
            -> InFlightAssistantStage.THINKING
        }
        if (current.stage == nextStage && current.detail == detail) {
            return
        }

        safeLogInfo(COORDINATOR_TAG, "thread=$threadId phase=${nextStage.name.lowercase()} detail=$detail")
        _activeReply.value = current.transitionTo(
            stage = nextStage,
            detail = detail,
        )
    }

    private fun appendInFlightReplyText(
        threadId: String,
        delta: String,
    ) {
        val current = _activeReply.value?.takeIf { it.threadId == threadId } ?: return
        if (current.text.isBlank()) {
            safeLogInfo(COORDINATOR_TAG, "thread=$threadId phase=streaming detail=drafting reply")
        }

        _activeReply.value = current.transitionTo(
            stage = InFlightAssistantStage.STREAMING,
            detail = "drafting reply",
        ).copy(
            text = current.text + delta,
        )
    }

    private fun finish(
        threadId: String,
        error: Throwable?,
    ) {
        synchronized(lock) {
            activeJob = null
        }

        if (_activeReply.value?.threadId == threadId) {
            _activeReply.value = null
        }

        if (error == null) {
            safeLogInfo(COORDINATOR_TAG, "thread=$threadId phase=completed")
            return
        }

        val message = error.message ?: requestFailedFallbackMessage
        safeLogWarn(COORDINATOR_TAG, "thread=$threadId phase=failed detail=$message", error)
        _lastFailure.value = ChatExecutionFailure(threadId = threadId, message = message)
    }

    companion object {
        @Volatile
        private var instance: ChatExecutionCoordinator? = null

        fun getInstance(context: Context): ChatExecutionCoordinator =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): ChatExecutionCoordinator {
            val settingsRepository = SettingsRepository(context)
            val threadRepository = ThreadRepository(context)
            val chatService = ChatService(context)

            return ChatExecutionCoordinator(
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                loadRuntimeSnapshot = {
                    val settings = settingsRepository.settingsFlow.first()
                    val apiKey = settingsRepository.readApiKey().trim()
                    if (apiKey.isEmpty()) {
                        error(context.getString(R.string.error_api_key_before_send))
                    }
                    RuntimeChatConfiguration(settings = settings, apiKey = apiKey)
                },
                loadThread = threadRepository::getThread,
                saveAssistantResult = { threadId, result ->
                    threadRepository.appendMessage(
                        ChatMessage(
                            role = ChatRole.ASSISTANT,
                            text = result.assistantText,
                            remoteResponseId = result.responseId,
                            requestId = result.requestId,
                            model = result.model,
                        ),
                        threadId,
                    )
                    threadRepository.setLastResponseId(threadId, result.responseId)
                },
                streamRequest = { thread, runtime, controls ->
                    chatService.stream(thread, runtime, controls)
                },
                sendRequest = { thread, runtime, controls ->
                    chatService.send(thread, runtime, controls)
                },
                requestInProgressMessage = context.getString(R.string.error_request_in_progress),
                requestFailedFallbackMessage = context.getString(R.string.error_request_failed_generic),
                missingActiveThreadMessage = context.getString(R.string.error_no_active_thread),
            )
        }
    }
}

private const val COORDINATOR_TAG = "RelayChatExecution"
private const val STREAM_TAG = "RelayChatStream"

private fun safeLogInfo(
    tag: String,
    message: String,
) {
    runCatching { Log.i(tag, message) }
}

private fun safeLogDebug(
    tag: String,
    message: String,
) {
    runCatching { Log.d(tag, message) }
}

private fun safeLogWarn(
    tag: String,
    message: String,
    error: Throwable,
) {
    runCatching { Log.w(tag, message, error) }
}
