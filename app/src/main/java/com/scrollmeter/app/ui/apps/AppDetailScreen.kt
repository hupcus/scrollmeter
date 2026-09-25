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
import com.scrollmeter.app.insights.ChartScale
import com.scrollmeter.app.insights.DaySeries
import com.scrollmeter.app.insights.Period
import com.scrollmeter.app.insights.ScrollShare
import com.scrollmeter.app.measurement.MeasurementQuality
import com.scrollmeter.app.settings.Settings
import com.scrollmeter.app.ui.components.AppIcon
import com.scrollmeter.app.ui.components.BarChart
import com.scrollmeter.app.ui.components.ChartLabels
import com.scrollmeter.app.ui.components.appLocale
import com.scrollmeter.app.ui.components.rememberAppInfo
import com.scrollmeter.app.ui.components.rememberCalibrationConfidence
import com.scrollmeter.app.ui.components.rememberToday
import com.scrollmeter.app.ui.components.rememberUsageGranted
import com.scrollmeter.app.ui.period.periodName
import java.util.Locale
import kotlinx.coroutines.flow.map

/**
 * One app over one period (spec §24, D19, ADR-036) — the period it was opened from, named in the
 * header: distance, its share of the period's distance, time in app, scroll time, pace, the share of
 * the time in app spent scrolling, and the quality word with its explanation; below, the last 30 days
 * of distance and of time (in app with Usage access, scrolling otherwise).
 */
@Composable
fun AppDetailScreen(graph: AppGraph, packageName: String, period: Period, onBack: () -> Unit) {
    val locale = appLocale()
    val labels = remember(locale) { ChartLabels(locale) }
    val today = rememberToday()
    val usageGranted = rememberUsageGranted(graph)
    val calibration = rememberCalibrationConfidence(graph)
    val info by rememberAppInfo(graph.appInfoProvider, packageName, 48.dp)
    val label = info?.label ?: packageName
    val settings by graph.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = Settings())
    val unit = settings.unitPreference
    val range = period.range
    val periodDays by remember(range, packageName) { graph.scrollRepository.appDays(packageName, range) }
        .collectAsStateWithLifecycle(initialValue = null)
    val totals = remember(periodDays) { periodDays?.let(AppPeriodTotals::of) }
    val periodMm by remember(range) { graph.scrollRepository.distance(range) }.collectAsStateWithLifecycle(initialValue = null)
    val chartRange = DateRange(today.minusDays(CHART_DAYS - 1L), today)
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
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val name = periodName(period, today, labels)
                val dates = labels.dates(period)
                Text(
                    if (name == dates) dates else stringResource(R.string.app_detail_period, name, dates),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        PeriodSummary(totals, AppRanking.sharePercent(totals?.distanceMm, periodMm), usageGranted, ::distance, locale)

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

        val distanceBars = remember(days, chartRange) { DaySeries.bars(chartRange, days) }
        val distanceScale = remember(distanceBars) { ChartLabels.distanceScale(distanceBars.maxOf { it.value }) }
        ChartCard(
            title = stringResource(R.string.app_detail_distance_chart),
            values = distanceBars.map { it.value },
            scale = distanceScale,
            axisLabel = remember(distanceScale, unit, locale) { ChartLabels.distanceAxis(distanceScale, unit, locale) },
            barLabel = { labels.bar(distanceBars[it], distanceBars.size) },
            firstBar = chartRange.fromKey,
            selectedText = { i -> stringResource(R.string.chart_day_value, labels.day(distanceBars[i].date), distance(distanceBars[i].value)) },
        )
        val timeBars = remember(days, chartRange, usageGranted) {
            DaySeries.bars(chartRange, days) { (if (usageGranted) it.foregroundMs else it.activeScrollMs)?.toDouble() }
        }
        ChartCard(
            title = stringResource(if (usageGranted) R.string.app_detail_time_chart else R.string.app_detail_scroll_chart),
            // Bars and axis in minutes, so the grid steps read as clock time.
            values = timeBars.map { it.value / 60_000.0 },
            scale = remember(timeBars) { ChartLabels.minutesScale(timeBars.maxOf { it.value }) },
            axisLabel = ChartLabels::minutesAxis,
            barLabel = { labels.bar(timeBars[it], timeBars.size) },
            firstBar = chartRange.fromKey,
            selectedText = { i ->
                stringResource(R.string.chart_day_value, labels.day(timeBars[i].date), TimeFormatter.duration(timeBars[i].value.toLong()))
            },
        )
    }
}

/**
 * Distance and its share of the period's distance, time in app and pace (with Usage access, ADR-022),
 * scroll time; the scroll-share sentence
 * when both times are known, or a note that the app reports no scrolling. Pace and share use only
 * the days that have a time in app ([AppPeriodTotals]).
 */
@Composable
private fun PeriodSummary(app: AppPeriodTotals?, sharePercent: Double?, usageGranted: Boolean, distance: (Double) -> String, locale: Locale) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            SummaryRow(stringResource(R.string.app_detail_distance), app?.distanceMm?.let(distance) ?: TimeFormatter.UNKNOWN)
            if (sharePercent != null) {
                HorizontalDivider()
                SummaryRow(stringResource(R.string.app_detail_share), stringResource(R.string.percent, decimal(sharePercent, locale)))
            }
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

/** The last 30 days, whatever the period: the charts show the app's trend, the summary the period. */
private const val CHART_DAYS = 30

private fun qualityLabel(quality: MeasurementQuality): Int = when (quality) {
    MeasurementQuality.HIGH -> R.string.quality_high
    MeasurementQuality.MEDIUM -> R.string.quality_medium
    MeasurementQuality.LOW -> R.string.quality_low
    MeasurementQuality.UNKNOWN -> R.string.quality_unknown
}

private fun qualityInfo(quality: MeasurementQuality): Int = when (quality) {
    MeasurementQuality.HIGH -> R.string.quality_high_info
    MeasurementQuality.MEDIUM -> R.string.quality_medium_info
    MeasurementQuality.LOW -> R.string.quality_low_info
    MeasurementQuality.UNKNOWN -> R.string.quality_unknown_info
}

/** A share with one decimal ("43,7"). */
private fun decimal(value: Double, locale: Locale): String =
    java.text.NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }.format(value)
