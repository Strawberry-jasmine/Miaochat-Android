package com.example.relaychat.app

import kotlin.math.max

internal fun beginInFlightAssistantReply(
    threadId: String,
    nowMs: Long = System.currentTimeMillis(),
): InFlightAssistantReply = InFlightAssistantReply(
    threadId = threadId,
    stage = InFlightAssistantStage.THINKING,
    detail = "thinking",
    startedAtMs = nowMs,
    stageChangedAtMs = nowMs,
    visitedStages = listOf(InFlightAssistantStage.THINKING),
)

internal fun InFlightAssistantReply.transitionTo(
    stage: InFlightAssistantStage,
    detail: String,
    nowMs: Long = System.currentTimeMillis(),
): InFlightAssistantReply {
    if (this.stage == stage && this.detail == detail) {
        return this
    }

    val nextVisitedStages = if (stage in visitedStages) {
        visitedStages
    } else {
        visitedStages + stage
    }

    return copy(
        stage = stage,
        detail = detail,
        stageChangedAtMs = if (this.stage == stage) stageChangedAtMs else nowMs,
        visitedStages = nextVisitedStages,
    )
}

internal enum class PendingTimelineStatus {
    COMPLETED,
    ACTIVE,
    UPCOMING,
}

internal data class PendingTimelineEntry(
    val stage: InFlightAssistantStage,
    val label: String,
    val status: PendingTimelineStatus,
)

internal data class PendingReplyVisuals(
    val stateLabel: String,
    val title: String,
    val subtitle: String,
    val detail: String,
    val elapsedLabel: String,
    val timeline: List<PendingTimelineEntry>,
)

internal data class PendingReplyTextSet(
    val thinkingTitle: String = "Thinking",
    val searchingTitle: String = "Searching the Web",
    val streamingTitle: String = "Streaming Reply",
    val thinkingSubtitle: String = "Preparing the request and warming up the first tokens.",
    val postSearchThinkingSubtitle: String = "Synthesizing the gathered sources into a final answer.",
    val searchingSubtitle: String = "Gathering live sources, ranking them, and deciding what to cite.",
    val slowSearchingSubtitle: String = "Still gathering live sources. On intelalloc, live search can take longer before the first answer appears.",
    val blankStreamingSubtitle: String = "Drafting the opening lines in real time.",
    val streamingSubtitle: String = "The answer is arriving live. Auto-scroll stays pinned to the newest text.",
    val thinkingStateLabel: String = "thinking",
    val searchingStateLabel: String = "searching",
    val streamingStateLabel: String = "streaming",
    val thinkingTimelineLabel: String = "Think",
    val searchingTimelineLabel: String = "Search",
    val streamingTimelineLabel: String = "Answer",
    val thinkingDetail: String = "Thinking through the request",
    val reasoningDetail: String = "Reasoning through the request",
    val searchingDetail: String = "Running live web search",
    val draftingDetail: String = "Drafting reply",
)

internal fun pendingReplyVisuals(
    reply: InFlightAssistantReply,
    textSet: PendingReplyTextSet = PendingReplyTextSet(),
    nowMs: Long = System.currentTimeMillis(),
): PendingReplyVisuals {
    val elapsedMs = max(0L, nowMs - reply.startedAtMs)
    val timelineStages = listOf(
        InFlightAssistantStage.THINKING,
        InFlightAssistantStage.SEARCHING,
        InFlightAssistantStage.STREAMING,
    )

    val title = when (reply.stage) {
        InFlightAssistantStage.THINKING -> textSet.thinkingTitle
        InFlightAssistantStage.SEARCHING -> textSet.searchingTitle
        InFlightAssistantStage.STREAMING -> textSet.streamingTitle
    }
    val subtitle = when (reply.stage) {
        InFlightAssistantStage.THINKING -> if (InFlightAssistantStage.SEARCHING in reply.visitedStages) {
            textSet.postSearchThinkingSubtitle
        } else {
            textSet.thinkingSubtitle
        }

        InFlightAssistantStage.SEARCHING -> if (elapsedMs >= 15_000L) {
            textSet.slowSearchingSubtitle
        } else {
            textSet.searchingSubtitle
        }

        InFlightAssistantStage.STREAMING -> if (reply.text.isBlank()) {
            textSet.blankStreamingSubtitle
        } else {
            textSet.streamingSubtitle
        }
    }

    return PendingReplyVisuals(
        stateLabel = when (reply.stage) {
            InFlightAssistantStage.THINKING -> textSet.thinkingStateLabel
            InFlightAssistantStage.SEARCHING -> textSet.searchingStateLabel
            InFlightAssistantStage.STREAMING -> textSet.streamingStateLabel
        },
        title = title,
        subtitle = subtitle,
        detail = friendlyPendingDetail(reply.detail, textSet),
        elapsedLabel = formatElapsed(elapsedMs),
        timeline = timelineStages.map { stage ->
            PendingTimelineEntry(
                stage = stage,
                label = when (stage) {
                    InFlightAssistantStage.THINKING -> textSet.thinkingTimelineLabel
                    InFlightAssistantStage.SEARCHING -> textSet.searchingTimelineLabel
                    InFlightAssistantStage.STREAMING -> textSet.streamingTimelineLabel
                },
                status = when {
                    stage == reply.stage -> PendingTimelineStatus.ACTIVE
                    stage in reply.visitedStages -> PendingTimelineStatus.COMPLETED
                    else -> PendingTimelineStatus.UPCOMING
                },
            )
        },
    )
}

private fun friendlyPendingDetail(
    detail: String,
    textSet: PendingReplyTextSet,
): String = when (detail.lowercase()) {
    "sending request",
    "thinking",
    -> textSet.thinkingDetail
    "reasoning" -> textSet.reasoningDetail
    "searching web" -> textSet.searchingDetail
    "drafting reply" -> textSet.draftingDetail
    else -> detail.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

private fun formatElapsed(elapsedMs: Long): String {
    val totalSeconds = elapsedMs / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}
