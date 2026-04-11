package com.example.relaychat.app

import kotlin.math.max

internal fun beginInFlightAssistantReply(
    threadId: String,
    nowMs: Long = System.currentTimeMillis(),
): InFlightAssistantReply = InFlightAssistantReply(
    threadId = threadId,
    stage = InFlightAssistantStage.THINKING,
    detail = "sending request",
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

internal fun pendingReplyVisuals(
    reply: InFlightAssistantReply,
    nowMs: Long = System.currentTimeMillis(),
): PendingReplyVisuals {
    val elapsedMs = max(0L, nowMs - reply.startedAtMs)
    val timelineStages = listOf(
        InFlightAssistantStage.THINKING,
        InFlightAssistantStage.SEARCHING,
        InFlightAssistantStage.STREAMING,
    )

    val title = when (reply.stage) {
        InFlightAssistantStage.THINKING -> "Thinking"
        InFlightAssistantStage.SEARCHING -> "Searching the web"
        InFlightAssistantStage.STREAMING -> "Streaming answer"
    }
    val subtitle = when (reply.stage) {
        InFlightAssistantStage.THINKING -> if (InFlightAssistantStage.SEARCHING in reply.visitedStages) {
            "Synthesizing the gathered sources into a final answer."
        } else {
            "Preparing the request and warming up the first tokens."
        }

        InFlightAssistantStage.SEARCHING -> if (elapsedMs >= 15_000L) {
            "Still gathering live sources. On intelalloc, live search can take longer before the first answer appears."
        } else {
            "Gathering live sources, ranking them, and deciding what to cite."
        }

        InFlightAssistantStage.STREAMING -> if (reply.text.isBlank()) {
            "Drafting the opening lines in real time."
        } else {
            "The answer is arriving live. Auto-scroll will stay pinned to the newest text."
        }
    }

    return PendingReplyVisuals(
        stateLabel = when (reply.stage) {
            InFlightAssistantStage.THINKING -> "thinking"
            InFlightAssistantStage.SEARCHING -> "searching web"
            InFlightAssistantStage.STREAMING -> "streaming"
        },
        title = title,
        subtitle = subtitle,
        detail = friendlyPendingDetail(reply.detail),
        elapsedLabel = formatElapsed(elapsedMs),
        timeline = timelineStages.map { stage ->
            PendingTimelineEntry(
                stage = stage,
                label = when (stage) {
                    InFlightAssistantStage.THINKING -> "Think"
                    InFlightAssistantStage.SEARCHING -> "Search"
                    InFlightAssistantStage.STREAMING -> "Answer"
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

private fun friendlyPendingDetail(detail: String): String = when (detail.lowercase()) {
    "sending request" -> "Sending request"
    "reasoning" -> "Reasoning through the request"
    "searching web" -> "Running live web search"
    "drafting reply" -> "Drafting reply"
    else -> detail.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

private fun formatElapsed(elapsedMs: Long): String {
    val totalSeconds = elapsedMs / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}
