package com.example.relaychat.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object RelayChatJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        prettyPrint = false
    }
}

internal fun parseJsonElement(raw: String): JsonElement =
    RelayChatJson.instance.parseToJsonElement(raw.trim())

internal fun parseJsonObject(raw: String): JsonObject = parseJsonElement(raw) as JsonObject

internal fun setNestedValue(
    target: JsonObject,
    path: String,
    value: JsonElement,
): JsonObject {
    val parts = path.split('.').filter { it.isNotBlank() }
    if (parts.isEmpty()) {
        return target
    }

    fun setRecursive(
        current: JsonObject,
        remaining: List<String>,
    ): JsonObject {
        val head = remaining.first()
        if (remaining.size == 1) {
            return JsonObject(current.toMutableMap().apply { this[head] = value })
        }

        val child = current[head] as? JsonObject ?: JsonObject(emptyMap())
        return JsonObject(
            current.toMutableMap().apply {
                this[head] = setRecursive(child, remaining.drop(1))
            }
        )
    }

    return setRecursive(target, parts)
}

internal fun deepMerge(
    target: JsonObject,
    source: JsonObject,
): JsonObject {
    val result = target.toMutableMap()
    source.forEach { (key, sourceValue) ->
        val mergedValue = if (sourceValue is JsonObject && result[key] is JsonObject) {
            deepMerge(result.getValue(key) as JsonObject, sourceValue)
        } else {
            sourceValue
        }
        result[key] = mergedValue
    }
    return JsonObject(result)
}

internal fun jsonString(value: String): JsonPrimitive = JsonPrimitive(value)
