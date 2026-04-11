package com.example.relaychat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ProviderApiStyle {
    @SerialName("responses")
    RESPONSES,

    @SerialName("chatCompletions")
    CHAT_COMPLETIONS,
}

@Serializable
enum class ReasoningEffort {
    @SerialName("none")
    NONE,

    @SerialName("minimal")
    MINIMAL,

    @SerialName("low")
    LOW,

    @SerialName("medium")
    MEDIUM,

    @SerialName("high")
    HIGH,

    @SerialName("xhigh")
    XHIGH,
}

@Serializable
enum class VerbosityLevel {
    @SerialName("low")
    LOW,

    @SerialName("medium")
    MEDIUM,

    @SerialName("high")
    HIGH,
}

@Serializable
enum class AppThemeMode {
    @SerialName("system")
    SYSTEM,

    @SerialName("light")
    LIGHT,

    @SerialName("dark")
    DARK,
    ;

    fun resolve(systemIsDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemIsDark
        LIGHT -> false
        DARK -> true
    }
}

@Serializable
enum class ToolChoiceMode {
    @SerialName("auto")
    AUTO,

    @SerialName("none")
    NONE,

    @SerialName("required")
    REQUIRED,
}

@Serializable
enum class ResponseFormatMode {
    @SerialName("text")
    TEXT,

    @SerialName("jsonObject")
    JSON_OBJECT,

    @SerialName("jsonSchema")
    JSON_SCHEMA,
}

@Serializable
data class ReasoningFieldMapping(
    val path: String = "reasoning_effort",
    val noneJson: String = "\"none\"",
    val minimalJson: String = "\"minimal\"",
    val lowJson: String = "\"low\"",
    val mediumJson: String = "\"medium\"",
    val highJson: String = "\"high\"",
    val xhighJson: String = "\"xhigh\"",
) {
    fun jsonValueFor(effort: ReasoningEffort): String = when (effort) {
        ReasoningEffort.NONE -> noneJson
        ReasoningEffort.MINIMAL -> minimalJson
        ReasoningEffort.LOW -> lowJson
        ReasoningEffort.MEDIUM -> mediumJson
        ReasoningEffort.HIGH -> highJson
        ReasoningEffort.XHIGH -> xhighJson
    }
}

@Serializable
data class VerbosityFieldMapping(
    val path: String = "",
    val lowJson: String = "\"low\"",
    val mediumJson: String = "\"medium\"",
    val highJson: String = "\"high\"",
) {
    fun jsonValueFor(level: VerbosityLevel): String = when (level) {
        VerbosityLevel.LOW -> lowJson
        VerbosityLevel.MEDIUM -> mediumJson
        VerbosityLevel.HIGH -> highJson
    }
}

@Serializable
data class ToggleFieldMapping(
    val path: String = "tools",
    val enabledJson: String = "[{\"type\":\"web_search_preview\"}]",
    val disabledJson: String = "[]",
) {
    fun jsonValue(enabled: Boolean): String = if (enabled) enabledJson else disabledJson
}

@Serializable
data class ResponseFormatOptions(
    val mode: ResponseFormatMode = ResponseFormatMode.TEXT,
    val schemaName: String = "chat_response",
    val schemaDescription: String = "",
    val schemaJson: String =
        """
        {
          "type": "object",
          "properties": {
            "answer": { "type": "string" }
          },
          "required": ["answer"],
          "additionalProperties": false
        }
        """.trimIndent(),
    val strict: Boolean = true,
)

@Serializable
data class RequestControls(
    val reasoningEffort: ReasoningEffort = ReasoningEffort.MEDIUM,
    val verbosity: VerbosityLevel = VerbosityLevel.MEDIUM,
    val webSearchEnabled: Boolean = false,
    val toolChoice: ToolChoiceMode = ToolChoiceMode.AUTO,
    val responseStorageEnabled: Boolean = true,
    val temperatureEnabled: Boolean = false,
    val temperature: Double = 0.2,
    val topPEnabled: Boolean = false,
    val topP: Double = 1.0,
    val maxOutputTokensEnabled: Boolean = false,
    val maxOutputTokens: Int = 4096,
    val seedEnabled: Boolean = false,
    val seed: Int = 42,
    val responseFormat: ResponseFormatOptions = ResponseFormatOptions(),
) {
    companion object {
        val Standard = RequestControls()
    }
}

@Serializable
data class ProviderProfile(
    val presetId: String = "custom",
    val displayName: String = "Custom provider",
    val apiStyle: ProviderApiStyle = ProviderApiStyle.RESPONSES,
    val baseUrl: String = "https://api.openai.com/v1",
    val path: String = "/responses",
    val model: String = "gpt-5.4-mini",
    val instructionsPrompt: String = "You are a practical assistant.",
    val supportsImageInput: Boolean = true,
    val supportsWebSearch: Boolean = true,
    val supportsStreaming: Boolean = true,
    val supportsVerbosity: Boolean = true,
    val supportsStructuredOutputs: Boolean = true,
    val extraHeaders: String = "",
    val extraBodyJson: String = "",
    val reasoningMapping: ReasoningFieldMapping = ReasoningFieldMapping(),
    val verbosityMapping: VerbosityFieldMapping = VerbosityFieldMapping(),
    val webSearchMapping: ToggleFieldMapping = ToggleFieldMapping(),
)

