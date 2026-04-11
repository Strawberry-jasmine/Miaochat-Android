package com.example.relaychat.core.network

import com.example.relaychat.core.model.ProviderPreset
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChatServiceTransportTest {
    @Test
    fun intelallocUsesUrlConnectionTransport() {
        assertThat(preferredTransportFor(ProviderPreset.INTELALLOC_CODEX.profile))
            .isEqualTo(NetworkTransport.URL_CONNECTION)
    }

    @Test
    fun standardProvidersKeepOkHttpTransport() {
        assertThat(preferredTransportFor(ProviderPreset.OPENAI_RESPONSES.profile))
            .isEqualTo(NetworkTransport.OKHTTP)
    }
}
