package com.example.relaychat.app

import com.example.relaychat.core.importer.CodexConfigImportException
import com.example.relaychat.core.model.AppLocale
import com.example.relaychat.core.model.AppSettings
import com.example.relaychat.core.model.AppThemeMode
import com.example.relaychat.core.model.ProviderPreset
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppSettingsMergeTest {
    @Test
    fun importedSettingsPreserveAppLevelLocaleAndTheme() {
        val current = AppSettings(
            provider = ProviderPreset.OPENAI_RESPONSES.profile,
            defaultControls = ProviderPreset.OPENAI_RESPONSES.defaultControls,
            appLocale = AppLocale.SIMPLIFIED_CHINESE,
            themeMode = AppThemeMode.DARK,
        )
        val imported = AppSettings(
            provider = ProviderPreset.INTELALLOC_CODEX.profile,
            defaultControls = ProviderPreset.INTELALLOC_CODEX.defaultControls,
            appLocale = AppLocale.SYSTEM,
            themeMode = AppThemeMode.SYSTEM,
        )

        val merged = importedSettingsPreservingAppPreferences(
            current = current,
            imported = imported,
        )

        assertThat(merged.provider).isEqualTo(imported.provider)
        assertThat(merged.defaultControls).isEqualTo(imported.defaultControls)
        assertThat(merged.appLocale).isEqualTo(AppLocale.SIMPLIFIED_CHINESE)
        assertThat(merged.themeMode).isEqualTo(AppThemeMode.DARK)
    }

    @Test
    fun importFailureMessageUsesLocalizedFallbackForCodexParseErrors() {
        val message = importFailureMessage(
            error = CodexConfigImportException("No config content was provided."),
            localizedFallback = "\u5bfc\u5165\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u914d\u7f6e\u683c\u5f0f\u540e\u91cd\u8bd5\u3002",
        )

        assertThat(message).isEqualTo(
            "\u5bfc\u5165\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u914d\u7f6e\u683c\u5f0f\u540e\u91cd\u8bd5\u3002"
        )
    }

    @Test
    fun importFailureMessageKeepsOtherActionableErrors() {
        val message = importFailureMessage(
            error = IllegalStateException("Disk full"),
            localizedFallback = "\u5bfc\u5165\u5931\u8d25",
        )

        assertThat(message).isEqualTo("Disk full")
    }
}
