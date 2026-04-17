package com.example.relaychat.ui

import com.google.common.truth.Truth.assertThat
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Test

class RelayChatAppSourceRegressionTest {
    @Test
    fun relayChatAppNoLongerMountsModalHistorySheet() {
        val source = readProjectFile("app/src/main/java/com/example/relaychat/ui/RelayChatApp.kt")

        assertThat(source).doesNotContain("HistorySheet(")
    }

    @Test
    fun chatScreenUsesBottomThreadRail() {
        val source = readProjectFile("app/src/main/java/com/example/relaychat/ui/chat/ChatScreen.kt")

        assertThat(source).contains("ThreadHistoryRail(")
    }

    @Test
    fun mainActivityAppliesStoredLocaleBeforeRenderingCompose() {
        val source = readProjectFile("app/src/main/java/com/example/relaychat/MainActivity.kt")

        assertThat(source).contains("AppLocaleManager")
    }

    @Test
    fun mainActivitySynchronizesAppCompatNightModeBeforeRenderingCompose() {
        val source = readProjectFile("app/src/main/java/com/example/relaychat/MainActivity.kt")

        assertThat(source).contains("applyToAppCompat")
    }

    @Test
    fun manifestDeclaresLocaleConfig() {
        val manifest = readProjectFile("app/src/main/AndroidManifest.xml")

        assertThat(manifest).contains("localeConfig")
    }

    @Test
    fun appThemeUsesAppCompatDayNightShell() {
        val themes = readProjectFile("app/src/main/res/values/themes.xml")

        assertThat(themes).contains("Theme.AppCompat.DayNight.NoActionBar")
    }

    @Test
    fun simplifiedChineseStringsFileExists() {
        assertThat(projectFileExists("app/src/main/res/values-zh-rCN/strings.xml")).isTrue()
    }

    @Test
    fun darkLaunchBackgroundResourceExists() {
        assertThat(projectFileExists("app/src/main/res/values-night/colors.xml")).isTrue()
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

    private fun projectFileExists(relativePath: String): Boolean {
        val workingDirectory = Path.of(System.getProperty("user.dir"))
        val direct = workingDirectory.resolve(relativePath)
        if (Files.exists(direct)) {
            return true
        }

        val fromModuleDir = workingDirectory.parent?.resolve(relativePath)
        return fromModuleDir != null && Files.exists(fromModuleDir)
    }
}
