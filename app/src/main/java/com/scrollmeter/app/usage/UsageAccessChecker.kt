package com.scrollmeter.app.usage

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import androidx.core.net.toUri

/**
 * Usage access (`PACKAGE_USAGE_STATS`) is granted by the user in Settings, not at runtime (ADR-021).
 * The app op decides; MODE_DEFAULT falls back to the permission check, as the platform does.
 */
class UsageAccessChecker(private val context: Context) {
    fun isGranted(): Boolean = try {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        // unsafeCheckOpNoThrow (API 29, minSdk since ADR-033) is deprecated by compileSdk 36; it still
        // reads the op mode without throwing.
        @Suppress("DEPRECATION")
        val mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    } catch (e: RuntimeException) {
        false
    }

    /** Opens "Přístup k údajům o využití" (our entry where the OEM supports it), else the settings list. */
    fun openSettings(from: Context) {
        val intents = listOf(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, "package:${context.packageName}".toUri()),
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (intent in intents) {
            try {
                from.startActivity(intent)
                return
            } catch (e: RuntimeException) {
                // ActivityNotFoundException, or an OEM Settings refusing the package: URI
                // (SecurityException / IllegalArgumentException) — try the next, plainer intent.
                continue
            }
        }
    }
}
