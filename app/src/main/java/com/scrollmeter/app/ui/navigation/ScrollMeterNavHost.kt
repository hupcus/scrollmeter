package com.scrollmeter.app.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.scrollmeter.app.AppGraph
import com.scrollmeter.app.R
import com.scrollmeter.app.devtools.DevTools
import com.scrollmeter.app.ui.apps.AppDetailScreen
import com.scrollmeter.app.ui.apps.AppsScreen
import com.scrollmeter.app.ui.calibration.AccuracyScreen
import com.scrollmeter.app.ui.calibration.CalibrationScreen
import com.scrollmeter.app.ui.dashboard.DashboardScreen
import com.scrollmeter.app.ui.history.HistoryScreen
import com.scrollmeter.app.ui.settings.SettingsScreen
import com.scrollmeter.app.ui.usage.UsageAccessScreen
import kotlinx.serialization.Serializable

// Type-safe routes (D14). A package name is the only argument; it is ours, read back from Room.
@Serializable data object OverviewRoute
@Serializable data object HistoryRoute
@Serializable data object AppsRoute
@Serializable data object SettingsRoute
@Serializable data class AppDetailRoute(val packageName: String)
@Serializable data object AccuracyRoute
@Serializable data object CalibrationRoute
@Serializable data object UsageAccessRoute
@Serializable data class DevToolRoute(val key: String)

private class TopLevel(val route: Any, @get:StringRes val label: Int, @get:DrawableRes val icon: Int)

private val TOP_LEVEL = listOf(
    TopLevel(OverviewRoute, R.string.nav_overview, R.drawable.ic_nav_overview),
    TopLevel(HistoryRoute, R.string.nav_history, R.drawable.ic_nav_history),
    TopLevel(AppsRoute, R.string.nav_apps, R.drawable.ic_nav_apps),
    TopLevel(SettingsRoute, R.string.nav_settings, R.drawable.ic_nav_settings),
)

/**
 * Přehled / Historie / Aplikace / Nastavení in a bottom bar (spec §43); the bar hides on the screens
 * below them (app detail, Přesnost, Kalibrace, Čas v aplikacích, developer screens). Switching tabs
 * keeps each tab's state. [initialDevTool] opens a developer screen from the launch intent (debug).
 */
@Composable
fun ScrollMeterNavHost(graph: AppGraph, initialDevTool: String?, onOpenAccessibilitySettings: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val onTopLevel = TOP_LEVEL.any { destination?.hasRoute(it.route::class) == true }
    var launchHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialDevTool) {
        if (!launchHandled && initialDevTool != null) nav.navigate(DevToolRoute(initialDevTool))
        launchHandled = true
    }

    Scaffold(
        bottomBar = {
            if (onTopLevel) {
                NavigationBar {
                    TOP_LEVEL.forEach { item ->
                        NavigationBarItem(
                            selected = destination?.hierarchy?.any { it.hasRoute(item.route::class) } == true,
                            onClick = {
                                nav.navigate(item.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(painterResource(item.icon), contentDescription = null) },
                            label = { Text(stringResource(item.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = OverviewRoute, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            composable<OverviewRoute> {
                DashboardScreen(
                    graph = graph,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onOpenUsageAccess = { nav.open(UsageAccessRoute) },
                    onOpenAccuracy = { nav.open(AccuracyRoute) },
                    onOpenApp = { nav.open(AppDetailRoute(it)) },
                )
            }
            composable<HistoryRoute> { HistoryScreen(graph) }
            composable<AppsRoute> { AppsScreen(graph, onOpenApp = { nav.open(AppDetailRoute(it)) }) }
            composable<SettingsRoute> {
                SettingsScreen(
                    graph = graph,
                    devTools = DevTools.entries,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onOpenUsageAccess = { nav.open(UsageAccessRoute) },
                    onOpenAccuracy = { nav.open(AccuracyRoute) },
                    onOpenDevTool = { nav.open(DevToolRoute(it)) },
                )
            }
            composable<AppDetailRoute> { back ->
                AppDetailScreen(graph, back.toRoute<AppDetailRoute>().packageName, onBack = { nav.leave(back) })
            }
            composable<AccuracyRoute> { back ->
                AccuracyScreen(graph, onBack = { nav.leave(back) }, onCalibrate = { nav.open(CalibrationRoute) })
            }
            // Saving or skipping a calibration returns to Přesnost, which shows the result.
            composable<CalibrationRoute> { back -> CalibrationScreen(graph, onBack = { nav.leave(back) }, onSaved = { nav.leave(back) }) }
            composable<UsageAccessRoute> { back -> UsageAccessScreen(graph, onGranted = { nav.leave(back) }, onBack = { nav.leave(back) }) }
            if (DevTools.entries.isNotEmpty()) {
                composable<DevToolRoute> { back ->
                    val key = back.toRoute<DevToolRoute>().key
                    DevTools.entries.firstOrNull { it.key == key }?.content?.invoke(graph) { nav.leave(back) }
                }
            }
        }
    }
}

/** A double tap must not stack the same screen twice. */
private fun NavController.open(route: Any) = navigate(route) { launchSingleTop = true }

/**
 * Leaves [entry] only while it is the resumed screen: a second tap on "Zpět" during the exit
 * animation would otherwise pop the screen below as well — down to an empty host.
 */
private fun NavController.leave(entry: NavBackStackEntry) {
    if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) popBackStack()
}
