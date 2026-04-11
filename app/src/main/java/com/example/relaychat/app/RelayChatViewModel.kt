package com.example.relaychat.app

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.relaychat.core.importer.CodexConfigImporter
import com.example.relaychat.core.importer.CodexConfigImportResult
import com.example.relaychat.core.model.AppSettings
import com.example.relaychat.core.model.ChatAttachment
import com.example.relaychat.core.model.ChatMessage
import com.example.relaychat.core.model.ChatRole
import com.example.relaychat.core.model.ChatSendResult
import com.example.relaychat.core.model.ChatStreamEvent
import com.example.relaychat.core.model.ChatStreamLifecycleStage
import com.example.relaychat.core.model.ChatThread
import com.example.relaychat.core.model.ProviderApiStyle
import com.example.relaychat.core.model.ProviderProfile
import com.example.relaychat.core.model.ProviderPreset
import com.example.relaychat.core.model.RequestControls
import com.example.relaychat.core.model.RequestTuningPreset
import com.example.relaychat.core.model.RuntimeChatConfiguration
import com.example.relaychat.core.network.ChatService
import com.example.relaychat.core.network.ChatServiceException
import com.example.relaychat.core.network.EndpointResolver
import com.example.relaychat.data.settings.SettingsRepository
import com.example.relaychat.data.threads.ThreadRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImportedConfigSummary(
    val providerName: String,
    val model: String,
    val endpoint: String,
    val reasoning: String,
    val verbosity: String,
    val webSearch: String,
    val responseStorage: String,
    val apiKeyEnvName: String,
) {
    companion object {
        fun from(result: CodexConfigImportResult): ImportedConfigSummary = ImportedConfigSummary(
            providerName = result.providerName,
            model = result.settings.provider.model,
            endpoint = EndpointResolver.buildDisplayString(
                baseUrl = result.settings.provider.baseUrl,
                path = result.settings.provider.path,
            ),
            reasoning = result.settings.defaultControls.reasoningEffort.name.lowercase(),
            verbosity = result.settings.defaultControls.verbosity.name.lowercase(),
            webSearch = if (result.settings.defaultControls.webSearchEnabled) "Enabled" else "Disabled",
            responseStorage = if (result.settings.defaultControls.responseStorageEnabled) "Enabled" else "Disabled",
            apiKeyEnvName = result.apiKeyEnvName,
        )
    }
}

enum class InFlightAssistantStage {
    THINKING,
    SEARCHING,
    STREAMING,
}

data class InFlightAssistantReply(
    val threadId: String,
    val text: String = "",
    val stage: InFlightAssistantStage = InFlightAssistantStage.THINKING,
    val detail: String = "sending request",
    val startedAtMs: Long = System.currentTimeMillis(),
    val stageChangedAtMs: Long = startedAtMs,
    val visitedStages: List<InFlightAssistantStage> = listOf(stage),
)

data class RelayChatUiState(
    val settings: AppSettings = AppSettings.Default,
    val apiKey: String = "",
    val threads: List<ChatThread> = emptyList(),
    val selectedThreadId: String? = null,
    val draft: String = "",
    val controls: RequestControls = AppSettings.Default.defaultControls,
    val attachment: ChatAttachment? = null,
    val isSending: Boolean = false,
    val errorMessage: String? = null,
    val composerNote: String? = null,
    val importStatus: String? = null,
    val importSummary: ImportedConfigSummary? = null,
    val inFlightReply: InFlightAssistantReply? = null,
) {
    val currentThread: ChatThread?
        get() = threads.firstOrNull { it.id == selectedThreadId }

    val visibleInFlightReply: InFlightAssistantReply?
        get() = inFlightReply?.takeIf { it.threadId == selectedThreadId }

    val canSend: Boolean
        get() = !isSending && (draft.trim().isNotEmpty() || attachment != null)

    val resolvedEndpoint: String
        get() = EndpointResolver.buildDisplayString(
            baseUrl = settings.provider.baseUrl,
            path = settings.provider.path,
        )
}

class RelayChatViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository(application)
    private val threadRepository = ThreadRepository(application)
    private val chatService = ChatService(application.applicationContext)

    private val _uiState = MutableStateFlow(RelayChatUiState())
    val uiState: MutableStateFlow<RelayChatUiState> = _uiState

    private var lastSelectedThreadId: String? = null

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(apiKey = settingsRepository.readApiKey()) }
        }

        viewModelScope.launch {
            threadRepository.cleanupIncompleteAssistantMessages()
        }

        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                _uiState.update { state ->
                    state.copy(
                        settings = settings,
                        controls = settings.defaultControls,
                        attachment = if (settings.provider.supportsImageInput) state.attachment else null,
                    )
                }
            }
        }

        viewModelScope.launch {
            combine(
                threadRepository.threadsFlow,
                threadRepository.selectedThreadIdFlow,
            ) { threads, selectedThreadId ->
                threads to selectedThreadId
            }.collect { (threads, selectedThreadId) ->
                val resolvedSelection = when {
                    selectedThreadId != null && threads.any { it.id == selectedThreadId } -> selectedThreadId
                    threads.isNotEmpty() -> threads.first().id
                    else -> null
                }
                val selectionChanged = resolvedSelection != lastSelectedThreadId
                lastSelectedThreadId = resolvedSelection

                _uiState.update { state ->
                    state.copy(
                        threads = threads,
                        selectedThreadId = resolvedSelection,
                        draft = if (selectionChanged) "" else state.draft,
                        controls = if (selectionChanged) state.settings.defaultControls else state.controls,
                        attachment = if (selectionChanged) null else state.attachment,
                        composerNote = if (selectionChanged) null else state.composerNote,
                        inFlightReply = state.inFlightReply?.takeIf { reply ->
                            threads.any { thread -> thread.id == reply.threadId }
                        },
                    )
                }
            }
        }

        viewModelScope.launch {
            threadRepository.ensureSelectedThread()
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun dismissImportStatus() {
        _uiState.update { it.copy(importStatus = null) }
    }

    fun updateDraft(value: String) {
        _uiState.update { it.copy(draft = value) }
    }

    fun updateControls(transform: (RequestControls) -> RequestControls) {
        _uiState.update { it.copy(controls = transform(it.controls)) }
    }

    fun attachImage(
        data: ByteArray,
        mimeType: String,
    ) {
        _uiState.update { it.copy(attachment = ChatAttachment(mimeType = mimeType, data = data)) }
    }

    fun clearAttachment() {
        _uiState.update { it.copy(attachment = null) }
    }

    fun clearComposerContext() {
        _uiState.update { it.copy(composerNote = null) }
    }

    fun useMessageAsDraft(
        message: ChatMessage,
        note: String,
    ) {
        _uiState.update {
            it.copy(
                draft = message.text,
                attachment = message.attachments.firstOrNull(),
                composerNote = note,
            )
        }
    }

    fun applyTuningPreset(preset: RequestTuningPreset) {
        _uiState.update { state ->
            state.copy(controls = preset.applyTo(state.controls, state.settings.provider))
        }
    }

    fun writeApiKey(value: String) {
        viewModelScope.launch {
            settingsRepository.writeApiKey(value)
            _uiState.update { it.copy(apiKey = value) }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            val updated = transform(_uiState.value.settings)
            settingsRepository.writeSettings(updated)
        }
    }

    fun applyProviderPreset(
        preset: ProviderPreset,
        status: String = "Applied the ${preset.title} preset.",
    ) {
        viewModelScope.launch {
            settingsRepository.writeSettings(
                AppSettings(
                    provider = preset.profile,
                    defaultControls = preset.defaultControls,
                )
            )
            _uiState.update {
                it.copy(
                    importStatus = status,
                    importSummary = null,
                )
            }
        }
    }

    fun applyDefaultTuningPreset(preset: RequestTuningPreset) {
        val current = _uiState.value
        val updatedControls = preset.applyTo(current.settings.defaultControls, current.settings.provider)
        updateSettings { settings ->
            settings.copy(defaultControls = updatedControls)
        }
        _uiState.update {
            it.copy(importStatus = "Applied the ${preset.title.lowercase()} quality preset to the default controls.")
        }
    }

    fun importCodexConfig(raw: String) {
        viewModelScope.launch {
            runCatching { CodexConfigImporter.parse(raw) }
                .onSuccess { result ->
                    settingsRepository.writeSettings(result.settings)
                    _uiState.update {
                        it.copy(
                            importStatus = "Imported ${result.providerName} config. Set the API key if it is not already stored.",
                            importSummary = ImportedConfigSummary.from(result),
                            errorMessage = null,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message ?: "Import failed") }
                }
        }
    }

    fun createThread() {
        viewModelScope.launch {
            threadRepository.createThread()
        }
    }

    fun selectThread(threadId: String) {
        viewModelScope.launch {
            threadRepository.selectThread(threadId)
        }
    }

    fun deleteThread(threadId: String) {
        viewModelScope.launch {
            threadRepository.deleteThread(threadId)
        }
    }

    fun renameCurrentThread(title: String) {
        val threadId = _uiState.value.currentThread?.id ?: return
        viewModelScope.launch {
            threadRepository.renameThread(threadId, title)
        }
    }

    fun renameThread(
        threadId: String,
        title: String,
    ) {
        viewModelScope.launch {
            threadRepository.renameThread(threadId, title)
        }
    }

    fun duplicateCurrentThread() {
        val threadId = _uiState.value.currentThread?.id ?: return
        viewModelScope.launch {
            threadRepository.duplicateThread(threadId)
        }
    }

    fun duplicateThread(threadId: String) {
        viewModelScope.launch {
            threadRepository.duplicateThread(threadId, select = false)
        }
    }

    fun clearCurrentThread() {
        val threadId = _uiState.value.currentThread?.id ?: return
        viewModelScope.launch {
            threadRepository.trimThread(threadId, throughMessageId = null)
        }
    }

    fun branchThread(messageId: String) {
        val threadId = _uiState.value.currentThread?.id ?: return
        viewModelScope.launch {
            threadRepository.branchThread(threadId, messageId, select = true)
        }
    }

    fun send() {
        val snapshot = _uiState.value
        if (!snapshot.canSend) {
            return
        }

        viewModelScope.launch {
            val provider = _uiState.value.settings.provider
            if (_uiState.value.attachment != null && !provider.supportsImageInput) {
                _uiState.update { it.copy(errorMessage = "The current provider profile has image input disabled.") }
                return@launch
            }

            val apiKey = _uiState.value.apiKey.trim()
            if (apiKey.isEmpty()) {
                _uiState.update { it.copy(errorMessage = "Set an API key in Settings before sending.") }
                return@launch
            }

            val threadId = threadRepository.ensureSelectedThread()
            val userMessage = ChatMessage(
                role = ChatRole.USER,
                text = snapshot.draft.trim(),
                attachments = snapshot.attachment?.let { listOf(it) } ?: emptyList(),
            )
            threadRepository.appendMessage(userMessage, threadId)
            beginInFlightReply(threadId)

            _uiState.update {
                it.copy(
                    draft = "",
                    attachment = null,
                    errorMessage = null,
                    composerNote = null,
                    isSending = true,
                )
            }

            try {
                val currentThread = threadRepository.getThread(threadId)
                    ?: error("No active thread was available.")
                val runtime = RuntimeChatConfiguration(_uiState.value.settings, apiKey)
                executeWithStreamingFallback(
                    provider = provider,
                    streamRequest = {
                        sendStreaming(currentThread, runtime, threadId)
                    },
                    sendRequest = {
                        val result = chatService.send(currentThread, runtime, _uiState.value.controls)
                        appendAssistantResult(threadId, result)
                    },
                )
                clearInFlightReply(threadId, status = "completed")
            } catch (error: Exception) {
                clearInFlightReply(threadId, status = "failed")
                _uiState.update { it.copy(errorMessage = error.message ?: "Request failed.") }
            } finally {
                _uiState.update { it.copy(isSending = false) }
            }
        }
    }

    fun regenerateLastAssistant() {
        val currentThread = _uiState.value.currentThread ?: return
        val lastAssistant = currentThread.messages.lastOrNull()
        val lastUser = currentThread.messages.lastOrNull { it.role == ChatRole.USER }
        if (_uiState.value.isSending) {
            return
        }
        if (lastAssistant?.role != ChatRole.ASSISTANT || lastUser == null) {
            _uiState.update { it.copy(errorMessage = "There is no assistant reply to regenerate.") }
            return
        }

        viewModelScope.launch {
            val apiKey = _uiState.value.apiKey.trim()
            if (apiKey.isEmpty()) {
                _uiState.update { it.copy(errorMessage = "Set an API key in Settings before regenerating.") }
                return@launch
            }

            threadRepository.trimThread(currentThread.id, lastUser.id)
            beginInFlightReply(currentThread.id)
            _uiState.update {
                it.copy(
                    isSending = true,
                    composerNote = "Regenerating the last reply",
                    errorMessage = null,
                )
            }

            try {
                val trimmedThread = threadRepository.getThread(currentThread.id)
                    ?: error("Could not prepare the thread for regeneration.")
                val runtime = RuntimeChatConfiguration(_uiState.value.settings, apiKey)
                executeWithStreamingFallback(
                    provider = runtime.settings.provider,
                    streamRequest = {
                        sendStreaming(trimmedThread, runtime, trimmedThread.id)
                    },
                    sendRequest = {
                        val result = chatService.send(trimmedThread, runtime, _uiState.value.controls)
                        appendAssistantResult(trimmedThread.id, result)
                    },
                )
                clearInFlightReply(trimmedThread.id, status = "completed")
            } catch (error: Exception) {
                clearInFlightReply(currentThread.id, status = "failed")
                _uiState.update { it.copy(errorMessage = error.message ?: "Regeneration failed.") }
            } finally {
                _uiState.update { it.copy(isSending = false, composerNote = null) }
            }
        }
    }

    private suspend fun sendStreaming(
        thread: ChatThread,
        runtime: RuntimeChatConfiguration,
        threadId: String,
    ) {
        chatService.stream(thread, runtime, _uiState.value.controls).collect { event ->
            when (event) {
                is ChatStreamEvent.Lifecycle -> {
                    updateInFlightLifecycle(
                        threadId = threadId,
                        stage = event.stage,
                        detail = event.detail ?: "thinking",
                    )
                }

                is ChatStreamEvent.TextDelta -> {
                    Log.d(
                        STREAM_TAG,
                        "delta thread=$threadId text=${event.delta.replace('\n', ' ').take(120)}"
                    )
                    appendInFlightReplyText(threadId, event.delta)
                }

                is ChatStreamEvent.Completed -> {
                    Log.i(
                        STREAM_TAG,
                        "completed thread=$threadId text=${event.result.assistantText.replace('\n', ' ').take(200)}"
                    )
                    appendAssistantResult(threadId, event.result)
                }
            }
        }
    }

    private suspend fun appendAssistantResult(
        threadId: String,
        result: ChatSendResult,
    ) {
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
    }

    private fun beginInFlightReply(threadId: String) {
        Log.i(UI_TAG, "thread=$threadId phase=thinking detail=sending request")
        _uiState.update {
            it.copy(inFlightReply = beginInFlightAssistantReply(threadId = threadId))
        }
    }

    private fun updateInFlightLifecycle(
        threadId: String,
        stage: ChatStreamLifecycleStage,
        detail: String,
    ) {
        _uiState.update { state ->
            val current = state.inFlightReply?.takeIf { it.threadId == threadId } ?: return@update state
            val nextStage = when (stage) {
                ChatStreamLifecycleStage.TOOL -> InFlightAssistantStage.SEARCHING
                ChatStreamLifecycleStage.REASONING,
                ChatStreamLifecycleStage.OUTPUT,
                ChatStreamLifecycleStage.OTHER,
                -> InFlightAssistantStage.THINKING
            }
            if (current.stage == nextStage && current.detail == detail) {
                return@update state
            }
            Log.i(UI_TAG, "thread=$threadId phase=${nextStage.name.lowercase()} detail=$detail")
            state.copy(
                inFlightReply = current.transitionTo(
                    stage = nextStage,
                    detail = detail,
                )
            )
        }
    }

    private fun appendInFlightReplyText(
        threadId: String,
        delta: String,
    ) {
        _uiState.update { state ->
            val current = state.inFlightReply?.takeIf { it.threadId == threadId } ?: return@update state
            val nextText = current.text + delta
            if (current.text.isBlank()) {
                Log.i(UI_TAG, "thread=$threadId phase=streaming detail=drafting reply")
            }
            state.copy(
                inFlightReply = current.transitionTo(
                    stage = InFlightAssistantStage.STREAMING,
                    detail = "drafting reply",
                ).copy(
                    text = nextText,
                )
            )
        }
    }

    private fun clearInFlightReply(
        threadId: String,
        status: String,
    ) {
        Log.i(UI_TAG, "thread=$threadId phase=$status")
        _uiState.update { state ->
            if (state.inFlightReply?.threadId != threadId) {
                state
            } else {
                state.copy(inFlightReply = null)
            }
        }
    }
}

private const val STREAM_TAG = "RelayChatStream"
private const val UI_TAG = "RelayChatUi"

internal suspend fun <T> executeWithStreamingFallback(
    provider: ProviderProfile,
    streamRequest: suspend () -> T,
    sendRequest: suspend () -> T,
): T {
    if (!provider.supportsStreaming || provider.apiStyle != ProviderApiStyle.RESPONSES) {
        return sendRequest()
    }

    return try {
        streamRequest()
    } catch (error: Exception) {
        if (!shouldFallbackToNonStreaming(provider, error)) {
            throw error
        }
        sendRequest()
    }
}

internal fun shouldFallbackToNonStreaming(
    provider: ProviderProfile,
    error: Throwable,
): Boolean {
    if (!provider.supportsStreaming || provider.apiStyle != ProviderApiStyle.RESPONSES) {
        return false
    }

    return error is ChatServiceException.RequestFailed && error.status in 500..599
}
