package com.example.relaychat.core.network

import com.example.relaychat.core.model.ChatMessage
import com.example.relaychat.core.model.ChatRole
import com.example.relaychat.core.model.ChatThread
import com.example.relaychat.core.model.ProviderApiStyle
import com.example.relaychat.core.model.ProviderProfile
import com.example.relaychat.core.model.RequestControls
import com.example.relaychat.core.model.ResponseFormatMode
import com.example.relaychat.core.model.ToggleFieldMapping
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

class ChatPayloadBuilderTest {
    @Test
    fun buildsResponsesPayloadWithVerbosityWebSearchAndJsonSchema() {
        val provider = ProviderProfile(apiStyle = ProviderApiStyle.RESPONSES)
        val controls = RequestControls().copy(
            webSearchEnabled = true,
            responseFormat = RequestControls().responseFormat.copy(
                mode = ResponseFormatMode.JSON_SCHEMA,
                schemaName = "answer",
                schemaJson = """
                {
                  "type": "object",
                  "properties": {
                    "answer": { "type": "string" }
                  },
                  "required": ["answer"],
                  "additionalProperties": false
                }
                """.trimIndent()
            )
        )
        val thread = ChatThread(
            messages = listOf(ChatMessage(role = ChatRole.USER, text = "Summarize this."))
        )

        val payload = ChatPayloadBuilder.build(
            thread = thread,
            provider = provider,
            controls = controls,
            stream = true
        )

        assertThat(payload["model"]).isEqualTo(JsonPrimitive(provider.model))
        assertThat(payload["stream"]).isEqualTo(JsonPrimitive(true))
        assertThat((payload["tools"] as JsonArray).size).isEqualTo(1)

        val text = payload["text"] as JsonObject
        assertThat(text["verbosity"]).isEqualTo(JsonPrimitive("medium"))

        val format = text["format"] as JsonObject
        assertThat(format["type"]).isEqualTo(JsonPrimitive("json_schema"))
    }

    @Test
    fun buildsChatCompletionsSchemaFormat() {
        val provider = ProviderProfile(apiStyle = ProviderApiStyle.CHAT_COMPLETIONS)
        val controls = RequestControls().copy(
            responseFormat = RequestControls().responseFormat.copy(
                mode = ResponseFormatMode.JSON_SCHEMA,
                schemaName = "answer",
                schemaJson = """
                {
                  "type": "object",
                  "properties": {
                    "answer": { "type": "string" }
                  },
                  "required": ["answer"]
                }
                """.trimIndent()
            )
        )
        val thread = ChatThread(
            messages = listOf(ChatMessage(role = ChatRole.USER, text = "Return JSON."))
        )

        val payload = ChatPayloadBuilder.build(
            thread = thread,
            provider = provider,
            controls = controls,
            stream = false
        )

        val responseFormat = payload["response_format"] as JsonObject
        assertThat(responseFormat["type"]).isEqualTo(JsonPrimitive("json_schema"))
    }

    @Test
    fun usesPreviousResponseIdForResponsesFollowUps() {
        val provider = ProviderProfile(apiStyle = ProviderApiStyle.RESPONSES)
        val thread = ChatThread(
            messages = listOf(
                ChatMessage(role = ChatRole.USER, text = "First question"),
                ChatMessage(role = ChatRole.ASSISTANT, text = "First answer"),
                ChatMessage(role = ChatRole.USER, text = "Follow up")
            ),
            lastResponseId = "resp_abc123"
        )

        val payload = ChatPayloadBuilder.build(
            thread = thread,
            provider = provider,
            controls = RequestControls(),
            stream = false
        )

        assertThat(payload["previous_response_id"]).isEqualTo(JsonPrimitive("resp_abc123"))
        val input = payload["input"] as JsonArray
        assertThat(input.size).isEqualTo(1)
    }

    @Test
    fun buildsResponsesPayloadUsingProviderSpecificWebSearchToolMapping() {
        val provider = ProviderProfile(
            apiStyle = ProviderApiStyle.RESPONSES,
            supportsWebSearch = true,
            webSearchMapping = ToggleFieldMapping(
                path = "tools",
                enabledJson = """[{"type":"web_search","external_web_access":true}]""",
                disabledJson = "[]",
            ),
        )
        val controls = RequestControls(webSearchEnabled = true)
        val thread = ChatThread(
            messages = listOf(ChatMessage(role = ChatRole.USER, text = "Find the latest update."))
        )

        val payload = ChatPayloadBuilder.build(
            thread = thread,
            provider = provider,
            controls = controls,
            stream = true,
        )

        val tools = payload["tools"] as JsonArray
        val tool = tools.first() as JsonObject
        assertThat(tool["type"]).isEqualTo(JsonPrimitive("web_search"))
        assertThat(tool["external_web_access"]).isEqualTo(JsonPrimitive(true))
    }
}
