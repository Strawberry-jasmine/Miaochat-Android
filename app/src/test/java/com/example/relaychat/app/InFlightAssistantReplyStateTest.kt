package com.example.relaychat.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InFlightAssistantReplyStateTest {
    @Test
    fun transitionRetainsStageHistoryWithoutDuplicates() {
        val started = beginInFlightAssistantReply(threadId = "thread-1", nowMs = 1_000L)
        val searching = started.transitionTo(
            stage = InFlightAssistantStage.SEARCHING,
            detail = "searching web",
            nowMs = 2_000L,
        )
        val reasoningAgain = searching.transitionTo(
            stage = InFlightAssistantStage.THINKING,
            detail = "reasoning",
            nowMs = 3_000L,
        )

        assertThat(reasoningAgain.visitedStages).containsExactly(
            InFlightAssistantStage.THINKING,
            InFlightAssistantStage.SEARCHING,
        ).inOrder()
        assertThat(reasoningAgain.stageChangedAtMs).isEqualTo(3_000L)
        assertThat(reasoningAgain.startedAtMs).isEqualTo(1_000L)
    }

    @Test
    fun pendingReplyVisualsShowLongWaitHintDuringSlowWebSearch() {
        val reply = beginInFlightAssistantReply(threadId = "thread-2", nowMs = 10_000L).transitionTo(
            stage = InFlightAssistantStage.SEARCHING,
            detail = "searching web",
            nowMs = 18_000L,
        )

        val visuals = pendingReplyVisuals(reply = reply, nowMs = 32_000L)

        assertThat(visuals.title).isEqualTo("Searching the web")
        assertThat(visuals.subtitle).contains("live search can take longer")
        assertThat(visuals.elapsedLabel).isEqualTo("00:22")
        assertThat(visuals.timeline.map { it.stage }).containsExactly(
            InFlightAssistantStage.THINKING,
            InFlightAssistantStage.SEARCHING,
            InFlightAssistantStage.STREAMING,
        ).inOrder()
        assertThat(visuals.timeline[1].status).isEqualTo(PendingTimelineStatus.ACTIVE)
    }
}
