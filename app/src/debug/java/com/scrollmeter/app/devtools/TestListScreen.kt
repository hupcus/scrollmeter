package com.scrollmeter.app.devtools

import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollmeter.app.AppGraph
import com.scrollmeter.app.R
import com.scrollmeter.app.ui.components.Format
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce

/**
 * The three ways an app can report scrolling, each with an exact ground truth:
 * - [LAZY]: Compose LazyColumn/LazyRow — no deltas, index-based position estimate (ADR-019);
 * - [COLUMN]: Compose verticalScroll/horizontalScroll — no deltas, exact position (fallback path);
 * - [VIEW]: classic ScrollView/HorizontalScrollView — real `scrollDeltaX/Y` (direct path).
 */
private enum class Surface(val label: String) { LAZY("Lazy"), COLUMN("Column"), VIEW("View") }

/**
 * Spec §35 Measurement Test: our own scrollables with an exact ground truth next to what the
 * accessibility pipeline measured for our package. Ground truth = Σ|consumed| seen by a
 * [NestedScrollConnection] (Compose) or Σ|Δscroll| from `OnScrollChangeListener` (View) — drag
 * and fling alike. While this screen is open our own package is measured (test mode).
 */
@OptIn(FlowPreview::class)
@Composable
fun TestListScreen(graph: AppGraph, onBack: () -> Unit) {
    DisposableEffect(Unit) {
        graph.measurementSettings.enterTestMode()
        onDispose { graph.measurementSettings.exitTestMode() }
    }
    var surface by rememberSaveable { mutableStateOf(Surface.LAZY) }
    var groundTruthXPx by remember { mutableDoubleStateOf(0.0) }
    var groundTruthYPx by remember { mutableDoubleStateOf(0.0) }
    val engine by graph.monitor.selfTest.collectAsStateWithLifecycle()
    val connected by graph.monitor.serviceConnected.collectAsStateWithLifecycle()
    val scale = remember { graph.readDisplayScale().scale }
    val reset = {
        groundTruthXPx = 0.0
        groundTruthYPx = 0.0
        graph.monitor.resetSelfTest()
    }

    // One summary line after things settle, for the adb-driven accuracy runs (tools/device_accuracy.py).
    LaunchedEffect(Unit) {
        combine(snapshotFlow { Triple(surface, groundTruthXPx, groundTruthYPx) }, graph.monitor.selfTest) { gt, eng -> gt to eng }
            .debounce(1_500)
            .collect { (gt, eng) ->
                val (current, gtX, gtY) = gt
                Log.d(
                    LOG_TAG,
                    String.format(
                        Locale.ROOT,
                        "TESTLIST surface=%s gt_x_px=%.1f gt_y_px=%.1f eng_x_px=%d eng_y_px=%d gt_mm=%.3f eng_mm=%.3f eng_events=%d",
                        current.name, gtX, gtY, eng.absDxPx, eng.absDyPx,
                        gtX * scale.mmPerPxX + gtY * scale.mmPerPxY, eng.countedMm, eng.events,
                    ),
                )
            }
    }

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.devtools_back)) }
                Text(stringResource(R.string.devtools_testlist_title), style = MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.padding(horizontal = 12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface.entries.forEach { option ->
                        FilterChip(
                            selected = surface == option,
                            onClick = {
                                surface = option
                                reset()
                            },
                            label = { Text(option.label) },
                        )
                    }
                }
                val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                // Always one line, only the text changes: adb scripts tap by coordinates, and the
                // layout must not shift when the service briefly unbinds.
                Text(
                    stringResource(if (connected) R.string.devtools_service_running else R.string.devtools_testlist_service_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (connected) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    maxLines = 1,
                )
                Text(comparisonLine("svisle ", groundTruthYPx, engine.absDyPx), style = mono)
                Text(comparisonLine("vodor. ", groundTruthXPx, engine.absDxPx), style = mono)
                val groundTruthMm = groundTruthXPx * scale.mmPerPxX + groundTruthYPx * scale.mmPerPxY
                Text(
                    "celkem GT ${Format.decimal(groundTruthMm, 1)} mm · měřeno ${Format.decimal(engine.countedMm, 1)} mm " +
                        "· událostí ${engine.events}",
                    style = mono,
                )
                OutlinedButton(onClick = reset, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(stringResource(R.string.devtools_testlist_reset))
                }
            }
            val onX: (Double) -> Unit = { groundTruthXPx += it }
            val onY: (Double) -> Unit = { groundTruthYPx += it }
            when (surface) {
                Surface.LAZY -> LazySurface(onX, onY)
                Surface.COLUMN -> ColumnSurface(onX, onY)
                Surface.VIEW -> ViewSurface(onX, onY)
            }
        }
    }
}

