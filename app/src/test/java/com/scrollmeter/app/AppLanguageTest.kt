package com.scrollmeter.app

import android.app.Application
import android.app.LocaleManager
import android.os.LocaleList
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** A toast or notification built outside an activity speaks the app's language, not the phone's (ADR-035). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
class AppLanguageTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    // Null below API 33 (the last test), where there is no per-app language.
    private val localeManager: LocaleManager? = app.getSystemService(LocaleManager::class.java)

    @After
    fun resetAppLanguage() {
        localeManager?.applicationLocales = LocaleList.getEmptyLocaleList()
    }

    @Test
    fun theAppLanguageWinsOverThePhonesLanguage() {
        localeManager!!.applicationLocales = LocaleList.forLanguageTags("cs")
        assertThat(app.getString(R.string.delete_done)).isEqualTo("All measured data is deleted.")
        assertThat(app.inAppLanguage().getString(R.string.delete_done)).isEqualTo("Všechna naměřená data jsou smazaná.")
        assertThat(app.inAppLanguage().getString(R.string.number_locale)).isEqualTo("cs")
    }

    @Test
    fun englishChosenForTheAppGivesEnglish() {
        localeManager!!.applicationLocales = LocaleList.forLanguageTags("en")
        assertThat(app.inAppLanguage().getString(R.string.delete_done)).isEqualTo("All measured data is deleted.")
    }

    @Test
    fun withoutAnAppLanguageThePhonesLanguageStays() {
        assertThat(app.inAppLanguage().getString(R.string.delete_done)).isEqualTo("All measured data is deleted.")
    }

    @Test
    @Config(sdk = [32])
    fun beforeAndroid13TheContextIsReturnedAsItIs() {
        assertThat(app.inAppLanguage()).isSameInstanceAs(app)
    }
}
