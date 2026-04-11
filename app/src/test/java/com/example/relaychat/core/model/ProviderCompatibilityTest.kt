package com.example.relaychat.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProviderCompatibilityTest {
    @Test
    fun keepsSavedIntelallocWebSearchCapabilityWhenEnabled() {
        val settings = AppSettings(
            provider = ProviderPreset.INTELALLOC_CODEX.profile.copy(
                supportsWebSearch = true,
            ),
            defaultControls = ProviderPreset.INTELALLOC_CODEX.defaultControls.copy(
                webSearchEnabled = true,
            ),
        )

        val normalized = settings.normalizedForProviderCompatibility()

        assertThat(normalized.provider.supportsWebSearch).isTrue()
        assertThat(normalized.defaultControls.webSearchEnabled).isTrue()
    }

    @Test
    fun leavesNonIntelallocSettingsUntouched() {
        val settings = AppSettings(
            provider = ProviderPreset.OPENAI_RESPONSES.profile,
            defaultControls = ProviderPreset.OPENAI_RESPONSES.defaultControls.copy(
                webSearchEnabled = true,
            ),
        )

        val normalized = settings.normalizedForProviderCompatibility()

        assertThat(normalized).isEqualTo(settings)
    }
}
