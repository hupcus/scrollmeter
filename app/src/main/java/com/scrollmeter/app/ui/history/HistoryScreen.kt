package com.scrollmeter.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollmeter.app.AppGraph
import com.scrollmeter.app.R
import com.scrollmeter.app.format.DistanceFormatter
import com.scrollmeter.app.insights.HistoryPeriod
import com.scrollmeter.app.insights.HistorySeriesBuilder
import com.scrollmeter.app.settings.Settings
import com.scrollmeter.app.ui.components.BarChart
import com.scrollmeter.app.ui.components.ChartLabels
import com.scrollmeter.app.ui.components.PeriodSelector
import com.scrollmeter.app.ui.components.appLocale
import com.scrollmeter.app.ui.components.rememberToday

/**
 * Historie (spec §23): distance per day for 7 or 30 days, per month for 12 months, and below it
 * Průměr / den, Nejvyšší den, Nejnižší den, Celkem — per day in every view, counted from the first
 * measured day (ADR-029). Tapping a bar shows its value.
 */
@Composable
fun HistoryScreen(graph: AppGraph) {
    val locale = appLocale()
    val labels = remember(locale) { ChartLabels(locale) }
    val today = rememberToday()
    var period by rememberSaveable { mutableStateOf(HistoryPeriod.DAYS_7) }
    var selected by rememberSaveable(period, today) { mutableStateOf<Int?>(null) }
    val settings by graph.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = Settings())
    val repository = graph.scrollRepository
    val range = HistorySeriesBuilder.range(period, today)
    val days by remember(range) { repository.days(range) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val first by remember { repository.firstMeasuredDay() }.collectAsStateWithLifecycle(initialValue = null)
    val series = remember(period, today, days, first) { HistorySeriesBuilder.build(period, today, days, first) }
    val unit = settings.unitPreference

    fun distance(mm: Double) = DistanceFormatter.format(mm, unit, locale)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleLarge)
        PeriodSelector(
            options = HistoryPeriod.entries,
            selected = period,
            label = { stringResource(periodLabel(it)) },
            onSelect = { period = it },
        )
        val scale = remember(series) { ChartLabels.distanceScale(series.bars.maxOfOrNull { it.value } ?: 0.0) }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val bar = selected?.let(series.bars::getOrNull)
                Text(
                    text = if (bar != null) {
                        stringResource(R.string.history_day_value, labels.selected(period, bar), distance(bar.value))
                    } else {
                        stringResource(R.string.history_day_value, stringResource(R.string.history_total), distance(series.bars.sumOf { it.value }))
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                BarChart(
                    values = series.bars.map { it.value },
                    scale = scale,
                    axisLabel = remember(scale, unit, locale) { ChartLabels.distanceAxis(scale, unit, locale) },
                    barLabel = { labels.bar(period, series.bars[it]) },
                    contentDescription = stringResource(
                        R.string.history_chart_description,
                        distance(series.bars.sumOf { it.value }),
                        series.stats?.let { distance(it.maxDay.value) } ?: "—",
                    ),
                    selected = selected,
                    onSelect = { selected = it },
                )
            }
        }
        val stats = series.stats
        if (stats == null) {
            Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    StatRow(stringResource(R.string.history_average), distance(stats.averagePerDay))
                    HorizontalDivider()
                    StatRow(stringResource(R.string.history_max), distance(stats.maxDay.value), labels.day(stats.maxDay.date))
                    HorizontalDivider()
                    StatRow(stringResource(R.string.history_min), distance(stats.minDay.value), labels.day(stats.minDay.date))
                    HorizontalDivider()
                    StatRow(stringResource(R.string.history_total), distance(stats.total))
                }
            }
        }
    }
}

fun periodLabel(period: HistoryPeriod): Int = when (period) {
    HistoryPeriod.DAYS_7 -> R.string.period_7_days
    HistoryPeriod.DAYS_30 -> R.string.period_30_days
    HistoryPeriod.MONTHS_12 -> R.string.period_12_months
}

@Composable
private fun StatRow(label: String, value: String, date: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (date != null) Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
