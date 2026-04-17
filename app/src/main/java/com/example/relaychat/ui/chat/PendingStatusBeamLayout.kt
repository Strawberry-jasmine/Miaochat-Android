package com.example.relaychat.ui.chat

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
