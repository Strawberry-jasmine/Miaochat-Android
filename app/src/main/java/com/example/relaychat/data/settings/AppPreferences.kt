package com.example.relaychat.data.settings

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

internal val Context.relayChatPreferencesDataStore by preferencesDataStore(name = "relaychat_preferences")

internal object PreferenceKeys {
    val appSettingsJson = stringPreferencesKey("app_settings_json")
    val selectedThreadId = stringPreferencesKey("selected_thread_id")
}
