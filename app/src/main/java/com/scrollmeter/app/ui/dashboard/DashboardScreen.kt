package com.scrollmeter.app.ui.dashboard

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollmeter.app.AppGraph
import com.scrollmeter.app.R
import com.scrollmeter.app.calibration.CalibrationMethod
import com.scrollmeter.app.data.model.AppSummary
import com.scrollmeter.app.format.DistanceFormatter
import com.scrollmeter.app.format.TimeFormatter
import com.scrollmeter.app.insights.AppRanking
import com.scrollmeter.app.insights.DailyLimit
import com.scrollmeter.app.insights.DistanceComparison
import com.scrollmeter.app.insights.DistanceComparisonProvider
import com.scrollmeter.app.insights.LimitLevel
import com.scrollmeter.app.insights.Period
import com.scrollmeter.app.insights.PeriodKind
import com.scrollmeter.app.insights.Qualifier
import com.scrollmeter.app.insights.Reference
import com.scrollmeter.app.measurement.PhysicalScaleProvider
import com.scrollmeter.app.settings.Settings
import com.scrollmeter.app.ui.components.AppRow
import com.scrollmeter.app.ui.components.LimitCard
import com.scrollmeter.app.ui.components.ServiceStatus
import com.scrollmeter.app.ui.components.appLocale
import com.scrollmeter.app.ui.components.averageSentence
import com.scrollmeter.app.ui.components.launchWrite
import com.scrollmeter.app.ui.components.limitSentence
import com.scrollmeter.app.ui.components.rememberServiceStatus
import com.scrollmeter.app.ui.components.rememberToday
import com.scrollmeter.app.ui.components.rememberUsageGranted
import com.scrollmeter.app.ui.theme.limitColors
import java.time.LocalDate

/**
 * Přehled (spec §21, ADR-036): today's distance against the daily limit — the card turns green, orange,
 * red as the limit comes closer — this week and this month with their average per day, the apps
 * scrolled most today, and at most one comparison. Everything reads live from [AppGraph.scrollRepository]
 * (stored + not yet flushed). When the service is not running, a banner says so first (§32). Time in
 * app (D19) appears once Usage access is granted; until then one card offers it and can be dismissed
 * for good. Today, the week and the month open Statistiky; an app opens its detail for today;
 * Nastavení sits behind the gear — there is no bottom bar.
 */
