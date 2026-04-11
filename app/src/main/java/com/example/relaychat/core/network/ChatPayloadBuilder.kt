package com.example.relaychat.core.network

import com.example.relaychat.core.model.ChatAttachment
import com.example.relaychat.core.model.ChatMessage
import com.example.relaychat.core.model.ChatRole
import com.example.relaychat.core.model.ChatThread
import com.example.relaychat.core.model.ProviderApiStyle
import com.example.relaychat.core.model.ProviderProfile
import com.example.relaychat.core.model.RequestControls
import com.example.relaychat.core.model.ResponseFormatMode
import com.example.relaychat.core.model.ToolChoiceMode
import java.util.Base64
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object ChatPayloadBuilder {
    fun build(
        thread: ChatThread,
        provider: ProviderProfile,
        controls: RequestControls,
        stream: Boolean,
    ): JsonObject = when (provider.apiStyle) {
        ProviderApiStyle.RESPONSES -> buildResponses(thread, provider, controls, stream)
        ProviderApiStyle.CHAT_COMPLETIONS -> buildChatCompletions(thread, provider, controls, stream)
    }

    private fun buildResponses(
        thread: ChatThread,
        provider: ProviderProfile,
        controls: RequestControls,
        stream: Boolean,
    ): JsonObject {
        val base = buildJsonObject {
            put("model", provider.model)
            put("store", controls.responseStorageEnabled)
            if (stream) {
                put("stream", true)
            }

            val instructions = provider.instructionsPrompt.trim()
            if (instructions.isNotEmpty()) {
                put("instructions", instructions)
            }

            val lastResponseId = thread.lastResponseId
            val latestUserMessage = thread.messages.lastOrNull { it.role == ChatRole.USER }
            val inputMessages = if (lastResponseId != null && latestUserMessage != null) {
                put("previous_response_id", lastResponseId)
                listOf(serializeResponsesMessage(latestUserMessage))
            } else {
                thread.messages.map(::serializeResponsesMessage)
            }
            put("input", JsonArray(inputMessages))

            putJsonObject("reasoning") {
                put("effort", controls.reasoningEffort.name.lowercase())
            }

            val format = makeResponsesTextFormat(controls)
            if (provider.supportsVerbosity || format != null) {
                putJsonObject("text") {
                    if (provider.supportsVerbosity) {
                        put("verbosity", controls.verbosity.name.lowercase())
                    }
                    if (format != null) {
                        put("format", format)
                    }
                }
            }

            if (provider.supportsWebSearch && controls.webSearchEnabled) {
                put(
                    "tools",
                    parseJsonElement(provider.webSearchMapping.jsonValue(true)),
                )
                if (controls.toolChoice != ToolChoiceMode.AUTO) {
                    put("tool_choice", controls.toolChoice.name.lowercase())
                }
            }

            if (controls.temperatureEnabled) {
                put("temperature", controls.temperature)
            }
            if (controls.topPEnabled) {
                put("top_p", controls.topP)
            }
            if (controls.maxOutputTokensEnabled) {
                put("max_output_tokens", controls.maxOutputTokens)
            }
            if (controls.seedEnabled) {
                put("seed", controls.seed)
            }
        }

        val extraBody = provider.extraBodyJson.trim()
        return if (extraBody.isNotEmpty()) {
            deepMerge(base, parseJsonObject(extraBody))
        } else {
            base
        }
    }

    private fun buildChatCompletions(
        thread: ChatThread,
        provider: ProviderProfile,
        controls: RequestControls,
        stream: Boolean,
    ): JsonObject {
        var payload = buildJsonObject {
            put("model", provider.model)
            put("messages", JsonArray(serializeChatMessages(thread.messages, provider)))
            if (stream) {
                put("stream", true)
            }
            if (controls.temperatureEnabled) {
                put("temperature", controls.temperature)
            }
            if (controls.topPEnabled) {
                put("top_p", controls.topP)
            }
            if (controls.maxOutputTokensEnabled) {
                put("max_tokens", controls.maxOutputTokens)
            }
            if (controls.seedEnabled) {
                put("seed", controls.seed)
            }
        }

        val instructions = provider.instructionsPrompt.trim()
        if (instructions.isNotEmpty()) {
            val systemMessage = buildJsonObject {
                put("role", ChatRole.SYSTEM.wireValue)
                put("content", instructions)
            }
            val existingMessages = payload["messages"] as JsonArray
            payload = JsonObject(payload.toMutableMap().apply {
                this["messages"] = JsonArray(listOf(systemMessage) + existingMessages)
            })
        }

        if (provider.reasoningMapping.path.isNotBlank()) {
            payload = setNestedValue(
                target = payload,
                path = provider.reasoningMapping.path,
                value = parseJsonElement(provider.reasoningMapping.jsonValueFor(controls.reasoningEffort)),
            )
        }

        if (provider.verbosityMapping.path.isNotBlank()) {
            payload = setNestedValue(
                target = payload,
                path = provider.verbosityMapping.path,
                value = parseJsonElement(provider.verbosityMapping.jsonValueFor(controls.verbosity)),
            )
        }

        if (provider.webSearchMapping.path.isNotBlank()) {
            payload = setNestedValue(
                target = payload,
                path = provider.webSearchMapping.path,
                value = parseJsonElement(provider.webSearchMapping.jsonValue(controls.webSearchEnabled)),
            )
        }

        val responseFormat = makeChatCompletionsFormat(controls)
        if (responseFormat != null) {
            payload = JsonObject(payload.toMutableMap().apply {
                this["response_format"] = responseFormat
            })
        }

        if (controls.webSearchEnabled && controls.toolChoice != ToolChoiceMode.AUTO) {
            payload = JsonObject(payload.toMutableMap().apply {
                this["tool_choice"] = jsonString(controls.toolChoice.name.lowercase())
            })
        }

        val extraBody = provider.extraBodyJson.trim()
        return if (extraBody.isNotEmpty()) {
            deepMerge(payload, parseJsonObject(extraBody))
        } else {
            payload
        }
    }

    private fun serializeResponsesMessage(message: ChatMessage): JsonObject {
        val textType = if (message.role == ChatRole.ASSISTANT) "output_text" else "input_text"
        val content = buildJsonArray {
            val trimmedText = message.text.trim()
            if (trimmedText.isNotEmpty()) {
                add(
                    buildJsonObject {
                        put("type", textType)
                        put("text", message.text)
                    }
                )
            }
            message.attachments.forEach { attachment ->
                add(serializeResponsesAttachment(attachment))
            }
        }

        return buildJsonObject {
            put("role", message.role.wireValue)
            put("content", content)
        }
    }

    private fun serializeResponsesAttachment(attachment: ChatAttachment): JsonObject =
        buildJsonObject {
            put("type", "input_image")
            put(
                "image_url",
                "data:${attachment.mimeType};base64,${Base64.getEncoder().encodeToString(attachment.data)}"
            )
        }

    private fun serializeChatMessages(
        messages: List<ChatMessage>,
        provider: ProviderProfile,
    ): List<JsonElement> = messages.map { message ->
        val trimmedText = message.text.trim()
        if (!provider.supportsImageInput || message.attachments.isEmpty()) {
            buildJsonObject {
                put("role", message.role.wireValue)
                put("content", trimmedText)
            }
        } else {
            val content = buildJsonArray {
                if (trimmedText.isNotEmpty()) {
                    add(
                        buildJsonObject {
                            put("type", "text")
                            put("text", trimmedText)
                        }
                    )
                }
                message.attachments.forEach { attachment ->
                    add(
                        buildJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") {
                                put(
                                    "url",
                                    "data:${attachment.mimeType};base64,${Base64.getEncoder().encodeToString(attachment.data)}"
                                )
                            }
                        }
                    )
                }
            }

            buildJsonObject {
                put("role", message.role.wireValue)
                put("content", content)
            }
        }
    }

    private fun makeResponsesTextFormat(controls: RequestControls): JsonObject? = when (controls.responseFormat.mode) {
        ResponseFormatMode.TEXT -> null
        ResponseFormatMode.JSON_OBJECT -> buildJsonObject {
            put("type", "json_object")
        }

        ResponseFormatMode.JSON_SCHEMA -> {
            val schema = parseJsonObject(controls.responseFormat.schemaJson)
            buildJsonObject {
                put("type", "json_schema")
                put("name", controls.responseFormat.schemaName)
                put("schema", schema)
                put("strict", controls.responseFormat.strict)
                val description = controls.responseFormat.schemaDescription.trim()
                if (description.isNotEmpty()) {
                    put("description", description)
                }
            }
        }
    }

    private fun makeChatCompletionsFormat(controls: RequestControls): JsonObject? = when (controls.responseFormat.mode) {
        ResponseFormatMode.TEXT -> null
        ResponseFormatMode.JSON_OBJECT -> buildJsonObject {
            put("type", "json_object")
        }

        ResponseFormatMode.JSON_SCHEMA -> {
            val schema = parseJsonObject(controls.responseFormat.schemaJson)
            buildJsonObject {
                put("type", "json_schema")
                putJsonObject("json_schema") {
                    put("name", controls.responseFormat.schemaName)
                    put("schema", schema)
                    put("strict", controls.responseFormat.strict)
                    val description = controls.responseFormat.schemaDescription.trim()
                    if (description.isNotEmpty()) {
                        put("description", description)
                    }
                }
            }
        }
    }
}