private const val LIST_ITEMS = 300
private const val CAROUSEL_ITEMS = 60
private val CAROUSEL_HEIGHT = 110.dp

@Composable
private fun ColumnScope.LazySurface(onX: (Double) -> Unit, onY: (Double) -> Unit) {
    val rowTruth = remember { groundTruthConnection { onX(abs(it.x.toDouble())) } }
    val columnTruth = remember { groundTruthConnection { onY(abs(it.y.toDouble())) } }
    Box(Modifier.fillMaxWidth().height(CAROUSEL_HEIGHT).nestedScroll(rowTruth)) {
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(count = CAROUSEL_ITEMS) { Card(it) }
        }
    }
    Box(Modifier.fillMaxWidth().weight(1f).nestedScroll(columnTruth)) {
        LazyColumn(Modifier.fillMaxSize()) {
            items(count = LIST_ITEMS) { ListItem(it) }
        }
    }
}

@Composable
private fun ColumnScope.ColumnSurface(onX: (Double) -> Unit, onY: (Double) -> Unit) {
    val rowTruth = remember { groundTruthConnection { onX(abs(it.x.toDouble())) } }
    val columnTruth = remember { groundTruthConnection { onY(abs(it.y.toDouble())) } }
    Row(
        Modifier.fillMaxWidth().height(CAROUSEL_HEIGHT).nestedScroll(rowTruth).horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(CAROUSEL_ITEMS) { Card(it) }
    }
    Column(Modifier.fillMaxWidth().weight(1f).nestedScroll(columnTruth).verticalScroll(rememberScrollState())) {
        repeat(LIST_ITEMS) { ListItem(it) }
    }
}

@Composable
private fun ColumnScope.ViewSurface(onX: (Double) -> Unit, onY: (Double) -> Unit) {
    val cardColor = MaterialTheme.colorScheme.secondaryContainer.toArgb()
    val itemColor = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(CAROUSEL_HEIGHT),
        factory = { context ->
            val px = { dp: Float -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics).toInt() }
            HorizontalScrollView(context).apply {
                addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        repeat(CAROUSEL_ITEMS) { index ->
                            addView(
                                TextView(context).apply {
                                    text = "Karta ${index + 1}"
                                    gravity = Gravity.CENTER
                                    setTextColor(textColor)
                                    setBackgroundColor(cardColor)
                                },
                                LinearLayout.LayoutParams(px(200f), LinearLayout.LayoutParams.MATCH_PARENT).apply {
                                    setMargins(px(4f), px(8f), px(4f), px(8f))
                                },
                            )
                        }
                    },
                )
                setOnScrollChangeListener { _, x, _, oldX, _ -> onX(abs(x - oldX).toDouble()) }
            }
        },
    )
    AndroidView(
        modifier = Modifier.fillMaxWidth().weight(1f),
        factory = { context ->
            val px = { dp: Float -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics).toInt() }
            ScrollView(context).apply {
                addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        repeat(LIST_ITEMS) { index ->
                            addView(
                                TextView(context).apply {
                                    text = "Položka ${index + 1}"
                                    gravity = Gravity.CENTER_VERTICAL
                                    setPadding(px(24f), 0, 0, 0)
                                    setTextColor(textColor)
                                    setBackgroundColor(itemColor)
                                },
                                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(64f)).apply {
                                    setMargins(px(12f), px(4f), px(12f), px(4f))
                                },
                            )
                        }
                    },
                )
                setOnScrollChangeListener { _, _, y, _, oldY -> onY(abs(y - oldY).toDouble()) }
            }
        },
    )
}

@Composable
private fun Card(index: Int) {
    Box(
        Modifier.width(200.dp).fillMaxHeight().padding(vertical = 8.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) { Text("Karta ${index + 1}") }
}

@Composable
private fun ListItem(index: Int) {
    Box(
        Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 12.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.CenterStart,
    ) { Text("Položka ${index + 1}", Modifier.padding(horizontal = 12.dp)) }
}

private fun groundTruthConnection(onConsumed: (Offset) -> Unit) = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        onConsumed(consumed)
        return Offset.Zero
    }
}

private fun comparisonLine(label: String, groundTruthPx: Double, measuredPx: Long): String {
    val error = if (groundTruthPx > 0.0) Format.decimal((measuredPx - groundTruthPx) / groundTruthPx * 100.0, 1) + " %" else "—"
    return "$label GT ${Format.decimal(groundTruthPx, 0)} px · měřeno ${Format.integer(measuredPx)} px · chyba $error"
}
