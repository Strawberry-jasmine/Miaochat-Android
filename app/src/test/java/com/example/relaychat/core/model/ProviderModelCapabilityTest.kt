package com.example.relaychat.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProviderModelCapabilityTest {
    @Test
    fun openAiResponsesModelKeepsStandardReasoningVerbosityAndWebControls() {
        val updated = ProviderPreset.OPENAI_RESPONSES.profile.withSelectedModel("gpt-5.4-mini")
        val capabilities = updated.capabilitiesForSelectedModel()

        assertThat(updated.model).isEqualTo("gpt-5.4-mini")
        assertThat(updated.supportsVerbosity).isTrue()
        assertThat(updated.supportsWebSearch).isTrue()
        assertThat(capabilities.reasoningEfforts)
            .containsExactly(
                ReasoningEffort.NONE,
                ReasoningEffort.MINIMAL,
                ReasoningEffort.LOW,
                ReasoningEffort.MEDIUM,
                ReasoningEffort.HIGH,
                ReasoningEffort.XHIGH,
            )
            .inOrder()
        assertThat(capabilities.verbosityLevels)
            .containsExactly(VerbosityLevel.LOW, VerbosityLevel.MEDIUM, VerbosityLevel.HIGH)
            .inOrder()
    }

    @Test
    fun imageModelDisablesChatOnlyCapabilities() {
        val updated = ProviderPreset.OPENAI_IMAGE.profile.withSelectedModel("gpt-image-2")
        val capabilities = updated.capabilitiesForSelectedModel()

        assertThat(updated.apiStyle).isEqualTo(ProviderApiStyle.IMAGE_GENERATIONS)
        assertThat(updated.supportsStreaming).isFalse()
        assertThat(updated.supportsWebSearch).isFalse()
        assertThat(updated.supportsVerbosity).isFalse()
        assertThat(capabilities.reasoningEfforts).containsExactly(ReasoningEffort.NONE)
        assertThat(capabilities.verbosityLevels).isEmpty()
        assertThat(capabilities.supportsImageGeneration).isTrue()
    }

    @Test
    fun chatCompletionsModelKeepsWebDisabledUnlessProviderMappingEnablesIt() {
        val updated = ProviderPreset.OPENAI_CHAT_COMPLETIONS.profile.withSelectedModel("gpt-4.1-mini")

        assertThat(updated.supportsWebSearch).isFalse()
        assertThat(updated.supportsVerbosity).isFalse()
        assertThat(updated.capabilitiesForSelectedModel().verbosityLevels).isEmpty()
    }
}
