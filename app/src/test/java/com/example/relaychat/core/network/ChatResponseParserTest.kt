package com.example.relaychat.core.network

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChatResponseParserTest {
    @Test
    fun parsesNestedResponsePayload() {
        val result = ChatResponseParser.parse(
            """
            {
              "response": {
                "id": "resp_nested",
                "model": "gpt-5.4",
                "output": [
                  {
                    "content": [
                      { "type": "output_text", "text": "Nested reply" }
                    ]
                  }
                ]
              }
            }
            """.trimIndent()
        )

        assertThat(result.assistantText).isEqualTo("Nested reply")
        assertThat(result.responseId).isEqualTo("resp_nested")
        assertThat(result.model).isEqualTo("gpt-5.4")
    }
}
