package com.scrollmeter.app.ui.period

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollmeter.app.AppGraph
import com.scrollmeter.app.R
import com.scrollmeter.app.format.DistanceFormatter
import com.scrollmeter.app.format.TimeFormatter
import com.scrollmeter.app.insights.AppRanking
import com.scrollmeter.app.insights.DailyLimit
import com.scrollmeter.app.insights.DaySeries
import com.scrollmeter.app.insights.LimitLevel
import com.scrollmeter.app.insights.Period
import com.scrollmeter.app.insights.PeriodKind
import com.scrollmeter.app.insights.PeriodRelation
import com.scrollmeter.app.ui.components.AppRow
import com.scrollmeter.app.ui.components.BarChart
import com.scrollmeter.app.ui.components.ChartLabels
import com.scrollmeter.app.ui.components.LimitCard
import com.scrollmeter.app.ui.components.PeriodSelector
import com.scrollmeter.app.ui.components.ScreenHeader
import com.scrollmeter.app.ui.components.appLocale
import com.scrollmeter.app.ui.components.averageSentence
import com.scrollmeter.app.ui.components.limitSentence
import com.scrollmeter.app.ui.components.rememberToday
import com.scrollmeter.app.ui.components.rememberUsageGranted
import com.scrollmeter.app.ui.theme.limitColors
import java.time.LocalDate
import kotlin.math.max

/**
 * Statistiky (ADR-036) — replaces Historie and Aplikace: one calendar day, week or month (Den / Týden /
 * Měsíc, ‹ › to the one before and after, never past today or before the first measured day) with its
 * distance against the daily limit; for a week or month a bar per day in its own limit colour with the
 * limit as a dashed line; then the apps, the longest distance first, each with its time in app. A bar
 * opens that day, an app its detail for this period.
 */
@Composable
fun PeriodScreen(graph: AppGraph, initial: Period, onBack: () -> Unit, onOpenDay: (Period) -> Unit, onOpenApp: (String, Period) -> Unit) {
    val locale = appLocale()
    val labels = remember(locale) { ChartLabels(locale) }
    val today = rememberToday()
    val usageGranted = rememberUsageGranted(graph)
    var key by rememberSaveable { mutableStateOf(initial.key) }
    val period = Period.fromKey(key) ?: initial
    val range = period.range
    val repository = graph.scrollRepository
    // Null until loaded: the colours must not show the default limit to someone who switched it off.
    val settings by graph.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    val total by remember(range) { repository.distance(range) }.collectAsStateWithLifecycle(initialValue = null)
    val apps by remember(range) { repository.apps(range) }.collectAsStateWithLifecycle(initialValue = null)
    val days by remember(range) { repository.days(range) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val first by remember { repository.firstMeasuredDay() }.collectAsStateWithLifecycle(initialValue = null)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(stringResource(R.string.period_title), onBack)
        PeriodSelector(PeriodKind.entries, period.kind, { stringResource(kindLabel(it)) }, { key = period.withKind(it).key })
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { key = period.previous().key }, enabled = period.canGoBack(first)) {
                Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = stringResource(R.string.period_previous))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(periodName(period, today, labels), style = MaterialTheme.typography.titleMedium)
                if (period.relation(today) != PeriodRelation.OTHER) {
                    Text(labels.dates(period), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = { key = period.next().key }, enabled = period.canGoForward(today)) {
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = stringResource(R.string.period_next))
            }
        }

        val current = settings ?: return@Column
        val unit = current.unitPreference
        val limitMm = current.dailyLimitMm
        fun distance(mm: Double) = DistanceFormatter.format(mm, unit, locale)
        val timeInApps = if (usageGranted) apps?.sumOf { it.foregroundMs ?: 0L }?.takeIf { it > 0 } else null
        val timeLine = timeInApps?.let { stringResource(R.string.period_time_in_apps, TimeFormatter.duration(it)) }

        if (period.kind == PeriodKind.DAY) {
            val status = DailyLimit.status(total, limitMm)
            LimitCard(null, total?.let(::distance) ?: TimeFormatter.UNKNOWN, status.level, listOfNotNull(total?.let { limitSentence(status, ::distance) }, timeLine))
        } else {
            val average = DailyLimit.averagePerDay(total, range, first, today)
            val level = if (average == null) LimitLevel.NONE else DailyLimit.level(average, limitMm)
            LimitCard(null, total?.let(::distance) ?: TimeFormatter.UNKNOWN, level, listOfNotNull(averageSentence(average, limitMm, ::distance), timeLine))
        }

        val list = apps?.let(AppRanking::list) ?: return@Column
        if (list.isEmpty()) {
            Text(stringResource(R.string.period_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }

        if (period.kind != PeriodKind.DAY) {
            val bars = remember(range, days) { DaySeries.bars(range, days) }
            val maxBar = bars.maxOfOrNull { it.value } ?: 0.0
            // The limit line only when it sits within reach of the bars — far above, it would squash them flat.
            val showLimit = limitMm > 0.0 && limitMm <= 2 * maxBar
            val scale = remember(maxBar, showLimit, limitMm) { ChartLabels.distanceScale(if (showLimit) max(maxBar, limitMm) else maxBar) }
            val palette = LimitLevel.entries.associateWith { limitColors(it).bar }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.period_chart_title), style = MaterialTheme.typography.titleSmall)
                    BarChart(
                        values = bars.map { it.value },
                        scale = scale,
                        axisLabel = remember(scale, unit, locale) { ChartLabels.distanceAxis(scale, unit, locale) },
                        barLabel = { labels.bar(bars[it], bars.size) },
                        contentDescription = stringResource(R.string.period_chart_description, labels.dates(period)),
                        selected = null,
                        onSelect = { index ->
                            val date = index?.let(bars::getOrNull)?.date
                            if (date != null && !date.isAfter(today)) onOpenDay(Period(PeriodKind.DAY, date))
                        },
                        barColor = { palette.getValue(DailyLimit.level(bars[it].value, limitMm)) },
                        referenceLine = limitMm.takeIf { showLimit },
                    )
                    Text(stringResource(R.string.period_chart_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(stringResource(R.string.period_apps), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp))
                list.forEach { app -> AppRow(graph, app, usageGranted, ::distance) { onOpenApp(app.packageName, period) } }
            }
        }
    }
}

/** "Dnes", "Minulý týden", or for anything further back its dates. */
@Composable
fun periodName(period: Period, today: LocalDate, labels: ChartLabels): String = when (period.relation(today)) {
    PeriodRelation.CURRENT -> stringResource(
        when (period.kind) {
            PeriodKind.DAY -> R.string.period_today
            PeriodKind.WEEK -> R.string.period_this_week
            PeriodKind.MONTH -> R.string.period_this_month
        },
    )
    PeriodRelation.PREVIOUS -> stringResource(
        when (period.kind) {
            PeriodKind.DAY -> R.string.period_yesterday
            PeriodKind.WEEK -> R.string.period_last_week
            PeriodKind.MONTH -> R.string.period_last_month
        },
    )
    PeriodRelation.OTHER -> labels.dates(period)
}

private fun kindLabel(kind: PeriodKind): Int = when (kind) {
    PeriodKind.DAY -> R.string.period_day
    PeriodKind.WEEK -> R.string.period_week
    PeriodKind.MONTH -> R.string.period_month
}
