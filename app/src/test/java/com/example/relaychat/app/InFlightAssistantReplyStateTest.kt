package com.example.relaychat.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InFlightAssistantReplyStateTest {
    @Test
    fun pendingReplyVisualsUseThinkingDetailForInitialState() {
        val reply = beginInFlightAssistantReply(threadId = "thread-0", nowMs = 1_000L)

        val visuals = pendingReplyVisuals(reply = reply, nowMs = 1_500L)

        assertThat(visuals.stateLabel).isEqualTo("thinking")
        assertThat(visuals.detail).isEqualTo("Thinking through the request")
    }

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

        assertThat(visuals.title).isEqualTo("Searching the Web")
        assertThat(visuals.subtitle).contains("live search can take longer")
        assertThat(visuals.elapsedLabel).isEqualTo("00:22")
        assertThat(visuals.timeline.map { it.stage }).containsExactly(
            InFlightAssistantStage.THINKING,
            InFlightAssistantStage.SEARCHING,
            InFlightAssistantStage.STREAMING,
        ).inOrder()
        assertThat(visuals.timeline[1].status).isEqualTo(PendingTimelineStatus.ACTIVE)
    }

    @Test
    fun pendingReplyVisualsUseInjectedLocalizedText() {
        val reply = beginInFlightAssistantReply(threadId = "thread-3", nowMs = 10_000L).transitionTo(
            stage = InFlightAssistantStage.STREAMING,
            detail = "drafting reply",
            nowMs = 11_000L,
        ).copy(text = "\u4f60\u597d")

        val visuals = pendingReplyVisuals(
            reply = reply,
            textSet = PendingReplyTextSet(
                thinkingTitle = "\u601d\u8003\u4e2d",
                searchingTitle = "\u641c\u7d22\u4e2d",
                streamingTitle = "\u751f\u6210\u4e2d",
                thinkingSubtitle = "\u6b63\u5728\u6574\u7406\u8bf7\u6c42\u3002",
                postSearchThinkingSubtitle = "\u6b63\u5728\u6574\u5408\u68c0\u7d22\u7ed3\u679c\u3002",
                searchingSubtitle = "\u6b63\u5728\u67e5\u627e\u6700\u65b0\u4fe1\u606f\u3002",
                slowSearchingSubtitle = "\u68c0\u7d22\u8017\u65f6\u6bd4\u5e73\u65f6\u66f4\u957f\u3002",
                blankStreamingSubtitle = "\u6b63\u5728\u8d77\u8349\u5f00\u5934\u3002",
                streamingSubtitle = "\u5185\u5bb9\u6b63\u5728\u5b9e\u65f6\u5230\u8fbe\u3002",
                thinkingStateLabel = "\u601d\u8003\u4e2d",
                searchingStateLabel = "\u641c\u7d22\u4e2d",
                streamingStateLabel = "\u751f\u6210\u4e2d",
                thinkingTimelineLabel = "\u601d\u8003",
                searchingTimelineLabel = "\u641c\u7d22",
                streamingTimelineLabel = "\u56de\u590d",
                thinkingDetail = "\u6b63\u5728\u601d\u8003\u8fd9\u4e2a\u8bf7\u6c42",
                reasoningDetail = "\u6b63\u5728\u8fdb\u884c\u6df1\u5165\u63a8\u7406",
                searchingDetail = "\u6b63\u5728\u8054\u7f51\u641c\u7d22",
                draftingDetail = "\u6b63\u5728\u751f\u6210\u56de\u590d",
            ),
            nowMs = 13_000L,
        )

        assertThat(visuals.title).isEqualTo("\u751f\u6210\u4e2d")
        assertThat(visuals.subtitle).isEqualTo("\u5185\u5bb9\u6b63\u5728\u5b9e\u65f6\u5230\u8fbe\u3002")
        assertThat(visuals.detail).isEqualTo("\u6b63\u5728\u751f\u6210\u56de\u590d")
        assertThat(visuals.timeline.map { it.label }).containsExactly(
            "\u601d\u8003",
            "\u641c\u7d22",
            "\u56de\u590d",
        ).inOrder()
    }
}
