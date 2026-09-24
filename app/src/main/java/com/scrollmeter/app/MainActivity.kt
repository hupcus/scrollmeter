package com.scrollmeter.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.scrollmeter.app.devtools.DevTools
import com.scrollmeter.app.settings.ThemePreference
import com.scrollmeter.app.ui.navigation.NavIntents
import com.scrollmeter.app.ui.navigation.ScrollMeterNavHost
import com.scrollmeter.app.ui.theme.ScrollMeterTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as ScrollMeterApplication).graph
        intent = NavIntents.scrubbed(intent) // before the NavHost reads it
        setContent {
            val theme by graph.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
            val dark = when (theme?.theme) {
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
                else -> isSystemInDarkTheme()
            }
            // System bar icons follow the app's theme, not the system's: a forced dark theme on a light
            // system would otherwise draw dark icons on the dark app.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose { }
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

    private companion object {
        // androidx.activity's own defaults for the three-button navigation bar scrim.
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
