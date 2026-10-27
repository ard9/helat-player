package io.github.ard9.linguaplayer

import android.content.Context
import android.content.res.Configuration
import io.github.ard9.linguaplayer.data.AppLanguage
import io.github.ard9.linguaplayer.data.SettingsRepository
import java.util.Locale

/**
 * [base] with [language] as its locale. Only the locale (and with it the layout direction) is
 * overridden, so orientation and other changes still come from the system.
 */
fun withLanguage(base: Context, language: AppLanguage): Context {
    if (language == AppLanguage.System) return base
    val config = Configuration().apply { setLocale(Locale.forLanguageTag(language.tag)) }
    return base.createConfigurationContext(config)
}

/** [base] in the language chosen in Settings; for text made outside the activity. */
fun inAppLanguage(base: Context): Context = withLanguage(base, SettingsRepository.language(base))
