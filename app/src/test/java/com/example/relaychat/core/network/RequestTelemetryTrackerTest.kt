package com.example.relaychat.core.network

import com.example.relaychat.core.model.ChatStreamLifecycleStage
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RequestTelemetryTrackerTest {
    @Test
    fun classifiesReasoningToolAndOutputEvents() {
        assertThat(classifyLifecycleStage("response.reasoning_summary_text.delta"))
            .isEqualTo(ChatStreamLifecycleStage.REASONING)
        assertThat(classifyLifecycleStage("response.web_search_call.in_progress"))
            .isEqualTo(ChatStreamLifecycleStage.TOOL)
        assertThat(classifyLifecycleStage("response.output_text.delta"))
            .isEqualTo(ChatStreamLifecycleStage.OUTPUT)
        assertThat(classifyLifecycleStage("response.created"))
            .isEqualTo(ChatStreamLifecycleStage.OTHER)
    }

    @Test
    fun tracksHeadersToolAndFirstTextTimings() {
        val tracker = RequestTelemetryTracker(requestStartedAtMs = 1_000L)

        tracker.markHeaders(atMs = 1_300L)
        tracker.markEvent(type = "response.created", atMs = 1_450L)
        tracker.markEvent(type = "response.web_search_call.in_progress", atMs = 2_000L)
        tracker.markEvent(type = "response.web_search_call.completed", atMs = 4_300L)
        tracker.markEvent(type = "response.output_text.delta", atMs = 4_900L)
        tracker.markCompleted(atMs = 5_200L)

        val snapshot = tracker.snapshot()

        assertThat(snapshot.requestToHeadersMs).isEqualTo(300L)
        assertThat(snapshot.headersToFirstEventMs).isEqualTo(150L)
        assertThat(snapshot.requestToFirstTextMs).isEqualTo(3_900L)
        assertThat(snapshot.toolWindowMs).isEqualTo(2_300L)
        assertThat(snapshot.totalDurationMs).isEqualTo(4_200L)
        assertThat(snapshot.eventTypes).containsExactly(
            "response.created",
            "response.web_search_call.in_progress",
            "response.web_search_call.completed",
            "response.output_text.delta",
        ).inOrder()
    }
}
