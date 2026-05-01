package com.example.relaychat.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProviderCompatibilityTest {
    @Test
    fun intelallocPresetUsesBackendBaseUrl() {
        assertThat(ProviderPreset.INTELALLOC_CODEX.profile.baseUrl)
            .isEqualTo("https://backend.intelalloc.com")
    }

    @Test
    fun intelallocPresetDefaultsToGpt55() {
        assertThat(ProviderPreset.INTELALLOC_CODEX.profile.model)
            .isEqualTo("gpt-5.5")
    }

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

    @Test
    fun openAIImagePresetUsesDedicatedImagesEndpoint() {
        val profile = ProviderPreset.OPENAI_IMAGE.profile

        assertThat(profile.apiStyle).isEqualTo(ProviderApiStyle.IMAGE_GENERATIONS)
        assertThat(profile.baseUrl).isEqualTo("https://backend.intelalloc.com/v1")
        assertThat(profile.path).isEqualTo("/images/generations")
        assertThat(profile.model).isEqualTo("gpt-image-2")
        assertThat(profile.supportsStreaming).isFalse()
        assertThat(profile.supportsWebSearch).isFalse()
    }

    @Test
    fun imageGenerationPresetIsNotNormalizedToIntelallocResponsesEndpoint() {
        val settings = AppSettings(provider = ProviderPreset.OPENAI_IMAGE.profile)

        val normalized = settings.normalizedForProviderCompatibility()

        assertThat(normalized.provider.apiStyle).isEqualTo(ProviderApiStyle.IMAGE_GENERATIONS)
        assertThat(normalized.provider.path).isEqualTo("/images/generations")
        assertThat(normalized.provider.supportsWebSearch).isFalse()
    }

    @Test
    fun savedImagePresetWithOldOpenAiEndpointNormalizesToIntelallocImageEndpoint() {
        val settings = AppSettings(
            provider = ProviderPreset.OPENAI_IMAGE.profile.copy(
                baseUrl = "https://api.openai.com/v1",
            ),
        )

        val normalized = settings.normalizedForProviderCompatibility()

        assertThat(normalized.provider.displayName).isEqualTo("intelalloc Image")
        assertThat(normalized.provider.baseUrl).isEqualTo("https://backend.intelalloc.com/v1")
        assertThat(normalized.provider.path).isEqualTo("/images/generations")
        assertThat(normalized.provider.model).isEqualTo("gpt-image-2")
    }

    @Test
    fun customImageGenerationEndpointIsNotOverwrittenByPresetMigration() {
        val settings = AppSettings(
            provider = ProviderPreset.CUSTOM.profile.copy(
                apiStyle = ProviderApiStyle.IMAGE_GENERATIONS,
                baseUrl = "https://example.test/v1",
                path = "/images/generations",
                model = "gpt-image-2",
            ),
        )

        val normalized = settings.normalizedForProviderCompatibility()

        assertThat(normalized.provider.baseUrl).isEqualTo("https://example.test/v1")
        assertThat(normalized.provider.path).isEqualTo("/images/generations")
        assertThat(normalized.provider.supportsWebSearch).isFalse()
    }
}
