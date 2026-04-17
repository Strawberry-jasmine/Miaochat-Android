package com.example.relaychat.core.importer

import com.example.relaychat.core.model.ProviderApiStyle
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CodexConfigImporterTest {
    @Test
    fun importsIntelallocResponsesConfigAndNormalizesEndpoint() {
        val result = CodexConfigImporter.parse(
            """
            approval_policy = "never"
            sandbox_mode = "danger-full-access"
            model_provider = "intelalloc"
            model = "gpt-5.4"
            model_reasoning_effort = "xhigh"
            model_reasoning_summary = "detailed"
            network_access = "enabled"
            disable_response_storage = true
            model_verbosity = "high"

            [model_providers.intelalloc]
            name = "intelalloc"
            base_url = "https://backend.intelalloc.com/v1/responses"
            wire_api = "responses"
            api_key_env = "INTELALLOC_API_KEY"
            """.trimIndent()
        )

        assertThat(result.settings.provider.displayName).isEqualTo("intelalloc")
        assertThat(result.settings.provider.apiStyle).isEqualTo(ProviderApiStyle.RESPONSES)
        assertThat(result.settings.provider.baseUrl).isEqualTo("https://backend.intelalloc.com")
        assertThat(result.settings.provider.path).isEqualTo("/responses")
        assertThat(result.settings.provider.supportsWebSearch).isTrue()
        assertThat(result.settings.defaultControls.webSearchEnabled).isTrue()
        assertThat(result.settings.defaultControls.responseStorageEnabled).isFalse()
        assertThat(result.apiKeyEnvName).isEqualTo("INTELALLOC_API_KEY")
    }

    @Test
    fun importsIntelallocConfigWithoutNetworkAccessKeepingWebSearchAvailableButDisabledByDefault() {
        val result = CodexConfigImporter.parse(
            """
            model_provider = "intelalloc"
            model = "gpt-5.4"

            [model_providers.intelalloc]
            name = "intelalloc"
            base_url = "https://backend.intelalloc.com"
            wire_api = "responses"
            """.trimIndent()
        )

        assertThat(result.settings.provider.path).isEqualTo("/responses")
        assertThat(result.settings.provider.supportsWebSearch).isTrue()
        assertThat(result.settings.defaultControls.webSearchEnabled).isFalse()
    }
}
