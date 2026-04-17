package com.example.relaychat.ui.chat

import com.example.relaychat.app.InFlightAssistantStage

internal fun pendingStatusBeamStartOffsetPx(
    trackWidthPx: Float,
    beamWidthFraction: Float,
    progress: Float,
): Float {
    val safeTrackWidth = trackWidthPx.coerceAtLeast(0f)
    val safeBeamFraction = beamWidthFraction.coerceIn(0f, 1f)
    val beamWidthPx = safeTrackWidth * safeBeamFraction
    val travelDistance = (safeTrackWidth - beamWidthPx).coerceAtLeast(0f)
    return travelDistance * progress.coerceIn(0f, 1f)
}

internal fun pendingStatusBeamDurationMillis(stage: InFlightAssistantStage): Int = when (stage) {
    InFlightAssistantStage.SEARCHING -> 2_400
    InFlightAssistantStage.THINKING -> 2_050
    InFlightAssistantStage.STREAMING -> 1_650
}
