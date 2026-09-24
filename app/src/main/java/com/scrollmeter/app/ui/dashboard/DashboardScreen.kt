package com.scrollmeter.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollmeter.app.AppGraph
import com.scrollmeter.app.R
import com.scrollmeter.app.apps.AppInfo
import com.scrollmeter.app.calibration.CalibrationMethod
import com.scrollmeter.app.data.model.AppSummary
import com.scrollmeter.app.data.model.DateRange
import com.scrollmeter.app.devtools.DevToolEntry
import com.scrollmeter.app.format.DistanceFormatter
import com.scrollmeter.app.format.TimeFormatter
import com.scrollmeter.app.insights.DistanceComparison
import com.scrollmeter.app.insights.DistanceComparisonProvider
import com.scrollmeter.app.insights.Qualifier
import com.scrollmeter.app.insights.Reference
import com.scrollmeter.app.insights.TopApps
import com.scrollmeter.app.measurement.PhysicalScaleProvider
import com.scrollmeter.app.settings.Settings
import com.scrollmeter.app.settings.UnitPreference
import com.scrollmeter.app.ui.components.appLocale
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Spec §32: never claim data is being collected unless the service is actually running. */
enum class ServiceStatus { ON, ENABLED_NOT_RUNNING, OFF }

/**
 * Přehled (spec §21): today's distance against the goal, this week / month / in total, the top
 * apps today, and at most one comparison. Everything reads live from [AppGraph.scrollRepository]
 * (stored + not yet flushed). When the service is not running, a banner says so first (§32).
 * Time in app (D19) appears in the app rows once Usage access is granted; until then one card
 * offers it and can be dismissed for good.
 */
@Composable
fun DashboardScreen(
    graph: AppGraph,
    devTools: List<DevToolEntry>,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onOpenAccuracy: () -> Unit,
    onOpenDevTool: (Int) -> Unit,
) {
    val locale = appLocale()
    val scope = rememberCoroutineScope()
    val connected by graph.monitor.serviceConnected.collectAsStateWithLifecycle()
    var enabledInSettings by remember { mutableStateOf(graph.statusChecker.isEnabled()) }
    var usageGranted by remember { mutableStateOf(graph.usageAccessChecker.isGranted()) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    LifecycleResumeEffect(Unit) {
        enabledInSettings = graph.statusChecker.isEnabled()
        usageGranted = graph.usageAccessChecker.isGranted()
        today = LocalDate.now()
        onPauseOrDispose { }
    }
    LaunchedEffect(connected) { enabledInSettings = graph.statusChecker.isEnabled() }
    // The screen may stay open across midnight: roll "Dnes" over without waiting for a resume.
    LaunchedEffect(today) {
        val midnight = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        delay((midnight - System.currentTimeMillis()).coerceAtLeast(0) + 1_000)
        today = LocalDate.now()
    }
    val status = when {
        connected -> ServiceStatus.ON
        enabledInSettings -> ServiceStatus.ENABLED_NOT_RUNNING
        else -> ServiceStatus.OFF
    }

    val repository = graph.scrollRepository
    // Null until loaded: a card the user dismissed must not flash up while settings are read.
    val loadedSettings by graph.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    val settings = loadedSettings ?: Settings()
    val todayMm by remember(today) { repository.distance(DateRange.day(today)) }.collectAsStateWithLifecycle(initialValue = null)
    val weekMm by remember(today) { repository.distance(DateRange.week(today)) }.collectAsStateWithLifecycle(initialValue = null)
    val monthMm by remember(today) { repository.distance(DateRange.month(today)) }.collectAsStateWithLifecycle(initialValue = null)
    val lifetimeMm by remember { repository.lifetimeDistance() }.collectAsStateWithLifecycle(initialValue = null)
    val apps by remember(today) { repository.apps(DateRange.day(today)) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val calibration by graph.calibrationRepository.state.collectAsStateWithLifecycle(initialValue = null)
    val display = remember(LocalConfiguration.current.orientation) { graph.displayMetricsProvider.read() }

    fun distance(mm: Double?): String = mm?.let { DistanceFormatter.format(it, settings.unitPreference, locale) } ?: TimeFormatter.UNKNOWN

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (status == ServiceStatus.ON) MeasuringChip()
            }
            if (status != ServiceStatus.ON) ServiceBanner(status, onOpenAccessibilitySettings)
            TodayHeader(todayMm, settings.dailyGoalMm, ::distance)
            PeriodStats(distance(weekMm), distance(monthMm), distance(lifetimeMm))
            TopAppsCard(graph, TopApps.of(apps), usageGranted, settings.unitPreference, locale)
            if (!usageGranted && loadedSettings?.usageTimeCardDismissed == false) {
                UsageTimeCard(
                    onShow = onOpenUsageAccess,
                    onDismiss = { scope.launch { graph.settingsRepository.setUsageTimeCardDismissed(true) } },
                )
            }
            if (settings.showComparisons) todayMm?.let(DistanceComparisonProvider::compare)?.let { ComparisonCard(it) }
            calibration?.let { AccuracyRow(PhysicalScaleProvider.resolve(it, display).method == CalibrationMethod.MANUAL_CARD, onOpenAccuracy) }
            if (devTools.isNotEmpty()) {
                Text(stringResource(R.string.home_devtools_title), style = MaterialTheme.typography.titleSmall)
                devTools.forEachIndexed { index, tool ->
                    OutlinedButton(onClick = { onOpenDevTool(index) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(tool.titleRes)) }
                }
            }
        }
    }
}

