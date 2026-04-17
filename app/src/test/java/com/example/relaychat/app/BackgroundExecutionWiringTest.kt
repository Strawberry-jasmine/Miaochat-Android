package com.example.relaychat.app

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets
import org.junit.Test

class BackgroundExecutionWiringTest {
    @Test
    fun manifestDeclaresForegroundChatService() {
        val manifest = readProjectFile("app/src/main/AndroidManifest.xml")

        assertThat(manifest).contains("ChatForegroundService")
    }

    @Test
    fun viewModelLaunchesForegroundChatService() {
        val viewModelSource =
            readProjectFile("app/src/main/java/com/example/relaychat/app/RelayChatViewModel.kt")

        assertThat(viewModelSource).contains("ChatForegroundService.startSend")
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
