package com.example.relaychat.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.relaychat.R
import com.example.relaychat.core.importer.CodexConfigImporter
import com.example.relaychat.core.importer.CodexConfigImportException
import com.example.relaychat.core.importer.CodexConfigImportResult
import com.example.relaychat.core.model.AppSettings
import com.example.relaychat.core.model.ChatAttachment
import com.example.relaychat.core.model.ChatMessage
import com.example.relaychat.core.model.ChatRole
import com.example.relaychat.core.model.ChatThread
import com.example.relaychat.core.model.ProviderApiStyle
import com.example.relaychat.core.model.ProviderProfile
import com.example.relaychat.core.model.ProviderPreset
import com.example.relaychat.core.model.ReasoningEffort
import com.example.relaychat.core.model.RequestControls
import com.example.relaychat.core.model.ResponseFormatMode
import com.example.relaychat.core.model.VerbosityLevel
import com.example.relaychat.core.model.capabilitiesForSelectedModel
import com.example.relaychat.core.model.withSelectedModel
import com.example.relaychat.core.network.ChatServiceException
import com.example.relaychat.core.network.EndpointResolver
import com.example.relaychat.data.settings.SettingsRepository
import com.example.relaychat.data.threads.ThreadRepository
import com.example.relaychat.localization.AppLocaleManager
import com.example.relaychat.ui.strings.stringFor
import com.example.relaychat.ui.theme.applyToAppCompat
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
        fun from(
            result: CodexConfigImportResult,
            application: Application,
        ): ImportedConfigSummary = ImportedConfigSummary(
            providerName = result.providerName,
            model = result.settings.provider.model,
            endpoint = EndpointResolver.buildDisplayString(
                baseUrl = result.settings.provider.baseUrl,
                path = result.settings.provider.path,
            ),
            reasoning = application.stringFor(result.settings.defaultControls.reasoningEffort),
            verbosity = application.stringFor(result.settings.defaultControls.verbosity),
            webSearch = application.getString(
                if (result.settings.defaultControls.webSearchEnabled) {
                    R.string.status_enabled
                } else {
                    R.string.status_disabled
                },
            ),
            responseStorage = application.getString(
                if (result.settings.defaultControls.responseStorageEnabled) {
                    R.string.status_enabled
                } else {
                    R.string.status_disabled
                },
            ),
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
    val detail: String = "thinking",
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
    private val executionCoordinator = ChatExecutionCoordinator.getInstance(application.applicationContext)

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
                    if (state.settings.themeMode != settings.themeMode) {
                        settings.themeMode.applyToAppCompat()
                    }
                    if (state.settings.appLocale != settings.appLocale &&
                        AppLocaleManager.currentAppLocale() != settings.appLocale
                    ) {
                        AppLocaleManager.apply(settings.appLocale)
                    }

                    state.copy(
                        settings = settings,
                        controls = mergeControlsForSettingsChange(
                            currentControls = state.controls,
                            previousSettings = state.settings,
                            newSettings = settings,
                        ),
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

        viewModelScope.launch {
            executionCoordinator.activeReply.collect { reply ->
                _uiState.update { state ->
                    state.copy(
                        inFlightReply = reply,
                        isSending = reply != null,
                    )
                }
            }
        }

        viewModelScope.launch {
            executionCoordinator.lastFailure.collect { failure ->
                if (failure != null) {
                    _uiState.update { it.copy(errorMessage = failure.message) }
                }
            }
        }
    }

    fun dismissError() {
        executionCoordinator.clearFailure()
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

    fun selectProviderModel(modelId: String) {
        updateSettings { settings ->
            val provider = settings.provider.withSelectedModel(modelId)
            settings.copy(
                provider = provider,
                defaultControls = settings.defaultControls.normalizedForProviderCapabilities(provider),
            )
        }
    }

    fun updateAppLocale(appLocale: com.example.relaychat.core.model.AppLocale) {
        viewModelScope.launch {
            settingsRepository.writeSettings(_uiState.value.settings.copy(appLocale = appLocale))
            AppLocaleManager.apply(appLocale)
        }
    }

    fun applyProviderPreset(
        preset: ProviderPreset,
        status: String? = null,
    ) {
        viewModelScope.launch {
            settingsRepository.writeSettings(
                importedSettingsPreservingAppPreferences(
                    current = _uiState.value.settings,
                    imported = AppSettings(
                    provider = preset.profile,
                    defaultControls = preset.defaultControls,
                    ),
                ),
            )
            _uiState.update {
                it.copy(
                    importStatus = status ?: getApplication<Application>().getString(
                        R.string.status_provider_preset_applied,
                        getApplication<Application>().stringFor(preset),
                    ),
                    importSummary = null,
                )
            }
        }
    }

    fun importCodexConfig(raw: String) {
        viewModelScope.launch {
            runCatching { CodexConfigImporter.parse(raw) }
                .onSuccess { result ->
                    val mergedSettings = importedSettingsPreservingAppPreferences(
                        current = _uiState.value.settings,
                        imported = result.settings,
                    )
                    settingsRepository.writeSettings(mergedSettings)
                    _uiState.update {
                        it.copy(
                            importStatus = getApplication<Application>().getString(
                                R.string.status_config_imported,
                                result.providerName,
                            ),
                            importSummary = ImportedConfigSummary.from(result, getApplication()),
                            errorMessage = null,
                        )
                    }
                }
                .onFailure { error ->
                    val fallbackMessage = getApplication<Application>().getString(R.string.error_import_failed)
                    _uiState.update {
                        it.copy(
                            errorMessage = importFailureMessage(
                                error = error,
                                localizedFallback = fallbackMessage,
                            ),
                        )
                    }
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
            if (snapshot.attachment != null && !snapshot.settings.provider.supportsImageInput) {
                _uiState.update { it.copy(errorMessage = getApplication<Application>().getString(R.string.error_provider_image_disabled)) }
                return@launch
            }

            val apiKey = snapshot.apiKey.trim()
            if (apiKey.isEmpty()) {
                _uiState.update { it.copy(errorMessage = getApplication<Application>().getString(R.string.error_api_key_before_send)) }
                return@launch
            }

            val threadId = threadRepository.ensureSelectedThread()
            val userMessage = ChatMessage(
                role = ChatRole.USER,
                text = snapshot.draft.trim(),
                attachments = snapshot.attachment?.let { listOf(it) } ?: emptyList(),
            )
            threadRepository.appendMessage(userMessage, threadId)

            when (val startResult = executionCoordinator.start(threadId, snapshot.controls)) {
                is ChatExecutionStartResult.Started -> {
                    _uiState.update {
                        it.copy(
                            draft = "",
                            attachment = null,
                            errorMessage = null,
                            composerNote = null,
                            isSending = true,
                            inFlightReply = startResult.reply,
                        )
                    }
                    ChatForegroundService.startSend(
                        context = getApplication(),
                        threadId = threadId,
                        reason = getApplication<Application>().getString(R.string.notification_reason_background_send),
                    )
                }

                is ChatExecutionStartResult.Rejected -> {
                    _uiState.update { it.copy(errorMessage = startResult.message) }
                }
            }
        }
    }

    fun regenerateLastAssistant() {
        val snapshot = _uiState.value
        val currentThread = snapshot.currentThread ?: return
        val lastAssistant = currentThread.messages.lastOrNull()
        val lastUser = currentThread.messages.lastOrNull { it.role == ChatRole.USER }
        if (snapshot.isSending) {
            return
        }
        if (lastAssistant?.role != ChatRole.ASSISTANT || lastUser == null) {
            _uiState.update { it.copy(errorMessage = getApplication<Application>().getString(R.string.error_no_reply_to_regenerate)) }
            return
        }

        viewModelScope.launch {
            val apiKey = snapshot.apiKey.trim()
            if (apiKey.isEmpty()) {
                _uiState.update { it.copy(errorMessage = getApplication<Application>().getString(R.string.error_api_key_before_regenerate)) }
                return@launch
            }

            threadRepository.trimThread(currentThread.id, lastUser.id)

            when (val startResult = executionCoordinator.start(currentThread.id, snapshot.controls)) {
                is ChatExecutionStartResult.Started -> {
                    _uiState.update {
                        it.copy(
                            isSending = true,
                            composerNote = getApplication<Application>().getString(R.string.chat_note_regenerating),
                            errorMessage = null,
                            inFlightReply = startResult.reply,
                        )
                    }
                    ChatForegroundService.startSend(
                        context = getApplication(),
                        threadId = currentThread.id,
                        reason = getApplication<Application>().getString(R.string.notification_reason_background_regenerate),
                    )
                }

                is ChatExecutionStartResult.Rejected -> {
                    _uiState.update { it.copy(errorMessage = startResult.message, composerNote = null) }
                }
            }
        }
    }
}

internal fun importedSettingsPreservingAppPreferences(
    current: AppSettings,
    imported: AppSettings,
): AppSettings = imported.copy(
    appLocale = current.appLocale,
    themeMode = current.themeMode,
)

internal fun importFailureMessage(
    error: Throwable,
    localizedFallback: String,
): String = when (error) {
    is CodexConfigImportException -> localizedFallback
    else -> error.message ?: localizedFallback
}

private fun mergeControlsForSettingsChange(
    currentControls: RequestControls,
    previousSettings: AppSettings,
    newSettings: AppSettings,
): RequestControls {
    val shouldTrackDefaults = currentControls == previousSettings.defaultControls
    val candidate = if (shouldTrackDefaults) {
        newSettings.defaultControls
    } else {
        currentControls
    }

    return candidate.normalizedForProviderCapabilities(newSettings.provider)
}

private fun RequestControls.normalizedForProviderCapabilities(provider: ProviderProfile): RequestControls {
    val capabilities = provider.capabilitiesForSelectedModel()
    val normalizedReasoning = if (reasoningEffort in capabilities.reasoningEfforts) {
        reasoningEffort
    } else {
        capabilities.reasoningEfforts.lastOrNull() ?: ReasoningEffort.NONE
    }
    val normalizedVerbosity = if (capabilities.verbosityLevels.isEmpty()) {
        VerbosityLevel.MEDIUM
    } else if (verbosity in capabilities.verbosityLevels) {
        verbosity
    } else {
        VerbosityLevel.MEDIUM
    }

    return copy(
        reasoningEffort = normalizedReasoning,
        verbosity = normalizedVerbosity,
        webSearchEnabled = webSearchEnabled && capabilities.supportsWebSearch,
        responseStorageEnabled = responseStorageEnabled && !capabilities.supportsImageGeneration,
        responseFormat = if (provider.supportsStructuredOutputs) {
            responseFormat
        } else {
            responseFormat.copy(mode = ResponseFormatMode.TEXT)
        },
    )
}

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
