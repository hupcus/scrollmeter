package com.scrollmeter.app.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollmeter.app.AppGraph
import com.scrollmeter.app.R
import com.scrollmeter.app.data.model.DateRange
import com.scrollmeter.app.format.DistanceFormatter
import com.scrollmeter.app.format.TimeFormatter
import com.scrollmeter.app.insights.AppPeriodTotals
import com.scrollmeter.app.insights.AppRanking
import com.scrollmeter.app.insights.AppsPeriod
import com.scrollmeter.app.insights.ChartScale
import com.scrollmeter.app.insights.HistoryPeriod
import com.scrollmeter.app.insights.HistorySeriesBuilder
import com.scrollmeter.app.insights.ScrollShare
import com.scrollmeter.app.settings.Settings
import com.scrollmeter.app.ui.components.AppIcon
import com.scrollmeter.app.ui.components.BarChart
import com.scrollmeter.app.ui.components.ChartLabels
import com.scrollmeter.app.ui.components.PeriodSelector
import com.scrollmeter.app.ui.components.appLocale
import com.scrollmeter.app.ui.components.rememberAppInfo
import com.scrollmeter.app.ui.components.rememberCalibrationConfidence
import com.scrollmeter.app.ui.components.rememberToday
import com.scrollmeter.app.ui.components.rememberUsageGranted
import java.util.Locale
import kotlinx.coroutines.flow.map

/**
 * One app (spec §24, D19): Dnes | 7 dní | 30 dní | Celkem with distance, time in app, scroll time,
 * the share of the time in app spent scrolling and the quality word with its explanation; below,
 * the last 30 days of distance and of time (in app with Usage access, scrolling otherwise).
 */
@Composable
fun AppDetailScreen(graph: AppGraph, packageName: String, onBack: () -> Unit) {
    val locale = appLocale()
    val labels = remember(locale) { ChartLabels(locale) }
    val today = rememberToday()
    val usageGranted = rememberUsageGranted(graph)
    val calibration = rememberCalibrationConfidence(graph)
    val info by rememberAppInfo(graph.appInfoProvider, packageName, 48.dp)
    val label = info?.label ?: packageName
    var period by rememberSaveable { mutableStateOf(AppsPeriod.TODAY) }
    val settings by graph.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = Settings())
    val unit = settings.unitPreference
    val range = period.range(today)
    val periodDays by remember(range, packageName) { graph.scrollRepository.appDays(packageName, range) }
        .collectAsStateWithLifecycle(initialValue = null)
    val totals = remember(periodDays) { periodDays?.let(AppPeriodTotals::of) }
    val chartRange = HistorySeriesBuilder.range(HistoryPeriod.DAYS_30, today)
    val days by remember(chartRange, packageName) { graph.scrollRepository.appDays(packageName, chartRange) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    // Quality describes the app, not the period: rated on its all-time counters (ADR-029).
    val allTime by remember(packageName) {
        graph.scrollRepository.apps(DateRange.ALL).map { apps -> apps.firstOrNull { it.packageName == packageName } }
    }.collectAsStateWithLifecycle(initialValue = null)

    fun distance(mm: Double) = DistanceFormatter.format(mm, unit, locale)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back)) }
            Spacer(Modifier.width(4.dp))
            AppIcon(info, label, 40.dp)
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        PeriodSelector(AppsPeriod.entries, period, { stringResource(appsPeriodLabel(it)) }, { period = it })
        PeriodSummary(totals, usageGranted, ::distance, locale)

        val basis = allTime
        if (basis?.distanceMm != null) {
            val quality = AppRanking.quality(basis, calibration)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(qualityLabel(quality)), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(qualityInfo(quality)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Charts only: no statistics, so no first measured day.
        val distanceSeries = remember(days, today) { HistorySeriesBuilder.build(HistoryPeriod.DAYS_30, today, days, firstMeasuredDay = null) }
        val distanceScale = remember(distanceSeries) { ChartLabels.distanceScale(distanceSeries.bars.maxOf { it.value }) }
        ChartCard(
            title = stringResource(R.string.app_detail_distance_chart),
            values = distanceSeries.bars.map { it.value },
            scale = distanceScale,
            axisLabel = remember(distanceScale, unit, locale) { ChartLabels.distanceAxis(distanceScale, unit, locale) },
            barLabel = { labels.bar(HistoryPeriod.DAYS_30, distanceSeries.bars[it]) },
            firstBar = distanceSeries.range.from.toString(),
            selectedText = { i -> stringResource(R.string.history_day_value, labels.day(distanceSeries.bars[i].start), distance(distanceSeries.bars[i].value)) },
        )
        val timeSeries = remember(days, today, usageGranted) {
            HistorySeriesBuilder.build(HistoryPeriod.DAYS_30, today, days, firstMeasuredDay = null) {
                (if (usageGranted) it.foregroundMs else it.activeScrollMs)?.toDouble()
            }
        }
        ChartCard(
            title = stringResource(if (usageGranted) R.string.app_detail_time_chart else R.string.app_detail_scroll_chart),
            // Bars and axis in minutes, so the grid steps read as clock time.
            values = timeSeries.bars.map { it.value / 60_000.0 },
            scale = remember(timeSeries) { ChartLabels.minutesScale(timeSeries.bars.maxOf { it.value }) },
            axisLabel = ChartLabels::minutesAxis,
            barLabel = { labels.bar(HistoryPeriod.DAYS_30, timeSeries.bars[it]) },
            firstBar = timeSeries.range.from.toString(),
            selectedText = { i ->
                stringResource(R.string.history_day_value, labels.day(timeSeries.bars[i].start), TimeFormatter.duration(timeSeries.bars[i].value.toLong()))
            },
        )
    }
}

