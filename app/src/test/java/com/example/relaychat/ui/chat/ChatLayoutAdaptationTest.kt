package com.example.relaychat.ui.chat

import com.google.common.truth.Truth.assertThat
import androidx.compose.ui.unit.dp
import org.junit.Test

class ChatLayoutAdaptationTest {
    @Test
    fun narrowScreensUseOverlayHistoryRail() {
        assertThat(historyRailLayoutMode(420.dp)).isEqualTo(HistoryRailLayoutMode.OVERLAY)
    }

    @Test
    fun wideScreensUsePersistentHistoryRail() {
        assertThat(historyRailLayoutMode(960.dp)).isEqualTo(HistoryRailLayoutMode.PERSISTENT)
    }
}
