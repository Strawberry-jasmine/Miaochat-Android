package com.example.relaychat

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.example.relaychat.data.settings.SettingsRepository
import com.example.relaychat.localization.AppLocaleManager
import com.example.relaychat.ui.RelayChatApp
import com.example.relaychat.ui.theme.applyToAppCompat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val storedSettings = runBlocking {
            SettingsRepository(applicationContext).settingsFlow.first()
        }
        storedSettings.themeMode.applyToAppCompat()
        if (AppLocaleManager.currentAppLocale() != storedSettings.appLocale) {
            AppLocaleManager.apply(storedSettings.appLocale)
        }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RelayChatApp()
        }
    }
}
