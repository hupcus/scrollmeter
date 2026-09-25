package com.scrollmeter.app.ui.navigation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.scrollmeter.app.AppGraph
import com.scrollmeter.app.apps.PackageNames
import com.scrollmeter.app.onboarding.AccessibilityGate
import com.scrollmeter.app.devtools.DevTools
import com.scrollmeter.app.insights.Period
import com.scrollmeter.app.ui.apps.AppDetailScreen
import com.scrollmeter.app.ui.calibration.AccuracyScreen
import com.scrollmeter.app.ui.calibration.CalibrationScreen
import com.scrollmeter.app.ui.dashboard.DashboardScreen
import com.scrollmeter.app.ui.onboarding.DisclosureScreen
import com.scrollmeter.app.ui.period.PeriodScreen
import com.scrollmeter.app.ui.settings.AboutScreen
import com.scrollmeter.app.ui.settings.ExcludedAppsScreen
import com.scrollmeter.app.ui.settings.ExportScreen
import com.scrollmeter.app.ui.settings.PrivacyScreen
import com.scrollmeter.app.ui.settings.SettingsActions
import com.scrollmeter.app.ui.settings.SettingsScreen
import com.scrollmeter.app.ui.usage.UsageAccessScreen
import kotlinx.serialization.Serializable

// Type-safe routes (D14). Arguments are ours — a package name read back from Room, a period kind and
// an ISO date — and are still validated on arrival.
@Serializable data object OverviewRoute
@Serializable data class PeriodRoute(val kind: String, val anchor: String)
@Serializable data object SettingsRoute
@Serializable data class AppDetailRoute(val packageName: String, val kind: String, val anchor: String)
@Serializable data object AccuracyRoute
@Serializable data object CalibrationRoute
@Serializable data object UsageAccessRoute
@Serializable data class DevToolRoute(val key: String)
@Serializable data object ExcludedAppsRoute
@Serializable data object ExportRoute
@Serializable data object PrivacyRoute
@Serializable data object AboutRoute
@Serializable data object DisclosureRoute

private fun Period.route() = PeriodRoute(kind.name, anchor.toString())

private fun appRoute(packageName: String, period: Period) = AppDetailRoute(packageName, period.kind.name, period.anchor.toString())

/**
 * Přehled is the one top-level screen (ADR-036: no bottom bar); Statistiky, Nastavení and everything
 * else open over it with a way back. [onOpenAccessibilitySettings] is only ever called behind the gate,
 * never passed on (PolicyGuardTest). [initialDevTool] opens a developer screen from the launch intent (debug).
 */
@Composable
fun ScrollMeterNavHost(
    graph: AppGraph,
    initialDevTool: String?,
    disclosureAccepted: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
) {
    val nav = rememberNavController()
    // Spec §30: the dashboard banner and Nastavení reach the accessibility settings only through the
    // prominent disclosure while it has not been accepted (ADR-032). [disclosureAccepted] is the stored
    // flag MainActivity has already loaded — no first frames with a default.
    val openAccessibilitySettings = {
        when (AccessibilityGate.route(disclosureAccepted)) {
            AccessibilityGate.Route.OPEN_SETTINGS -> onOpenAccessibilitySettings()
            AccessibilityGate.Route.SHOW_DISCLOSURE -> nav.open(DisclosureRoute)
        }
    }
    var launchHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialDevTool) {
        if (!launchHandled && initialDevTool != null) nav.navigate(DevToolRoute(initialDevTool))
        launchHandled = true
    }

    // Only for the system bar insets now that there is no bottom bar.
    Scaffold { padding ->
        NavHost(nav, startDestination = OverviewRoute, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            composable<OverviewRoute> {
                DashboardScreen(
                    graph = graph,
                    onOpenAccessibilitySettings = openAccessibilitySettings,
                    onOpenUsageAccess = { nav.open(UsageAccessRoute) },
                    onOpenAccuracy = { nav.open(AccuracyRoute) },
                    onOpenSettings = { nav.open(SettingsRoute) },
                    onOpenPeriod = { nav.open(it.route()) },
                    onOpenApp = { pkg, period -> nav.open(appRoute(pkg, period)) },
                )
            }
            composable<PeriodRoute> { back ->
                val route = back.toRoute<PeriodRoute>()
                val period = Period.parse(route.kind, route.anchor)
                if (period != null) {
                    PeriodScreen(
                        graph = graph,
                        initial = period,
                        onBack = { nav.leave(back) },
                        onOpenDay = { nav.openFrom(back, it.route()) },
                        onOpenApp = { pkg, shown -> nav.openFrom(back, appRoute(pkg, shown)) },
                    )
                } else {
                    LaunchedEffect(back) { nav.leave(back) }
                }
            }
            composable<SettingsRoute> { back ->
                SettingsScreen(
                    graph = graph,
                    devTools = DevTools.entries,
                    onBack = { nav.leave(back) },
                    actions = SettingsActions(
                        openAccessibilitySettings = openAccessibilitySettings,
                        openUsageAccess = { nav.open(UsageAccessRoute) },
                        openAccuracy = { nav.open(AccuracyRoute) },
                        openExcluded = { nav.open(ExcludedAppsRoute) },
                        openExport = { nav.open(ExportRoute) },
                        openPrivacy = { nav.open(PrivacyRoute) },
                        openAbout = { nav.open(AboutRoute) },
                        openDevTool = { nav.open(DevToolRoute(it)) },
                    ),
                )
            }
            composable<ExcludedAppsRoute> { back -> ExcludedAppsScreen(graph, onBack = { nav.leave(back) }) }
            composable<ExportRoute> { back -> ExportScreen(graph, onBack = { nav.leave(back) }) }
            composable<PrivacyRoute> { back -> PrivacyScreen(onBack = { nav.leave(back) }) }
            composable<AboutRoute> { back -> AboutScreen(graph, onBack = { nav.leave(back) }) }
            composable<DisclosureRoute> { back ->
                DisclosureScreen(graph, onAccepted = {
                    nav.leave(back)
                    onOpenAccessibilitySettings()
                }, onBack = { nav.leave(back) })
            }
            composable<AppDetailRoute> { back ->
                val route = back.toRoute<AppDetailRoute>()
                val period = Period.parse(route.kind, route.anchor)
                // Defence in depth next to MainActivity's deep-link scrub: never show an arbitrary string as an app name.
                if (PackageNames.isValid(route.packageName) && period != null) {
                    AppDetailScreen(graph, route.packageName, period, onBack = { nav.leave(back) })
                } else {
                    LaunchedEffect(back) { nav.leave(back) }
                }
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
 * A day opened from a week is a new screen on top of the week, which launchSingleTop would replace
 * instead: here only the screen [from] may open it, and only while it is on top — the double tap guard.
 */
private fun NavController.openFrom(from: NavBackStackEntry, route: Any) {
    if (currentBackStackEntry?.id == from.id) navigate(route)
}

/**
 * Leaves [entry] only while it is the top of the stack: a second tap on "Zpět" during the exit
 * animation would otherwise pop the screen below as well — down to an empty host. Identity, not
 * lifecycle: an entry is not RESUMED during its enter animation or in an unfocused split window.
 */
private fun NavController.leave(entry: NavBackStackEntry) {
    if (currentBackStackEntry?.id == entry.id) popBackStack()
}
