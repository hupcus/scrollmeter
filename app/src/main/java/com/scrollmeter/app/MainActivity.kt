package com.scrollmeter.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.scrollmeter.app.devtools.DevTools
import com.scrollmeter.app.settings.ThemePreference
import com.scrollmeter.app.ui.navigation.ScrollMeterNavHost
import com.scrollmeter.app.ui.theme.ScrollMeterTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as ScrollMeterApplication).graph
        setContent {
            val theme by graph.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
            val dark = when (theme?.theme) {
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
                else -> isSystemInDarkTheme()
            }
            ScrollMeterTheme(darkTheme = dark) {
                ScrollMeterNavHost(
                    graph = graph,
                    initialDevTool = DevTools.entries.getOrNull(DevTools.toolFromLaunch(intent))?.key,
                    onOpenAccessibilitySettings = ::openAccessibilitySettings,
                )
            }
        }
    }

    /** Time in app is snapshotted whenever the app comes up (ADR-021); without Usage access a no-op. */
    override fun onResume() {
        super.onResume()
        val graph = (application as ScrollMeterApplication).graph
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                graph.usageSyncer.sync()
            } catch (e: Exception) {
                // Spec §61: keep what was synced last; the next resume retries.
            }
        }
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
