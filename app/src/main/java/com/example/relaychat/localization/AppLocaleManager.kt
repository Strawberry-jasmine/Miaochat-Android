package com.example.relaychat.localization

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.relaychat.core.model.AppLocale

object AppLocaleManager {
    fun apply(appLocale: AppLocale) {
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(appLocale.languageTag),
        )
    }

    fun currentAppLocale(): AppLocale =
        AppLocale.fromLanguageTag(AppCompatDelegate.getApplicationLocales().toLanguageTags())
}
