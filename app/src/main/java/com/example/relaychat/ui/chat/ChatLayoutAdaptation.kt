package com.example.relaychat.ui.chat

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal enum class HistoryRailLayoutMode {
    OVERLAY,
    PERSISTENT,
}

internal fun historyRailLayoutMode(screenWidth: Dp): HistoryRailLayoutMode =
    if (screenWidth >= 840.dp) {
        HistoryRailLayoutMode.PERSISTENT
    } else {
        HistoryRailLayoutMode.OVERLAY
    }
