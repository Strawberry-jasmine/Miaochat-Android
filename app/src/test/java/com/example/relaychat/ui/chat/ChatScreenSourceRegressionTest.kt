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
