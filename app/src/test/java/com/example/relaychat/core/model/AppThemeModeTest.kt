package com.example.relaychat.core.model

import com.example.relaychat.core.network.RelayChatJson
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppThemeModeTest {
    @Test
    fun appSettings_roundTripsThemeModeThroughJson() {
        val settings = AppSettings(
            themeMode = AppThemeMode.DARK,
        )

        val encoded = RelayChatJson.instance.encodeToString(AppSettings.serializer(), settings)
        val decoded = RelayChatJson.instance.decodeFromString(AppSettings.serializer(), encoded)

        assertThat(encoded).contains("\"themeMode\":\"dark\"")
        assertThat(decoded.themeMode).isEqualTo(AppThemeMode.DARK)
    }

    @Test
    fun themeMode_resolvesExplicitAndSystemChoices() {
        assertThat(AppThemeMode.LIGHT.resolve(systemIsDark = true)).isFalse()
        assertThat(AppThemeMode.DARK.resolve(systemIsDark = false)).isTrue()
        assertThat(AppThemeMode.SYSTEM.resolve(systemIsDark = true)).isTrue()
        assertThat(AppThemeMode.SYSTEM.resolve(systemIsDark = false)).isFalse()
    }
}
