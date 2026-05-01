package com.example.relaychat.core.importer

import com.example.relaychat.core.model.AppSettings
import com.example.relaychat.core.model.ProviderApiStyle
import com.example.relaychat.core.model.ProviderPreset
import com.example.relaychat.core.model.ReasoningEffort
import com.example.relaychat.core.model.VerbosityLevel
import com.example.relaychat.core.model.normalizedForProviderCompatibility
import com.example.relaychat.core.network.RelayChatJson
import com.example.relaychat.core.network.parseJsonObject
import java.net.URI
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class CodexConfigImportException(message: String) : IllegalArgumentException(message)

data class CodexConfigImportResult(
    val settings: AppSettings,
    val providerName: String,
    val apiKeyEnvName: String,
)

object CodexConfigImporter {
    fun parse(raw: String): CodexConfigImportResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            throw CodexConfigImportException("No config content was provided.")
        }

        val globalValues = linkedMapOf<String, String>()
        val providerSections = linkedMapOf<String, MutableMap<String, String>>()
        var currentProviderSection: String? = null

        trimmed.lineSequence().forEach { rawLine ->
            val line = sanitize(rawLine)
            if (line.isEmpty()) {
                return@forEach
            }

            if (line.startsWith('[') && line.endsWith(']')) {
                currentProviderSection = parseProviderSectionName(line)
                return@forEach
            }

            val assignment = parseAssignment(line) ?: return@forEach
            if (currentProviderSection != null) {
                val section = providerSections.getOrPut(currentProviderSection!!) { linkedMapOf() }
                section[assignment.first] = assignment.second
            } else {
                globalValues[assignment.first] = assignment.second
            }
        }

        val providerKey = globalValues["model_provider"] ?: providerSections.keys.firstOrNull()
            ?: throw CodexConfigImportException("The config did not include a usable provider definition.")

        val providerConfig = providerSections[providerKey].orEmpty()
        val wireApi = providerConfig["wire_api"] ?: "responses"
        val providerName = providerConfig["name"] ?: providerKey
        val baseUrl = providerConfig["base_url"] ?: globalValues["base_url"].orEmpty()
        val apiStyle = if (wireApi.equals("responses", ignoreCase = true)) {
            ProviderApiStyle.RESPONSES
        } else {
            ProviderApiStyle.CHAT_COMPLETIONS
        }

        val preset = inferPreset(providerName, baseUrl, wireApi)
        var settings = AppSettings(
            provider = preset.profile.copy(
                displayName = providerName,
                apiStyle = apiStyle,
            ),
            defaultControls = preset.defaultControls,
        )

        val normalized = normalizeImportedEndpoint(baseUrl, apiStyle)
        settings = settings.copy(
            provider = settings.provider.copy(
                baseUrl = normalized?.first ?: baseUrl.ifBlank { settings.provider.baseUrl },
                path = normalized?.second ?: defaultPath(apiStyle, settings.provider.path, settings.provider.baseUrl),
            )
        )

        globalValues["model"]?.takeIf { it.isNotEmpty() }?.let {
            settings = settings.copy(provider = settings.provider.copy(model = it))
        }

        parseReasoning(globalValues["model_reasoning_effort"] ?: globalValues["plan_mode_reasoning_effort"])?.let {
            settings = settings.copy(defaultControls = settings.defaultControls.copy(reasoningEffort = it))
        }

        parseVerbosity(globalValues["model_verbosity"])?.let {
            settings = settings.copy(defaultControls = settings.defaultControls.copy(verbosity = it))
        }

        globalValues["network_access"]?.let {
            settings = settings.copy(
                defaultControls = settings.defaultControls.copy(
                    webSearchEnabled = it.equals("enabled", ignoreCase = true)
                )
            )
        }

        parseBool(globalValues["disable_response_storage"])?.let { disableStorage ->
            settings = settings.copy(
                defaultControls = settings.defaultControls.copy(responseStorageEnabled = !disableStorage)
            )
        }

        globalValues["model_reasoning_summary"]?.takeIf { it.isNotBlank() }?.let { reasoningSummary ->
            settings = settings.copy(
                provider = settings.provider.copy(
                    extraBodyJson = mergedExtraBodyJson(
                        existing = settings.provider.extraBodyJson,
                        reasoningSummary = reasoningSummary,
                    )
                )
            )
        }

        return CodexConfigImportResult(
            settings = settings.normalizedForProviderCompatibility(),
            providerName = providerName,
            apiKeyEnvName = providerConfig["api_key_env"] ?: "OPENAI_API_KEY",
        )
    }

    private fun sanitize(rawLine: String): String {
        val commentIndex = rawLine.indexOf('#')
        val stripped = if (commentIndex >= 0) rawLine.substring(0, commentIndex) else rawLine
        return stripped.trim()
    }

    private fun parseProviderSectionName(line: String): String? {
        val inner = line.removePrefix("[").removeSuffix("]")
        val prefix = "model_providers."
        return if (inner.startsWith(prefix)) inner.removePrefix(prefix) else null
    }

    private fun parseAssignment(line: String): Pair<String, String>? {
        val separator = line.indexOf('=')
        if (separator < 0) {
            return null
        }

        val key = line.substring(0, separator).trim()
        val rawValue = line.substring(separator + 1).trim()
        if (key.isEmpty()) {
            return null
        }

        return key to unquote(rawValue)
    }

    private fun unquote(value: String): String =
        if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) {
            value.substring(1, value.length - 1)
        } else {
            value
        }

    private fun parseBool(value: String?): Boolean? = when (value?.lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }

    private fun inferPreset(
        providerName: String,
        baseUrl: String,
        wireApi: String,
    ): ProviderPreset {
        val combined = "$providerName $baseUrl".lowercase()
        return when {
            "intelalloc" in combined -> ProviderPreset.INTELALLOC_CODEX
            "openrouter" in combined -> ProviderPreset.OPENROUTER_COMPATIBLE
            wireApi.equals("responses", ignoreCase = true) -> ProviderPreset.OPENAI_RESPONSES
            else -> ProviderPreset.OPENAI_CHAT_COMPLETIONS
        }
    }

    private fun defaultPath(
        apiStyle: ProviderApiStyle,
        currentPath: String,
        baseUrl: String,
    ): String {
        if (currentPath.isNotBlank()) {
            return currentPath.trim()
        }

        return when (apiStyle) {
            ProviderApiStyle.RESPONSES ->
                if (baseUrl.lowercase().contains("/v1")) "/responses" else "/v1/responses"

            ProviderApiStyle.CHAT_COMPLETIONS ->
                if (baseUrl.lowercase().contains("/v1")) "/chat/completions" else "/v1/chat/completions"

            ProviderApiStyle.IMAGE_GENERATIONS ->
                if (baseUrl.lowercase().contains("/v1")) "/images/generations" else "/v1/images/generations"
        }
    }

    private fun normalizeImportedEndpoint(
        baseUrl: String,
        apiStyle: ProviderApiStyle,
    ): Pair<String, String>? {
        val trimmedBase = baseUrl.trim()
        if (trimmedBase.isEmpty()) {
            return null
        }

        val uri = runCatching { URI(trimmedBase) }.getOrNull() ?: return null
        val currentSegments = uri.path.split('/').filter { it.isNotBlank() }

        val candidates = when (apiStyle) {
            ProviderApiStyle.RESPONSES -> listOf(listOf("v1", "responses"), listOf("responses"))
            ProviderApiStyle.CHAT_COMPLETIONS -> listOf(
                listOf("v1", "chat", "completions"),
                listOf("chat", "completions"),
            )

            ProviderApiStyle.IMAGE_GENERATIONS -> listOf(
                listOf("v1", "images", "generations"),
                listOf("images", "generations"),
            )
        }

        candidates.forEach { candidate ->
            if (currentSegments.size >= candidate.size) {
                val suffix = currentSegments.takeLast(candidate.size).map { it.lowercase() }
                if (suffix == candidate.map { it.lowercase() }) {
                    val baseSegments = currentSegments.dropLast(candidate.size)
                    val normalizedBase = URI(
                        uri.scheme,
                        uri.userInfo,
                        uri.host,
                        uri.port,
                        if (baseSegments.isEmpty()) "" else "/" + baseSegments.joinToString("/"),
                        uri.query,
                        uri.fragment,
                    ).toString().trim()
                    return normalizedBase.ifEmpty { trimmedBase } to "/" + candidate.joinToString("/")
                }
            }
        }

        return null
    }

    private fun mergedExtraBodyJson(
        existing: String,
        reasoningSummary: String,
    ): String {
        val baseObject = existing.trim()
            .takeIf { it.isNotEmpty() }
            ?.let { runCatching { parseJsonObject(it) }.getOrNull() }
            ?: JsonObject(emptyMap())
        val reasoningObject = ((baseObject["reasoning"] as? JsonObject)?.toMutableMap() ?: linkedMapOf()).apply {
            this["summary"] = JsonPrimitive(reasoningSummary)
        }
        val merged = JsonObject(
            baseObject.toMutableMap().apply {
                this["reasoning"] = JsonObject(reasoningObject)
            }
        )
        return RelayChatJson.instance.encodeToString(JsonObject.serializer(), merged)
    }

    private fun parseReasoning(value: String?): ReasoningEffort? = when (value?.lowercase()) {
        "none" -> ReasoningEffort.NONE
        "minimal" -> ReasoningEffort.MINIMAL
        "low" -> ReasoningEffort.LOW
        "medium" -> ReasoningEffort.MEDIUM
        "high" -> ReasoningEffort.HIGH
        "xhigh" -> ReasoningEffort.XHIGH
        else -> null
    }

    private fun parseVerbosity(value: String?): VerbosityLevel? = when (value?.lowercase()) {
        "low" -> VerbosityLevel.LOW
        "medium" -> VerbosityLevel.MEDIUM
        "high" -> VerbosityLevel.HIGH
        else -> null
    }
}