@Composable
private fun MeasuringChip() {
    Row(
        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.dashboard_measuring), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

/** Spec §32: prominent, first, with the one action that fixes it. */
@Composable
private fun ServiceBanner(status: ServiceStatus, onOpenAccessibilitySettings: () -> Unit) {
    val (title, body) = if (status == ServiceStatus.OFF) {
        R.string.home_status_off to R.string.home_status_off_body
    } else {
        R.string.home_status_not_running to R.string.home_status_not_running_body
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            Button(onClick = onOpenAccessibilitySettings) {
                Text(stringResource(if (status == ServiceStatus.OFF) R.string.home_enable_button else R.string.home_settings_button))
            }
        }
    }
}

/** The main number, very prominent (spec §42), inside a progress ring towards the daily goal (§21, §25). */
@Composable
private fun TodayHeader(todayMm: Double?, goalMm: Double, distance: (Double?) -> String) {
    val progress = ((todayMm ?: 0.0) / goalMm).toFloat().coerceIn(0f, 1f)
    val reached = todayMm != null && todayMm >= goalMm
    val track = MaterialTheme.colorScheme.surfaceVariant
    val bar = MaterialTheme.colorScheme.primary
    val strokePx = with(LocalDensity.current) { 14.dp.toPx() }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.home_today_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(8.dp))
        Box(Modifier.size(232.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val inset = strokePx / 2
                val arcSize = Size(size.width - strokePx, size.height - strokePx)
                drawArc(track, 0f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(strokePx))
                if (progress > 0f) {
                    drawArc(bar, -90f, 360f * progress, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(distance(todayMm), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    stringResource(if (reached) R.string.dashboard_goal_reached else R.string.dashboard_goal, distance(goalMm)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PeriodStats(week: String, month: String, lifetime: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            StatRow(stringResource(R.string.dashboard_week), week)
            HorizontalDivider()
            StatRow(stringResource(R.string.dashboard_month), month)
            HorizontalDivider()
            StatRow(stringResource(R.string.dashboard_lifetime), lifetime)
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun TopAppsCard(graph: AppGraph, top: TopApps, usageGranted: Boolean, unit: UnitPreference, locale: Locale) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.dashboard_top_apps), style = MaterialTheme.typography.titleMedium)
            if (top.isEmpty) {
                Text(stringResource(R.string.dashboard_no_scroll_yet), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            top.apps.forEach { app -> AppRow(graph, app, usageGranted, unit, locale) }
            top.other?.let { AppRow(graph, it, usageGranted, unit, locale) }
        }
    }
}

/** Distance, and time: in app with Usage access, scrolling otherwise (ADR-022). Unknown is "—", never 0. */
@Composable
private fun AppRow(graph: AppGraph, app: AppSummary, usageGranted: Boolean, unit: UnitPreference, locale: Locale) {
    val other = app.packageName == TopApps.OTHER
    val iconPx = with(LocalDensity.current) { 36.dp.roundToPx() }
    val info by produceState<AppInfo?>(initialValue = null, app.packageName) {
        value = if (other) null else withContext(Dispatchers.IO) { graph.appInfoProvider.load(app.packageName, iconPx) }
    }
    val label = if (other) stringResource(R.string.dashboard_other_apps) else info?.label ?: app.packageName
    val detail = if (usageGranted) {
        val pace = TimeFormatter.pace(app.distanceMm, app.foregroundMs, locale)
        val time = TimeFormatter.duration(app.foregroundMs)
        if (pace == TimeFormatter.UNKNOWN) stringResource(R.string.dashboard_time_in_app, time) else stringResource(R.string.dashboard_time_in_app_pace, time, pace)
    } else {
        stringResource(R.string.dashboard_scroll_time, TimeFormatter.duration(app.activeScrollMs))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        val icon = info?.icon
        if (icon != null) {
            Image(icon, contentDescription = null, modifier = Modifier.size(36.dp))
        } else {
            Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                Text(label.take(1).uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        Text(app.distanceMm?.let { DistanceFormatter.format(it, unit, locale) } ?: TimeFormatter.UNKNOWN, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun UsageTimeCard(onShow: () -> Unit, onDismiss: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.dashboard_usage_card_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(stringResource(R.string.dashboard_usage_card_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onShow) { Text(stringResource(R.string.dashboard_usage_card_show)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.not_now)) }
            }
        }
    }
}

/** Spec §22: one comparison, an illustration only. */
@Composable
private fun ComparisonCard(comparison: DistanceComparison) {
    val qualifier = stringResource(
        when (comparison.qualifier) {
            Qualifier.ALMOST -> R.string.comparison_almost
            Qualifier.ABOUT -> R.string.comparison_about
            Qualifier.MORE_THAN -> R.string.comparison_more_than
        },
    )
    val plural = when (comparison.reference) {
        Reference.FOOTBALL_FIELD -> R.plurals.comparison_football_field
        Reference.EIFFEL_TOWER -> R.plurals.comparison_eiffel_tower
        Reference.RUNNING_TRACK_LAP -> R.plurals.comparison_running_track_lap
        Reference.RUN_5K -> R.plurals.comparison_run_5k
        Reference.HALF_MARATHON -> R.plurals.comparison_half_marathon
        Reference.MARATHON -> R.plurals.comparison_marathon
    }
    val phrase = pluralStringResource(plural, comparison.count, comparison.count)
    // A neutral surface: under dynamic colour the tertiary tone can land close to the red warning banner.
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.dashboard_comparison_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.comparison_sentence, qualifier, phrase), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun AccuracyRow(manual: Boolean, onOpenAccuracy: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpenAccuracy)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.home_accuracy_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(if (manual) R.string.home_accuracy_manual_short else R.string.home_accuracy_auto_short),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
