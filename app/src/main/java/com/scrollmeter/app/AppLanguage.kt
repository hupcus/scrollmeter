package com.scrollmeter.app

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build

/**
 * This context's resources in the app's own language. The per-app language of Android 13+
 * (ADR-032) reaches activity contexts only: the application context keeps the phone's language,
 * so a toast or a notification built from it came out in English inside a Czech app (API 33 and 36
 * emulators, ADR-035). Texts shown outside an activity take their strings from here. Below API 33
 * there is no per-app language and the context is returned as it is.
 */
fun Context.inAppLanguage(): Context {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    val locales = getSystemService(LocaleManager::class.java)?.applicationLocales ?: return this
    if (locales.isEmpty) return this
    return createConfigurationContext(Configuration(resources.configuration).apply { setLocales(locales) })
}
