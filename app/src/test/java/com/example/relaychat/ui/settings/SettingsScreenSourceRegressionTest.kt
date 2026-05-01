package com.example.relaychat.ui.settings

import com.google.common.truth.Truth.assertThat
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Test

class SettingsScreenSourceRegressionTest {
    @Test
    fun settingsScreenGroupsModelReasoningVerbosityAndWebTogether() {
        val source = readProjectFile("app/src/main/java/com/example/relaychat/ui/settings/SettingsScreen.kt")

        assertThat(source).contains("ModelCapabilitySection(")
        assertThat(source).contains("SettingsCategorySelector(")
        assertThat(source).contains("settings_model_capabilities_title")
    }

    @Test
    fun settingsScreenDoesNotOfferProviderModelRefresh() {
        val source = readProjectFile("app/src/main/java/com/example/relaychat/ui/settings/SettingsScreen.kt")

        assertThat(source).doesNotContain("refreshProviderModels")
        assertThat(source).doesNotContain("ProviderModelPicker(")
        assertThat(source).doesNotContain("settings_model_list_refresh")
    }

    @Test
    fun settingsScreenRemovesAnswerPresetSection() {
        val source = readProjectFile("app/src/main/java/com/example/relaychat/ui/settings/SettingsScreen.kt")

        assertThat(source).doesNotContain("RequestTuningPreset")
        assertThat(source).doesNotContain("settings_quality_presets_title")
    }

    @Test
    fun settingsScreenKeepsApiKeyInPasswordField() {
        val source = readProjectFile("app/src/main/java/com/example/relaychat/ui/settings/SettingsScreen.kt")

        assertThat(source).contains("PasswordVisualTransformation()")
        assertThat(source).doesNotContain("label = { Text(stringResource(R.string.settings_model_label)) },")
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
