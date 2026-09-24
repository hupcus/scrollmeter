package com.scrollmeter.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.scrollmeter.app.MainActivity
import com.scrollmeter.app.R
import com.scrollmeter.app.format.DistanceFormatter
import com.scrollmeter.app.settings.Settings
import java.util.Locale

/**
 * Posts the three notices (spec §26, ADR-030/031) on one channel, "Cíle a rekordy". Tapping one
 * opens the app — an explicit, immutable intent without extras. Without POST_NOTIFICATIONS
 * (API 33+) or with the app's notifications off, [canPost] is false and nothing is attempted.
 */
class AndroidNotificationPoster(private val context: Context) : NotificationPoster {
    private val manager = NotificationManagerCompat.from(context)

    override fun canPost(): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permitted && manager.areNotificationsEnabled()
    }

    override fun post(notice: Notice, settings: Settings) {
        if (!canPost()) return
        ensureChannel()
        val locale = Locale.forLanguageTag(context.getString(R.string.number_locale))
        val distance = DistanceFormatter.format(notice.distanceMm, settings.unitPreference, locale)
        val (title, text) = when (notice.kind) {
            NotificationKind.GOAL -> context.getString(R.string.notification_goal_title) to context.getString(R.string.notification_goal_text, distance)
            NotificationKind.RECORD -> context.getString(R.string.notification_record_title) to context.getString(R.string.notification_record_text, distance)
            NotificationKind.SUMMARY -> context.getString(R.string.notification_summary_title) to context.getString(R.string.notification_summary_text, distance)
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            manager.notify(notice.kind.ordinal + 1, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the post: skip this one (spec §61).
        }
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = context.getString(R.string.notification_channel_description) }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "goals"
    }
}
