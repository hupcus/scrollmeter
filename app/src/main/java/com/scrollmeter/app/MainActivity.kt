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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.scrollmeter.app.devtools.DevTools
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

/** Phase 1 navigation: home plus the debug-only developer screens. Navigation Compose arrives in Phase 4. */
@Composable
private fun ScrollMeterApp(graph: AppGraph, initialTool: Int, onOpenAccessibilitySettings: () -> Unit) {
    val devTools = DevTools.entries
    var openTool by rememberSaveable { mutableIntStateOf(initialTool) }
    val tool = devTools.getOrNull(openTool)
    if (tool != null) {
        BackHandler { openTool = NO_TOOL }
        tool.content(graph) { openTool = NO_TOOL }
    } else {
        HomeScreen(
            graph = graph,
            devTools = devTools,
            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
            onOpenDevTool = { openTool = it },
        )
    }
}

private const val NO_TOOL = -1
