package com.example.relaychat.core.network

import com.example.relaychat.core.model.ChatSendResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object ChatResponseParser {
    fun parse(raw: String): ChatSendResult = parse(parseJsonElement(raw))

    fun parse(element: JsonElement): ChatSendResult {
        val root = element as? JsonObject ?: JsonObject(emptyMap())
        return ChatSendResult(
            assistantText = extractAssistantText(root).orEmpty(),
            responseId = extractResponseId(root),
            requestId = null,
            model = extractModel(root),
        )
    }

    private fun extractAssistantText(value: JsonElement?): String? {
        val dictionary = value as? JsonObject ?: return null

        dictionary["output_text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { return it }

        val choices = dictionary["choices"] as? JsonArray
        if (choices != null) {
            val message = choices.firstOrNull()?.jsonObject?.get("message")?.jsonObject
            val flattened = flattenTextContent(message?.get("content"))
            if (!flattened.isNullOrEmpty()) {
                return flattened
            }
        }

        val output = dictionary["output"] as? JsonArray
        if (output != null) {
            val flattened = flattenTextContent(output)
            if (!flattened.isNullOrEmpty()) {
                return flattened
            }
        }

        val response = dictionary["response"] as? JsonObject
        if (response != null) {
            val nested = extractAssistantText(response)
            if (!nested.isNullOrEmpty()) {
                return nested
            }
        }

        dictionary["message"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { return it }
        return null
    }

    private fun extractResponseId(value: JsonElement?): String? {
        val dictionary = value as? JsonObject ?: return null
        dictionary["id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { return it }
        return extractResponseId(dictionary["response"])
    }

    private fun extractModel(value: JsonElement?): String? {
        val dictionary = value as? JsonObject ?: return null
        dictionary["model"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { return it }
        return extractModel(dictionary["response"])
    }

    private fun flattenTextContent(value: JsonElement?): String? = when (value) {
        null -> null
        is JsonPrimitive -> value.contentOrNull?.takeIf { it.isNotEmpty() }
        is JsonObject -> {
            value["text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }
                ?: value["delta"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }
                ?: flattenTextContent(value["content"])
                ?: value["output_text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }
        }

        is JsonArray -> {
            val parts = value.mapNotNull { flattenTextContent(it) }
            if (parts.isEmpty()) null else parts.joinToString("\n")
        }
    }
}
