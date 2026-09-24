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
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.scrollmeter.app.devtools.DevTools
import com.scrollmeter.app.settings.ThemePreference
import com.scrollmeter.app.ui.navigation.NavIntents
import com.scrollmeter.app.ui.navigation.ScrollMeterNavHost
import com.scrollmeter.app.ui.onboarding.OnboardingScreen
import com.scrollmeter.app.ui.theme.ScrollMeterTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as ScrollMeterApplication).graph
        intent = NavIntents.scrubbed(intent) // before the NavHost reads it
        val devTool = DevTools.entries.getOrNull(DevTools.toolFromLaunch(intent))?.key
        setContent {
            val settings by graph.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
            val dark = when (settings?.theme) {
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
                // The onboarding comes first (spec §31, ADR-032) and the app only after it: nothing opens the
                // accessibility settings before the prominent disclosure. A debug developer screen asked for
                // by the launch intent (tools/device_accuracy.py) skips it; release builds have none.
                val current = settings
                when {
                    current == null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                    current.onboardingCompleted || devTool != null -> ScrollMeterNavHost(
                        graph = graph,
                        initialDevTool = devTool,
                        onOpenAccessibilitySettings = ::openAccessibilitySettings,
                    )
                    else -> OnboardingScreen(graph, onOpenAccessibilitySettings = ::openAccessibilitySettings)
                }
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

    /** Callers go through the disclosure first (AccessibilityGate): the onboarding and ScrollMeterNavHost. */
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
