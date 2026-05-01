package com.example.relaychat.ui.chat

import com.google.common.truth.Truth.assertThat
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Test

class ChatScreenSourceRegressionTest {
    @Test
    fun chatScreenNoLongerUsesControlsCardHeading() {
        val source = readProjectFile("app/src/main/java/com/example/relaychat/ui/chat/ChatScreen.kt")

        assertThat(source).doesNotContain("RelaySectionEyebrow(text = \"Controls\")")
    }

    @Test
    fun composerBusyStateUsesThinkingLanguage() {
        val source = readProjectFile("app/src/main/java/com/example/relaychat/ui/chat/ChatScreen.kt")

        assertThat(source).doesNotContain("Text(\"Sending\")")
    }

    @Test
    fun assistantMetadataDoesNotRenderRawRequestPrefix() {
        val source = readProjectFile("app/src/main/java/com/example/relaychat/ui/chat/ChatScreen.kt")

        assertThat(source).doesNotContain("text = \"request \$it\"")
    }

    @Test
    fun chatScreenRoutesModeSwitchToProviderPresets() {
        val source = readProjectFile("app/src/main/java/com/example/relaychat/ui/chat/ChatScreen.kt")

        assertThat(source).contains("viewModel.applyProviderPreset(ProviderPreset.INTELALLOC_CODEX)")
        assertThat(source).contains("viewModel.applyProviderPreset(ProviderPreset.OPENAI_IMAGE)")
        assertThat(source).contains("chat_mode_switch_chat")
        assertThat(source).contains("chat_mode_switch_image")
    }

    @Test
    fun chatModeSwitchIsDisabledWhileSending() {
        val source = readProjectFile("app/src/main/java/com/example/relaychat/ui/chat/ChatScreen.kt")

        assertThat(source).contains("enabled = !uiState.isSending")
    }

    @Test
    fun chatModeSwitchRendersAboveScrollableThreadContent() {
        val source = readProjectFile("app/src/main/java/com/example/relaychat/ui/chat/ChatScreen.kt")

        val modeSwitchIndex = source.indexOf("ProviderModeSwitch(")
        val lazyColumnIndex = source.indexOf("LazyColumn(")

        assertThat(modeSwitchIndex).isAtLeast(0)
        assertThat(lazyColumnIndex).isAtLeast(0)
        assertThat(modeSwitchIndex).isLessThan(lazyColumnIndex)
    }

    @Test
    fun chatScreenDoesNotRenderAnswerPresetTuneChips() {
        val source = readProjectFile("app/src/main/java/com/example/relaychat/ui/chat/ChatScreen.kt")

        assertThat(source).doesNotContain("RequestTuningPreset")
        assertThat(source).doesNotContain("chat_controls_tune")
    }

    private fun readProjectFile(relativePath: String): String {
        val workingDirectory = Path.of(System.getProperty("user.dir"))
        val direct = workingDirectory.resolve(relativePath)
        if (Files.exists(direct)) {
            return String(Files.readAllBytes(direct), StandardCharsets.UTF_8)
        }

        val fromModuleDir = workingDirectory.parent?.resolve(relativePath)
        if (fromModuleDir != null && Files.exists(fromModuleDir)) {
            return String(Files.readAllBytes(fromModuleDir), StandardCharsets.UTF_8)
        }

        error("Could not locate $relativePath from $workingDirectory")
    }
}
