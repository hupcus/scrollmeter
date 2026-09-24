package com.scrollmeter.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.scrollmeter.app.devtools.DevTools
import com.scrollmeter.app.ui.calibration.AccuracyScreen
import com.scrollmeter.app.ui.calibration.CalibrationScreen
import com.scrollmeter.app.ui.home.HomeScreen
import com.scrollmeter.app.ui.theme.ScrollMeterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as ScrollMeterApplication).graph
        setContent {
            ScrollMeterTheme {
                ScrollMeterApp(
                    graph = graph,
                    initialTool = DevTools.toolFromLaunch(intent),
                    onOpenAccessibilitySettings = ::openAccessibilitySettings,
                )
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

/** The POC's screens. Navigation Compose with the bottom bar arrives in Phase 4 (D14). */
private enum class Screen { HOME, ACCURACY, CALIBRATION }

/**
 * Home → Přesnost → Kalibrace (or Home → Kalibrace directly). Back returns to where the
 * calibration was opened from; saving or skipping shows the result on Přesnost. Debug builds
 * add the developer screens.
 */
@Composable
private fun ScrollMeterApp(graph: AppGraph, initialTool: Int, onOpenAccessibilitySettings: () -> Unit) {
    val devTools = DevTools.entries
    var openTool by rememberSaveable { mutableIntStateOf(initialTool) }
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var calibrationOpenedFrom by rememberSaveable { mutableStateOf(Screen.HOME) }
    val openCalibration = { from: Screen ->
        calibrationOpenedFrom = from
        screen = Screen.CALIBRATION
    }
    val tool = devTools.getOrNull(openTool)
    when {
        tool != null -> {
            BackHandler { openTool = NO_TOOL }
            tool.content(graph) { openTool = NO_TOOL }
        }
        screen == Screen.CALIBRATION -> {
            BackHandler { screen = calibrationOpenedFrom }
            CalibrationScreen(graph, onBack = { screen = calibrationOpenedFrom }, onSaved = { screen = Screen.ACCURACY })
        }
        screen == Screen.ACCURACY -> {
            BackHandler { screen = Screen.HOME }
            AccuracyScreen(graph, onBack = { screen = Screen.HOME }, onCalibrate = { openCalibration(Screen.ACCURACY) })
        }
        else -> HomeScreen(
            graph = graph,
            devTools = devTools,
            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
            onOpenAccuracy = { screen = Screen.ACCURACY },
            onCalibrate = { openCalibration(Screen.HOME) },
            onOpenDevTool = { openTool = it },
        )
    }
}

private const val NO_TOOL = -1
