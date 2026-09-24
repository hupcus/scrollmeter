package com.scrollmeter.app.apps

import android.content.Context
import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** What the UI shows for a package: its label, or the package name when the app is not visible. */
data class AppInfo(val packageName: String, val label: String, val icon: ImageBitmap?)

/**
 * App labels and icons through `PackageManager` (spec §15, ADR-008). Visibility comes from the
 * manifest's `<queries>` for launcher activities — no `QUERY_ALL_PACKAGES`; an app we cannot see
 * (no launcher entry, uninstalled) shows its package name and no icon, and nothing throws.
 * UI only, never on the measurement path; blocking — call it off the main thread. Cached.
 */
class AppInfoProvider(private val context: Context) {
    private val cache = LruCache<String, AppInfo>(64)

    fun load(packageName: String, iconSizePx: Int): AppInfo {
        cache.get(packageName)?.let { return it }
        val pm = context.packageManager
        val info = try {
            val app = pm.getApplicationInfo(packageName, 0)
            val icon = runCatching { pm.getApplicationIcon(app).toBitmap(iconSizePx, iconSizePx).asImageBitmap() }.getOrNull()
            AppInfo(packageName, app.loadLabel(pm).toString().ifBlank { packageName }, icon)
        } catch (e: PackageManager.NameNotFoundException) {
            AppInfo(packageName, packageName, null)
        } catch (e: RuntimeException) {
            // Spec §15: a package we cannot read must never crash the screen.
            AppInfo(packageName, packageName, null)
        }
        cache.put(packageName, info)
        return info
    }
}
