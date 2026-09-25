package com.scrollmeter.app.notifications

import android.Manifest
import android.app.Application
import android.app.LocaleManager
import android.app.Notification
import android.app.NotificationManager
import android.os.LocaleList
import com.google.common.truth.Truth.assertThat
import com.scrollmeter.app.settings.Settings
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Notices are posted from the application context, yet in the app's language (ADR-035). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
class AndroidNotificationPosterTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val localeManager = app.getSystemService(LocaleManager::class.java)
    private val notificationManager = app.getSystemService(NotificationManager::class.java)

    @After
    fun resetAppLanguage() {
        localeManager.applicationLocales = LocaleList.getEmptyLocaleList()
    }

    private fun postLimit(): Notification {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        AndroidNotificationPoster(app).post(Notice(NotificationKind.LIMIT, 1_250_000.0), Settings())
        return shadowOf(notificationManager).allNotifications.single()
    }

    @Test
    fun aCzechAppOnAnEnglishPhoneNotifiesInCzech() {
        localeManager.applicationLocales = LocaleList.forLanguageTags("cs")
        val notice = postLimit()
        assertThat(notice.extras.getString(Notification.EXTRA_TITLE)).isEqualTo("Denní limit překročen")
        assertThat(notice.extras.getString(Notification.EXTRA_TEXT)).isEqualTo("Dnes jsi přes svůj denní limit 1,25 km.")
        assertThat(notificationManager.getNotificationChannel("goals").name.toString()).isEqualTo("Limit a shrnutí")
    }

    @Test
    fun withoutAnAppLanguageThePhonesLanguageIsUsed() {
        val notice = postLimit()
        assertThat(notice.extras.getString(Notification.EXTRA_TITLE)).isEqualTo("Daily limit exceeded")
        assertThat(notice.extras.getString(Notification.EXTRA_TEXT)).isEqualTo("Today you are over your daily limit of 1.25 km.")
    }
}
