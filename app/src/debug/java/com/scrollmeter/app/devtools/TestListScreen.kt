package com.scrollmeter.app.devtools

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
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
 * Spec §35 Measurement Test: our own lists with an exact ground truth next to what the
 * accessibility pipeline measured for our package. Ground truth = Σ|consumed| reported to a
 * [NestedScrollConnection] wrapped around each list — drag and fling alike.
 * While this screen is open our own package is measured (test mode); leaving restores exclusion.
 */
@OptIn(FlowPreview::class)
@Composable
fun TestListScreen(graph: AppGraph, onBack: () -> Unit) {
    DisposableEffect(Unit) {
        graph.measurementSettings.includeOwnPackage = true
        onDispose { graph.measurementSettings.includeOwnPackage = false }
    }
    var groundTruthXPx by remember { mutableDoubleStateOf(0.0) }
    var groundTruthYPx by remember { mutableDoubleStateOf(0.0) }
    val rowTruth = remember { groundTruthConnection { consumed -> groundTruthXPx += abs(consumed.x.toDouble()) } }
    val columnTruth = remember { groundTruthConnection { consumed -> groundTruthYPx += abs(consumed.y.toDouble()) } }
    val engine by graph.monitor.selfTest.collectAsStateWithLifecycle()
    val connected by graph.monitor.serviceConnected.collectAsStateWithLifecycle()
    val scale = remember { graph.readDisplayScale().scale }

    // One summary line after things settle, for the adb-driven accuracy runs (Tests A, C, D, E).
    LaunchedEffect(Unit) {
        combine(snapshotFlow { groundTruthXPx to groundTruthYPx }, graph.monitor.selfTest) { gt, eng ->
            Triple(gt.first, gt.second, eng)
        }
            .debounce(1_500)
            .collect { (gtX, gtY, eng) ->
                Log.d(
                    LOG_TAG,
                    String.format(
                        Locale.ROOT,
                        "TESTLIST gt_x_px=%.1f gt_y_px=%.1f eng_x_px=%d eng_y_px=%d gt_mm=%.3f eng_mm=%.3f eng_events=%d",
                        gtX, gtY, eng.absDxPx, eng.absDyPx,
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
                val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                if (!connected) {
                    Text(stringResource(R.string.devtools_testlist_service_off), color = MaterialTheme.colorScheme.error)
                }
                Text(comparisonLine("svisle ", groundTruthYPx, engine.absDyPx), style = mono)
                Text(comparisonLine("vodor. ", groundTruthXPx, engine.absDxPx), style = mono)
                val groundTruthMm = groundTruthXPx * scale.mmPerPxX + groundTruthYPx * scale.mmPerPxY
                Text(
                    "celkem GT ${Format.decimal(groundTruthMm, 1)} mm · měřeno ${Format.decimal(engine.countedMm, 1)} mm " +
                        "· událostí ${engine.events}",
                    style = mono,
                )
                OutlinedButton(
                    onClick = {
                        groundTruthXPx = 0.0
                        groundTruthYPx = 0.0
                        graph.monitor.resetSelfTest()
                    },
                    modifier = Modifier.padding(vertical = 4.dp),
                ) { Text(stringResource(R.string.devtools_testlist_reset)) }
            }
            Box(Modifier.fillMaxWidth().height(140.dp).nestedScroll(rowTruth)) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(count = CAROUSEL_ITEMS) { index ->
                        Box(
                            Modifier.width(200.dp).fillMaxHeight().padding(vertical = 8.dp)
                                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) { Text("Karta ${index + 1}") }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f).nestedScroll(columnTruth)) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(count = LIST_ITEMS) { index ->
                        Box(
                            Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 12.dp, vertical = 4.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.CenterStart,
                        ) { Text("Položka ${index + 1}", Modifier.padding(horizontal = 12.dp)) }
                    }
                }
            }
        }
    }
}

private const val LIST_ITEMS = 500
private const val CAROUSEL_ITEMS = 100

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
