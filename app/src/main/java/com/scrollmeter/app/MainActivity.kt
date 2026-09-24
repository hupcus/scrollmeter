package com.scrollmeter.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.scrollmeter.app.devtools.DevTools
import com.scrollmeter.app.ui.calibration.AccuracyScreen
import com.scrollmeter.app.ui.calibration.CalibrationScreen
import com.scrollmeter.app.settings.ThemePreference
import com.scrollmeter.app.ui.dashboard.DashboardScreen
import com.scrollmeter.app.ui.theme.ScrollMeterTheme
import com.scrollmeter.app.ui.usage.UsageAccessScreen
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
                ScrollMeterApp(
                    graph = graph,
                    initialTool = DevTools.toolFromLaunch(intent),
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

/** Until the bottom navigation of Phase 5 (spec §43, D14) the screens switch on a saveable enum. */
private enum class Screen { HOME, ACCURACY, CALIBRATION, USAGE_ACCESS }

/**
 * Přehled → Přesnost → Kalibrace, and Přehled → Čas v aplikacích. Saving or skipping a calibration
 * shows the result on Přesnost. Debug builds add the developer screens.
 */
@Composable
private fun ScrollMeterApp(graph: AppGraph, initialTool: Int, onOpenAccessibilitySettings: () -> Unit) {
    val devTools = DevTools.entries
    var openTool by rememberSaveable { mutableIntStateOf(initialTool) }
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    val tool = devTools.getOrNull(openTool)
    when {
        tool != null -> {
            BackHandler { openTool = NO_TOOL }
            tool.content(graph) { openTool = NO_TOOL }
        }
        screen == Screen.CALIBRATION -> {
            BackHandler { screen = Screen.ACCURACY }
            CalibrationScreen(graph, onBack = { screen = Screen.ACCURACY }, onSaved = { screen = Screen.ACCURACY })
        }
        screen == Screen.ACCURACY -> {
            BackHandler { screen = Screen.HOME }
            AccuracyScreen(graph, onBack = { screen = Screen.HOME }, onCalibrate = { screen = Screen.CALIBRATION })
        }
        screen == Screen.USAGE_ACCESS -> {
            BackHandler { screen = Screen.HOME }
            UsageAccessScreen(graph, onGranted = { screen = Screen.HOME }, onBack = { screen = Screen.HOME })
        }
        else -> DashboardScreen(
            graph = graph,
            devTools = devTools,
            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
            onOpenUsageAccess = { screen = Screen.USAGE_ACCESS },
            onOpenAccuracy = { screen = Screen.ACCURACY },
            onOpenDevTool = { openTool = it },
        )
    }
}

private const val NO_TOOL = -1
