package com.scrollmeter.app.devtools

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollmeter.app.AppGraph
import com.scrollmeter.app.R
import com.scrollmeter.app.measurement.MeasurementResult
import com.scrollmeter.app.measurement.MeasurementSource
import com.scrollmeter.app.measurement.ScrollDistanceCalculator
import com.scrollmeter.app.ui.components.Format
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val rowTime = DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.ROOT)

/** Spec §34: live list of the newest events with Clear / Pause / Export. Debug builds only. */
@Composable
fun DebugMeasurementScreen(graph: AppGraph, log: DebugEventLog, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val events by log.visible.collectAsStateWithLifecycle()
    val counts by log.counts.collectAsStateWithLifecycle()
    val paused by log.paused.collectAsStateWithLifecycle()
    val connected by graph.monitor.serviceConnected.collectAsStateWithLifecycle()
    // Re-read after rotation, like the service does in onConfigurationChanged.
    val display = remember(LocalConfiguration.current.orientation) { graph.displayMetricsProvider.read() }
    val scale = remember(display) { display.toDisplayScale() }
    val savedLabel = stringResource(R.string.devtools_debug_exported)
    val shareLabel = stringResource(R.string.devtools_debug_share)

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.devtools_back)) }
                Text(stringResource(R.string.devtools_debug_title), style = MaterialTheme.typography.titleLarge)
            }
            val s = scale.scale
            val mm1200 = ScrollDistanceCalculator.distance(0, 1200, s).totalMm
            val small = MaterialTheme.typography.bodySmall
            Text(
                "Displej ${display.widthPx} × ${display.heightPx} px · xdpi ${Format.decimal(display.xdpi, 3)} · " +
                    "ydpi ${Format.decimal(display.ydpi, 3)} · densityDpi ${display.densityDpi}",
                style = small,
            )
            Text(
                "mm/px X ${Format.decimal(s.mmPerPxX, 6)} · Y ${Format.decimal(s.mmPerPxY, 6)} (${s.method}, ${s.confidence})",
                style = small,
            )
            Text(
                "1200 px svisle = ${Format.decimal(mm1200, 1)} mm · limit outlieru " +
                    "${Format.decimal(scale.geometry.maxEventDistancePx, 0)} px",
                style = small,
            )
            Text(
                stringResource(if (connected) R.string.devtools_service_running else R.string.devtools_service_stopped) +
                    " · zahozeno ${graph.monitor.droppedSamples.get()} · chyby ${graph.monitor.processingFailures.get()}",
                style = small,
            )
            val by = counts.bySource
            Text(
                "Událostí ${counts.total} · přímé ${by[MeasurementSource.DIRECT_DELTA] ?: 0} · fallback " +
                    "${by[MeasurementSource.FALLBACK_POSITION] ?: 0} · duplicitní ${by[MeasurementSource.SUPERSEDED_BY_DIRECT] ?: 0} · bez dat ${by[MeasurementSource.UNMEASURABLE] ?: 0} · " +
                    "outlier ${by[MeasurementSource.OUTLIER_REJECTED] ?: 0} · vyloučené ${by[MeasurementSource.EXCLUDED] ?: 0} · " +
                    "v paměti ${counts.recorded}" + if (counts.overflowed > 0) " (přeteklo ${counts.overflowed})" else "",
                style = small,
            )
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { log.clear() }) { Text(stringResource(R.string.devtools_debug_clear)) }
                OutlinedButton(onClick = { log.setPaused(!paused) }) {
                    Text(stringResource(if (paused) R.string.devtools_debug_resume else R.string.devtools_debug_pause))
                }
                Button(onClick = {
                    scope.launch {
                        val file = withContext(Dispatchers.IO) { DebugExport.write(context, graph, log) }
                        val action = snackbar.showSnackbar("$savedLabel ${file.name}", actionLabel = shareLabel)
                        if (action == SnackbarResult.ActionPerformed) {
                            try {
                                context.startActivity(DebugExport.shareIntent(context, file))
                            } catch (e: ActivityNotFoundException) {
                                snackbar.showSnackbar(file.absolutePath)
                            }
                        }
                    }
                }) { Text(stringResource(R.string.devtools_debug_export)) }
            }
            HorizontalDivider()
            EventRowHeader()
            LazyColumn(Modifier.fillMaxSize()) {
                items(events) { EventRow(it) }
            }
        }
    }
}

@Composable
private fun EventRowHeader() {
    Cells("čas", "aplikace", "dx", "dy", "mm", "zdroj", "ok")
}

@Composable
private fun EventRow(result: MeasurementResult) {
    val s = result.sample
    Cells(
        rowTime.format(Instant.ofEpochMilli(s.wallTimeMs).atZone(ZoneId.systemDefault())),
        s.packageName ?: "—",
        // The pixels behind the mm column (direct or fallback), not the raw -1/-1 of position-only apps.
        result.dxPx.toString(),
        result.dyPx.toString(),
        Format.decimal(result.distance.totalMm, 2),
        result.source.shortCode(),
        if (result.accepted) "✓" else "✗",
    )
}

@Composable
private fun Cells(time: String, pkg: String, dx: String, dy: String, mm: String, source: String, ok: String) {
    val style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(time, style = style, modifier = Modifier.width(78.dp), maxLines = 1)
        Text(pkg, style = style, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.StartEllipsis)
        Text(dx, style = style, modifier = Modifier.width(40.dp), maxLines = 1)
        Text(dy, style = style, modifier = Modifier.width(40.dp), maxLines = 1)
        Text(mm, style = style, modifier = Modifier.width(44.dp), maxLines = 1)
        Text(source, style = style, modifier = Modifier.width(34.dp), maxLines = 1)
        Text(ok, style = style, modifier = Modifier.width(16.dp), maxLines = 1)
    }
}

private fun MeasurementSource.shortCode(): String = when (this) {
    MeasurementSource.DIRECT_DELTA -> "DIR"
    MeasurementSource.FALLBACK_POSITION -> "FB"
    MeasurementSource.SUPERSEDED_BY_DIRECT -> "DUP"
    MeasurementSource.UNMEASURABLE -> "N/A"
    MeasurementSource.OUTLIER_REJECTED -> "OUT"
    MeasurementSource.EXCLUDED -> "EXC"
}
