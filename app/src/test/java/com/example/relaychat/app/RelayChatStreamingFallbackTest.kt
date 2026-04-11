package com.example.relaychat.app

import com.example.relaychat.core.model.ProviderPreset
import com.example.relaychat.core.network.ChatServiceException
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

class RelayChatStreamingFallbackTest {
    @Test
    fun responsesStreamingFallsBackToNonStreamingOnServerErrors() = runTest {
        var streamCalls = 0
        var sendCalls = 0

        val result = executeWithStreamingFallback(
            provider = ProviderPreset.INTELALLOC_CODEX.profile,
            streamRequest = {
                streamCalls += 1
                throw ChatServiceException.RequestFailed(502, "error code: 502")
            },
            sendRequest = {
                sendCalls += 1
                "fallback-success"
            },
        )

        assertThat(result).isEqualTo("fallback-success")
        assertThat(streamCalls).isEqualTo(1)
        assertThat(sendCalls).isEqualTo(1)
    }

    @Test
    fun responsesStreamingDoesNotFallbackOnClientErrors() {
        var streamCalls = 0
        var sendCalls = 0

        val error = assertThrows(ChatServiceException.RequestFailed::class.java) {
            runTest {
                executeWithStreamingFallback(
                    provider = ProviderPreset.INTELALLOC_CODEX.profile,
                    streamRequest = {
                        streamCalls += 1
                        throw ChatServiceException.RequestFailed(401, "unauthorized")
                    },
                    sendRequest = {
                        sendCalls += 1
                        "should-not-run"
                    },
                )
            }
        }

        assertThat(error.status).isEqualTo(401)
        assertThat(streamCalls).isEqualTo(1)
        assertThat(sendCalls).isEqualTo(0)
    }
}