@Composable
fun DashboardScreen(
    graph: AppGraph,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onOpenAccuracy: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPeriod: (Period) -> Unit,
    onOpenApp: (String, Period) -> Unit,
) {
    val locale = appLocale()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val status = rememberServiceStatus(graph)
    val usageGranted = rememberUsageGranted(graph)
    // The screen may stay open across midnight: "Dnes" rolls over without waiting for a resume.
    val today = rememberToday()
    val day = Period(PeriodKind.DAY, today)
    val week = Period(PeriodKind.WEEK, today)
    val month = Period(PeriodKind.MONTH, today)

    val repository = graph.scrollRepository
    // Null until loaded: a card the user dismissed must not flash up, nor the default limit's colour.
    val loadedSettings by graph.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    val settings = loadedSettings ?: Settings()
    val limitMm = loadedSettings?.dailyLimitMm ?: Settings.NO_LIMIT
    val todayMm by remember(today) { repository.distance(day.range) }.collectAsStateWithLifecycle(initialValue = null)
    val weekMm by remember(today) { repository.distance(week.range) }.collectAsStateWithLifecycle(initialValue = null)
    val monthMm by remember(today) { repository.distance(month.range) }.collectAsStateWithLifecycle(initialValue = null)
    val first by remember { repository.firstMeasuredDay() }.collectAsStateWithLifecycle(initialValue = null)
    val apps by remember(today) { repository.apps(day.range) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val calibration by graph.calibrationRepository.state.collectAsStateWithLifecycle(initialValue = null)
    val display = remember(LocalConfiguration.current.orientation) { graph.displayMetricsProvider.read() }

    fun distance(mm: Double): String = DistanceFormatter.format(mm, settings.unitPreference, locale)

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (status == ServiceStatus.ON) MeasuringChip()
                IconButton(onClick = onOpenSettings) {
                    Icon(painterResource(R.drawable.ic_nav_settings), contentDescription = stringResource(R.string.settings_title))
                }
            }
            if (status != ServiceStatus.ON) ServiceBanner(status, onOpenAccessibilitySettings)

            val todayStatus = DailyLimit.status(todayMm, limitMm)
            val timeToday = if (usageGranted) apps.sumOf { it.foregroundMs ?: 0L }.takeIf { it > 0 } else null
            LimitCard(
                title = stringResource(R.string.home_today_title),
                value = todayMm?.let(::distance) ?: TimeFormatter.UNKNOWN,
                level = todayStatus.level,
                lines = listOfNotNull(
                    limitSentence(todayStatus, ::distance),
                    timeToday?.let { stringResource(R.string.period_time_in_apps, TimeFormatter.duration(it)) },
                ),
                onClick = { onOpenPeriod(day) },
            )
            Card(Modifier.fillMaxWidth()) {
                PeriodRow(week, weekMm, first, today, limitMm, ::distance, onOpenPeriod)
                HorizontalDivider()
                PeriodRow(month, monthMm, first, today, limitMm, ::distance, onOpenPeriod)
            }
            MostTodayCard(graph, AppRanking.list(apps), usageGranted, ::distance, onOpenAll = { onOpenPeriod(day) }, onOpenApp = { onOpenApp(it, day) })
            if (!usageGranted && loadedSettings?.usageTimeCardDismissed == false) {
                UsageTimeCard(
                    onShow = onOpenUsageAccess,
                    onDismiss = { scope.launchWrite(context) { graph.settingsRepository.setUsageTimeCardDismissed(true) } },
                )
            }
            if (settings.showComparisons) todayMm?.let(DistanceComparisonProvider::compare)?.let { ComparisonCard(it) }
            calibration?.let { AccuracyRow(PhysicalScaleProvider.resolve(it, display).method == CalibrationMethod.MANUAL_CARD, onOpenAccuracy) }
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

/**
 * This week / this month: its total, the average per day against the daily limit, and a dot in the
 * limit colour of that average (ADR-036). The text carries the same, so the dot is never the only signal.
 */
@Composable
private fun PeriodRow(
    period: Period,
    totalMm: Double?,
    first: LocalDate?,
    today: LocalDate,
    limitMm: Double,
    distance: (Double) -> String,
    onOpen: (Period) -> Unit,
) {
    val average = DailyLimit.averagePerDay(totalMm, period.range, first, today)
    val level = if (average == null) LimitLevel.NONE else DailyLimit.level(average, limitMm)
    Row(Modifier.fillMaxWidth().clickable { onOpen(period) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (level != LimitLevel.NONE) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(limitColors(level).bar))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(if (period.kind == PeriodKind.WEEK) R.string.period_this_week else R.string.period_this_month), style = MaterialTheme.typography.bodyLarge)
            averageSentence(average, limitMm, distance)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(totalMm?.let(distance) ?: TimeFormatter.UNKNOWN, style = MaterialTheme.typography.titleMedium)
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The three apps scrolled most today, and the way to all of them. */
@Composable
private fun MostTodayCard(
    graph: AppGraph,
    apps: List<AppSummary>,
    usageGranted: Boolean,
    distance: (Double) -> String,
    onOpenAll: () -> Unit,
    onOpenApp: (String) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(stringResource(R.string.dashboard_most_today), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
            if (apps.isEmpty()) {
                Text(stringResource(R.string.dashboard_no_scroll_yet), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            apps.take(TOP_APPS).forEach { app -> AppRow(graph, app, usageGranted, distance) { onOpenApp(app.packageName) } }
            if (apps.size > TOP_APPS) {
                TextButton(onClick = onOpenAll, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.dashboard_all_apps)) }
            }
        }
    }
}

private const val TOP_APPS = 3

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
