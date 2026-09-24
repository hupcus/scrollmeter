package com.scrollmeter.app.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

/** Why an app is suggested for exclusion (spec §14, PLAN Phase 6). */
enum class ExclusionReason { LAUNCHER, KEYBOARD, SYSTEM_UI }

data class ExclusionSuggestion(val packageName: String, val reason: ExclusionReason)

/**
 * Apps whose scrolling is not "content": the home screen, the keyboard and the system UI. Detected
 * on this phone at runtime — the default home app through `resolveActivity(HOME)` (the manifest's
 * HOME `<queries>` entry, ADR-030), the default keyboard from `Settings.Secure.DEFAULT_INPUT_METHOD`
 * — plus `com.android.systemui`. Nothing else is hard-coded. They are only suggested; the user decides.
 */
class SuggestedExclusions(private val context: Context) {
    fun detect(): List<ExclusionSuggestion> = buildList {
        homePackage()?.let { add(ExclusionSuggestion(it, ExclusionReason.LAUNCHER)) }
        keyboardPackage()?.let { add(ExclusionSuggestion(it, ExclusionReason.KEYBOARD)) }
        add(ExclusionSuggestion(SYSTEM_UI, ExclusionReason.SYSTEM_UI))
    }.distinctBy { it.packageName }

    private fun homePackage(): String? = try {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            // No default home app chosen: the resolver ("android") answers — not a launcher.
            ?.takeIf { it != "android" && PackageNames.isValid(it) }
    } catch (e: RuntimeException) {
        null
    }

    private fun keyboardPackage(): String? = try {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')?.takeIf(PackageNames::isValid)
    } catch (e: RuntimeException) {
        null
    }

    private companion object {
        const val SYSTEM_UI = "com.android.systemui"
    }
}
