package com.scrollmeter.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.scrollmeter.app.R
import java.util.Locale

/**
 * The locale numbers are formatted in: the language of the strings the app shows, not the device's.
 * A Czech UI on an English phone still writes "2,84 km" (values-en arrives in Phase 7).
 */
@Composable
fun appLocale(): Locale {
    val tag = stringResource(R.string.number_locale)
    return remember(tag) { Locale.forLanguageTag(tag) }
}
