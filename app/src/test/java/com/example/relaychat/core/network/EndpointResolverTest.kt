package com.example.relaychat.core.network

import com.example.relaychat.core.model.ProviderPreset
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EndpointResolverTest {
    @Test
    fun buildsResponseEndpointWithoutDuplicatingSharedSegments() {
        val url = EndpointResolver.buildUrl(
            baseUrl = "https://api.openai.com/v1",
            path = "/v1/responses"
        )

        assertThat(url).isEqualTo("https://api.openai.com/v1/responses")
    }

    @Test
    fun preservesExistingEndpointPathWhenBaseAlreadyContainsIt() {
        val url = EndpointResolver.buildUrl(
            baseUrl = "https://relay.example.com/v1/responses",
            path = "/responses"
        )

        assertThat(url).isEqualTo("https://relay.example.com/v1/responses")
    }

    @Test
    fun buildsIntelallocEndpointAsExpected() {
        val url = EndpointResolver.buildUrl(
            baseUrl = "https://backend.intelalloc.com",
            path = ProviderPreset.INTELALLOC_CODEX.profile.path
        )

        assertThat(url).isEqualTo("https://backend.intelalloc.com/responses")
    }
}
