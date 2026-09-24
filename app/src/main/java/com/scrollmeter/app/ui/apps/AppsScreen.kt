package com.scrollmeter.app.ui.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollmeter.app.AppGraph
import com.scrollmeter.app.R
import com.scrollmeter.app.data.model.DateRange
import com.scrollmeter.app.format.DistanceFormatter
import com.scrollmeter.app.format.TimeFormatter
import com.scrollmeter.app.insights.AppRanking
import com.scrollmeter.app.insights.AppSort
import com.scrollmeter.app.insights.AppsPeriod
import com.scrollmeter.app.insights.RankedApp
import com.scrollmeter.app.measurement.MeasurementQuality
import com.scrollmeter.app.settings.Settings
import com.scrollmeter.app.settings.UnitPreference
import com.scrollmeter.app.ui.components.AppIcon
import com.scrollmeter.app.ui.components.PeriodSelector
import com.scrollmeter.app.ui.components.appLocale
import com.scrollmeter.app.ui.components.rememberAppInfo
import com.scrollmeter.app.ui.components.rememberCalibrationConfidence
import com.scrollmeter.app.ui.components.rememberToday
import com.scrollmeter.app.ui.components.rememberUsageGranted
import java.util.Locale

/**
 * Aplikace (spec §24, D19): the apps of Dnes | 7 dní | 30 dní | Celkem, by distance or by time, with
 * their share of the distance and a quality word (ADR-029) — never a made-up accuracy percentage.
 * An app with time but no scroll data (YouTube) shows "—" and says so. A row opens the detail.
 */
@Composable
fun AppsScreen(graph: AppGraph, onOpenApp: (String) -> Unit) {
    val locale = appLocale()
    val today = rememberToday()
    val usageGranted = rememberUsageGranted(graph)
    val calibration = rememberCalibrationConfidence(graph)
    var period by rememberSaveable { mutableStateOf(AppsPeriod.TODAY) }
    var sort by rememberSaveable { mutableStateOf(AppSort.DISTANCE) }
    val settings by graph.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = Settings())
    val range = period.range(today)
    val apps by remember(range) { graph.scrollRepository.apps(range) }.collectAsStateWithLifecycle(initialValue = null)
    // Quality describes the app, not the period: rated on all-time counters (ADR-029).
    val allTime by remember { graph.scrollRepository.apps(DateRange.ALL) }.collectAsStateWithLifecycle(initialValue = null)
    // Ranked only once both are read, so a row never flashes the period's own quality first.
    val ranked = remember(apps, allTime, sort, usageGranted, calibration) {
        val period = apps ?: return@remember null
        val basis = allTime ?: return@remember null
        AppRanking.rank(period, sort, usageGranted, calibration, basis.associateBy { it.packageName })
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.apps_title), style = MaterialTheme.typography.titleLarge)
                PeriodSelector(AppsPeriod.entries, period, { stringResource(appsPeriodLabel(it)) }, { period = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppSort.entries.forEach { option ->
                        FilterChip(
                            selected = sort == option,
                            onClick = { sort = option },
                            label = { Text(stringResource(if (option == AppSort.DISTANCE) R.string.apps_sort_distance else R.string.apps_sort_time)) },
                        )
                    }
                }
            }
        }
        if (ranked?.isEmpty() == true) {
            item { Text(stringResource(R.string.apps_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(ranked.orEmpty(), key = { it.app.packageName }) { row ->
            AppListRow(graph, row, usageGranted, settings.unitPreference, locale) { onOpenApp(row.app.packageName) }
        }
    }
}

@Composable
private fun AppListRow(graph: AppGraph, row: RankedApp, usageGranted: Boolean, unit: UnitPreference, locale: Locale, onClick: () -> Unit) {
    val app = row.app
    val info by rememberAppInfo(graph.appInfoProvider, app.packageName, 36.dp)
    val label = info?.label ?: app.packageName
    val time = if (usageGranted) app.foregroundMs else app.activeScrollMs
    val detail = when {
        row.quality == null -> stringResource(R.string.apps_no_scroll_data)
        row.sharePercent != null -> stringResource(R.string.apps_share_quality, decimal(row.sharePercent, locale), stringResource(qualityLabel(row.quality)))
        else -> stringResource(qualityLabel(row.quality))
    }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(info, label)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(app.distanceMm?.let { DistanceFormatter.format(it, unit, locale) } ?: TimeFormatter.UNKNOWN, style = MaterialTheme.typography.titleMedium)
            Text(TimeFormatter.duration(time), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun appsPeriodLabel(period: AppsPeriod): Int = when (period) {
    AppsPeriod.TODAY -> R.string.period_today
    AppsPeriod.DAYS_7 -> R.string.period_7_days
    AppsPeriod.DAYS_30 -> R.string.period_30_days
    AppsPeriod.LIFETIME -> R.string.period_lifetime
}

fun qualityLabel(quality: MeasurementQuality): Int = when (quality) {
    MeasurementQuality.HIGH -> R.string.quality_high
    MeasurementQuality.MEDIUM -> R.string.quality_medium
    MeasurementQuality.LOW -> R.string.quality_low
    MeasurementQuality.UNKNOWN -> R.string.quality_unknown
}

fun qualityInfo(quality: MeasurementQuality): Int = when (quality) {
    MeasurementQuality.HIGH -> R.string.quality_high_info
    MeasurementQuality.MEDIUM -> R.string.quality_medium_info
    MeasurementQuality.LOW -> R.string.quality_low_info
    MeasurementQuality.UNKNOWN -> R.string.quality_unknown_info
}

/** A share with one decimal ("43,7"). */
internal fun decimal(value: Double, locale: Locale): String =
    java.text.NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }.format(value)
