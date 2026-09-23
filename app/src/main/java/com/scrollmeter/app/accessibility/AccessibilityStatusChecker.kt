package com.scrollmeter.app.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/**
 * Is our service switched on in the system settings (spec §32)? Whether it is actually running
 * is a separate signal — `MeasurementMonitor.serviceConnected`, set by the service itself.
 */
class AccessibilityStatusChecker(private val context: Context) {
    private val component = ComponentName(context, ScrollAccessibilityService::class.java)

    fun isEnabled(): Boolean = isInSecureSetting() || isInManagerList()

    private fun isInSecureSetting(): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it.trim()) == component }
    }

    private fun isInManagerList(): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val service = info.resolveInfo?.serviceInfo ?: return@any false
                service.packageName == component.packageName && service.name == component.className
            }
    }
}