/**
 * Distance, time in app and pace (with Usage access, ADR-022), scroll time; the scroll-share sentence
 * when both times are known, or a note that the app reports no scrolling. Pace and share use only
 * the days that have a time in app ([AppPeriodTotals]).
 */
@Composable
private fun PeriodSummary(app: AppPeriodTotals?, usageGranted: Boolean, distance: (Double) -> String, locale: Locale) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            SummaryRow(stringResource(R.string.app_detail_distance), app?.distanceMm?.let(distance) ?: TimeFormatter.UNKNOWN)
            if (usageGranted) {
                HorizontalDivider()
                SummaryRow(stringResource(R.string.app_detail_time_in_app), TimeFormatter.duration(app?.foregroundMs))
            }
            HorizontalDivider()
            SummaryRow(stringResource(R.string.app_detail_scroll_time), TimeFormatter.duration(app?.activeScrollMs))
            if (usageGranted) {
                HorizontalDivider()
                SummaryRow(stringResource(R.string.app_detail_pace), TimeFormatter.pace(app?.pairedDistanceMm, app?.foregroundMs, locale))
            }
            val share = if (usageGranted) ScrollShare.percent(app?.pairedScrollMs, app?.foregroundMs) else null
            val note = when {
                app != null && app.distanceMm == null && app.foregroundMs != null -> stringResource(R.string.app_detail_no_scroll)
                share != null -> stringResource(
                    R.string.app_detail_scroll_share,
                    TimeFormatter.duration(app?.pairedScrollMs),
                    TimeFormatter.duration(app?.foregroundMs),
                    share.toString(),
                )
                else -> null
            }
            if (note != null) {
                HorizontalDivider()
                Text(note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ChartCard(
    title: String,
    values: List<Double>,
    scale: ChartScale,
    axisLabel: (Double) -> String,
    barLabel: (Int) -> String,
    firstBar: String,
    selectedText: @Composable (Int) -> String,
) {
    // Keyed by the first bar's date: after midnight the same index is another day.
    var selected by rememberSaveable(firstBar) { mutableStateOf<Int?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(selected?.takeIf { it in values.indices }?.let { selectedText(it) } ?: title, style = MaterialTheme.typography.titleSmall)
            BarChart(values, scale, axisLabel, barLabel, contentDescription = title, selected = selected, onSelect = { selected = it }, height = 160.dp)
        }
    }
}
