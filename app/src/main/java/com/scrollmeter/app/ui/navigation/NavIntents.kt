package com.scrollmeter.app.ui.navigation

import android.content.Intent
import android.os.Bundle

/**
 * Navigation's `handleDeepLink` honours explicit deep-link extras (`android-support-nav:controller:…`)
 * on any intent that starts the exported launcher activity — no intent filter needed. ScrollMeter
 * never sends them, so no other app may: they are removed before the NavHost reads the intent.
 */
object NavIntents {
    private const val NAV_EXTRA_PREFIX = "android-support-nav:"

    fun scrubbed(intent: Intent): Intent {
        val navKeys = try {
            intent.extras?.keySet()?.filter { it.startsWith(NAV_EXTRA_PREFIX) }.orEmpty()
        } catch (e: RuntimeException) {
            // Extras that cannot even be unparcelled did not come from us: drop them all.
            return Intent(intent).replaceExtras(null as Bundle?)
        }
        navKeys.forEach(intent::removeExtra)
        return intent
    }
}
