package com.example.relaychat.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.example.relaychat.core.model.AppSettings
import com.example.relaychat.core.model.normalizedForProviderCompatibility
import com.example.relaychat.core.network.RelayChatJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(
    private val context: Context,
    private val apiKeyVault: ApiKeyVault = KeystoreFileApiKeyVault(context),
) {
    val settingsFlow: Flow<AppSettings> = context.relayChatPreferencesDataStore.data.map { preferences ->
        preferences[PreferenceKeys.appSettingsJson]
            ?.let { encoded ->
                runCatching {
                    RelayChatJson.instance.decodeFromString(AppSettings.serializer(), encoded)
                }.getOrNull()
            }
            ?: AppSettings.Default
    }.map { settings ->
        settings.normalizedForProviderCompatibility()
    }

    suspend fun writeSettings(settings: AppSettings) {
        val normalized = settings.normalizedForProviderCompatibility()
        context.relayChatPreferencesDataStore.edit { preferences ->
            preferences[PreferenceKeys.appSettingsJson] =
                RelayChatJson.instance.encodeToString(AppSettings.serializer(), normalized)
        }
    }

    suspend fun readApiKey(): String = apiKeyVault.read()

    suspend fun writeApiKey(value: String) {
        apiKeyVault.write(value)
    }
}
