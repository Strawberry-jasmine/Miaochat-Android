package com.example.relaychat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ProviderApiStyle {
    @SerialName("responses")
    RESPONSES,

    @SerialName("chatCompletions")
    CHAT_COMPLETIONS,

    @SerialName("imageGenerations")
    IMAGE_GENERATIONS,
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
enum class AppLocale(
    val languageTag: String,
) {
    @SerialName("system")
    SYSTEM(""),

    @SerialName("en")
    ENGLISH("en"),

    @SerialName("zhHans")
    SIMPLIFIED_CHINESE("zh-CN"),
    ;

    companion object {
        fun fromLanguageTag(languageTag: String?): AppLocale {
            val normalized = languageTag
                ?.substringBefore(',')
                ?.trim()
                ?.lowercase()
                .orEmpty()

            return when {
                normalized.isBlank() -> SYSTEM
                normalized.startsWith("zh") -> SIMPLIFIED_CHINESE
                normalized.startsWith("en") -> ENGLISH
                else -> SYSTEM
            }
        }
    }
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

data class SelectedModelCapabilities(
    val reasoningEfforts: List<ReasoningEffort>,
    val verbosityLevels: List<VerbosityLevel>,
    val supportsWebSearch: Boolean,
    val supportsImageGeneration: Boolean,
)

@Serializable
data class AppSettings(
    val provider: ProviderProfile = ProviderPreset.OPENAI_RESPONSES.profile,
    val defaultControls: RequestControls = ProviderPreset.OPENAI_RESPONSES.defaultControls,
    val imageGeneration: ImageGenerationOptions = ImageGenerationOptions(),
    val appLocale: AppLocale = AppLocale.SYSTEM,
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
    if (apiStyle == ProviderApiStyle.IMAGE_GENERATIONS) {
        val presetProfile = if (presetId == ProviderPreset.OPENAI_IMAGE.id) {
            copy(
                displayName = ProviderPreset.OPENAI_IMAGE.profile.displayName,
                baseUrl = ProviderPreset.OPENAI_IMAGE.profile.baseUrl,
                path = ProviderPreset.OPENAI_IMAGE.profile.path,
                model = model.ifBlank { ProviderPreset.OPENAI_IMAGE.profile.model },
            )
        } else {
            this
        }
        return presetProfile.copy(
            supportsImageInput = false,
            supportsWebSearch = false,
            supportsStreaming = false,
            supportsVerbosity = false,
            supportsStructuredOutputs = false,
        )
    }

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

fun ProviderProfile.withSelectedModel(modelId: String): ProviderProfile {
    val trimmedModel = modelId.trim()
    if (trimmedModel.isEmpty()) {
        return this
    }

    val candidate = copy(model = trimmedModel)
    if (candidate.apiStyle == ProviderApiStyle.IMAGE_GENERATIONS || trimmedModel.isImageGenerationModelId()) {
        val imageCandidate = if (trimmedModel.isImageGenerationModelId() && candidate.isOpenAiLikeProvider()) {
            candidate.copy(
                apiStyle = ProviderApiStyle.IMAGE_GENERATIONS,
                path = "/images/generations",
            )
        } else {
            candidate
        }
        return imageCandidate.copy(
            supportsImageInput = false,
            supportsWebSearch = false,
            supportsStreaming = false,
            supportsVerbosity = false,
            supportsStructuredOutputs = false,
        )
    }

    if (!candidate.isOpenAiLikeProvider()) {
        return candidate
    }

    return when (candidate.apiStyle) {
        ProviderApiStyle.RESPONSES -> candidate.copy(
            supportsImageInput = true,
            supportsWebSearch = true,
            supportsStreaming = true,
            supportsVerbosity = true,
            supportsStructuredOutputs = true,
        )

        ProviderApiStyle.CHAT_COMPLETIONS -> candidate.copy(
            supportsImageInput = true,
            supportsWebSearch = false,
            supportsStreaming = true,
            supportsVerbosity = false,
            supportsStructuredOutputs = true,
        )

        ProviderApiStyle.IMAGE_GENERATIONS -> candidate
    }
}

fun ProviderProfile.capabilitiesForSelectedModel(): SelectedModelCapabilities {
    if (apiStyle == ProviderApiStyle.IMAGE_GENERATIONS || model.isImageGenerationModelId()) {
        return SelectedModelCapabilities(
            reasoningEfforts = listOf(ReasoningEffort.NONE),
            verbosityLevels = emptyList(),
            supportsWebSearch = false,
            supportsImageGeneration = true,
        )
    }

    val reasoningEfforts = when (apiStyle) {
        ProviderApiStyle.RESPONSES -> ReasoningEffort.entries
        ProviderApiStyle.CHAT_COMPLETIONS -> if (reasoningMapping.path.isNotBlank()) {
            ReasoningEffort.entries
        } else {
            listOf(ReasoningEffort.NONE)
        }

        ProviderApiStyle.IMAGE_GENERATIONS -> listOf(ReasoningEffort.NONE)
    }

    val verbosityLevels = if (supportsVerbosity || verbosityMapping.path.isNotBlank()) {
        VerbosityLevel.entries
    } else {
        emptyList()
    }

    return SelectedModelCapabilities(
        reasoningEfforts = reasoningEfforts,
        verbosityLevels = verbosityLevels,
        supportsWebSearch = supportsWebSearch,
        supportsImageGeneration = false,
    )
}

private fun ProviderProfile.isOpenAiLikeProvider(): Boolean {
    val preset = presetId.lowercase()
    val display = displayName.lowercase()
    val base = baseUrl.lowercase()
    return preset.startsWith("openai") ||
        "openai" in display ||
        "api.openai.com" in base
}

private fun String.isImageGenerationModelId(): Boolean {
    val normalized = lowercase()
    return normalized.startsWith("gpt-image") ||
        normalized.startsWith("dall-e") ||
        "image" in normalized && "embedding" !in normalized
}

data class RuntimeChatConfiguration(
    val settings: AppSettings,
    val apiKey: String,
)