@Serializable
data class AppSettings(
    val provider: ProviderProfile = ProviderPreset.OPENAI_RESPONSES.profile,
    val defaultControls: RequestControls = ProviderPreset.OPENAI_RESPONSES.defaultControls,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
) {
    companion object {
        val Default = AppSettings()
    }
}

fun AppSettings.normalizedForProviderCompatibility(): AppSettings {
    val normalizedProvider = provider.normalizedForProviderCompatibility()
    val normalizedControls = defaultControls.copy(
        webSearchEnabled = defaultControls.webSearchEnabled && normalizedProvider.supportsWebSearch,
    )

    return if (normalizedProvider == provider && normalizedControls == defaultControls) {
        this
    } else {
        copy(
            provider = normalizedProvider,
            defaultControls = normalizedControls,
        )
    }
}

internal fun ProviderProfile.normalizedForProviderCompatibility(): ProviderProfile {
    if (!isIntelallocProvider()) {
        return this
    }

    return copy(
        path = "/responses",
        supportsWebSearch = true,
        webSearchMapping = ToggleFieldMapping(
            path = "tools",
            enabledJson = """[{"type":"web_search","external_web_access":true}]""",
            disabledJson = "[]",
        ),
    )
}

internal fun ProviderProfile.isIntelallocProvider(): Boolean {
    val display = displayName.lowercase()
    val base = baseUrl.lowercase()
    return presetId == ProviderPreset.INTELALLOC_CODEX.id ||
        "intelalloc" in display ||
        "intelalloc" in base
}

data class RuntimeChatConfiguration(
    val settings: AppSettings,
    val apiKey: String,
)

enum class RequestTuningPreset(
    val title: String,
    val detail: String,
) {
    PRECISE(
        title = "Precise",
        detail = "Lower randomness, keep answers stable.",
    ),
    BALANCED(
        title = "Balanced",
        detail = "General-purpose defaults.",
    ),
    DEEP(
        title = "Deep",
        detail = "Higher reasoning and web access when available.",
    ),
    ;

    fun applyTo(controls: RequestControls, provider: ProviderProfile): RequestControls = when (this) {
        PRECISE -> controls.copy(
            reasoningEffort = ReasoningEffort.HIGH,
            verbosity = if (provider.supportsVerbosity) VerbosityLevel.MEDIUM else controls.verbosity,
            webSearchEnabled = false,
            toolChoice = ToolChoiceMode.AUTO,
            temperatureEnabled = false,
            topPEnabled = false,
            seedEnabled = true,
        )

        BALANCED -> controls.copy(
            reasoningEffort = ReasoningEffort.MEDIUM,
            verbosity = if (provider.supportsVerbosity) VerbosityLevel.MEDIUM else controls.verbosity,
            webSearchEnabled = false,
            toolChoice = ToolChoiceMode.AUTO,
            temperatureEnabled = false,
            topPEnabled = false,
            seedEnabled = false,
        )

        DEEP -> controls.copy(
            reasoningEffort = ReasoningEffort.XHIGH,
            verbosity = if (provider.supportsVerbosity) VerbosityLevel.HIGH else controls.verbosity,
            webSearchEnabled = provider.supportsWebSearch,
            toolChoice = ToolChoiceMode.AUTO,
            temperatureEnabled = false,
            topPEnabled = false,
            seedEnabled = false,
        )
    }

    fun matches(controls: RequestControls, provider: ProviderProfile): Boolean = when (this) {
        PRECISE -> controls.reasoningEffort == ReasoningEffort.HIGH &&
            !controls.webSearchEnabled &&
            !controls.temperatureEnabled &&
            !controls.topPEnabled &&
            controls.seedEnabled &&
            (!provider.supportsVerbosity || controls.verbosity == VerbosityLevel.MEDIUM)

        BALANCED -> controls.reasoningEffort == ReasoningEffort.MEDIUM &&
            !controls.webSearchEnabled &&
            !controls.temperatureEnabled &&
            !controls.topPEnabled &&
            !controls.seedEnabled &&
            (!provider.supportsVerbosity || controls.verbosity == VerbosityLevel.MEDIUM)

        DEEP -> controls.reasoningEffort == ReasoningEffort.XHIGH &&
            controls.webSearchEnabled == provider.supportsWebSearch &&
            !controls.temperatureEnabled &&
            !controls.topPEnabled &&
            !controls.seedEnabled &&
            (!provider.supportsVerbosity || controls.verbosity == VerbosityLevel.HIGH)
    }
}
