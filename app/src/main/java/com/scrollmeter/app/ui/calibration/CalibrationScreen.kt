package com.scrollmeter.app.ui.calibration

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollmeter.app.AppGraph
import com.scrollmeter.app.R
import com.scrollmeter.app.calibration.CalibrationState
import com.scrollmeter.app.calibration.CardCalibration
import com.scrollmeter.app.calibration.DisplaySnapshot
import com.scrollmeter.app.measurement.PhysicalScaleProvider
import com.scrollmeter.app.ui.components.Format
import com.scrollmeter.app.ui.theme.CalibrationBarBlue
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Spec §8 "Kalibrace displeje": the user matches a bar to the long edge of a payment card
 * (ISO/IEC 7810 ID-1, 85.60 mm) and `mmPerPx = 85.60 / referencePixels`.
 *
 * The bar is **vertical** (ADR-023): 85.60 mm is wider than a phone held upright (~68 mm on the
 * test phone), but fits along its height. It is drawn on a Canvas in layout pixels, which are the
 * display's raw pixels, so the saved length is exactly what the user saw. Square pixels are
 * assumed (spec §8), so the orientation of the bar does not change the result.
 *
 * Height is the scarce resource (the card needs ~1360 of the test phone's 2400 px): the title
 * and the instructions sit beside the bar, where the card covers them once it is in place.
 */
@Composable
fun CalibrationScreen(graph: AppGraph, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val state by graph.calibrationRepository.state.collectAsStateWithLifecycle(initialValue = null)
    val display = remember(LocalConfiguration.current.orientation) { graph.displayMetricsProvider.read() }
    val autoMmPerPx = remember(display) { automaticMmPerPx(display) }
    val autoLengthPx = remember(autoMmPerPx) { CardCalibration.referencePxFor(autoMmPerPx) }

    // 0 until the stored state is known; then the saved card length (recalibration) or the
    // length that xdpi/ydpi predict.
    var lengthPx by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(state, display) {
        val current = state ?: return@LaunchedEffect
        if (lengthPx == 0) lengthPx = startLength(current, display, autoLengthPx)
    }
    var areaHeightPx by remember { mutableIntStateOf(0) }

    val density = LocalDensity.current
    val barTopPx = with(density) { 8.dp.roundToPx() }
    val maxPx = minOf(CardCalibration.plausibleReferencePx.last, areaHeightPx - barTopPx)
    val minPx = CardCalibration.plausibleReferencePx.first
    val usable = lengthPx > 0 && maxPx >= minPx
    val shownPx = if (usable) lengthPx.coerceIn(minPx, maxPx) else 0
    // A bar pinned at the screen's limit cannot have matched a card that is longer still
    // (landscape, a tiny screen): nothing to save until the phone is held upright.
    val atLimit = usable && shownPx >= maxPx

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { areaHeightPx = it.height }) {
                val barX = with(density) { 24.dp.toPx() }
                val barWidth = with(density) { 6.dp.toPx() }
                val guide = with(density) { 1.dp.toPx() }.coerceAtLeast(2f)
                if (shownPx > 0) {
                    Canvas(Modifier.fillMaxSize()) {
                        val top = barTopPx.toFloat()
                        // The bar covers exactly shownPx rows; the guides extend its two ends across the screen.
                        drawRect(CalibrationBarBlue, Offset(barX, top), Size(barWidth, shownPx.toFloat()))
                        drawRect(CalibrationBarBlue, Offset(0f, top), Size(size.width, guide))
                        drawRect(CalibrationBarBlue, Offset(0f, top + shownPx - guide), Size(size.width, guide))
                    }
                }
                Column(
                    Modifier.padding(start = 40.dp, top = 12.dp, end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDone) { Text(stringResource(R.string.back)) }
                        Text(stringResource(R.string.calibration_title), style = MaterialTheme.typography.titleLarge)
                    }
                    Text(stringResource(R.string.calibration_instruction), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.calibration_hint), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (usable) {
                    val mmPerPx = CardCalibration.mmPerPx(shownPx)
                    Text(
                        stringResource(R.string.calibration_value, Format.integer(shownPx.toLong()), Format.decimal(mmPerPx, 4)) +
                            " · " + stringResource(R.string.calibration_vs_auto, signed((mmPerPx / autoMmPerPx - 1.0) * 100.0)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (atLimit) {
                    Text(
                        stringResource(R.string.calibration_too_small),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                val shorter = stringResource(R.string.calibration_shorter)
                val longer = stringResource(R.string.calibration_longer)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { lengthPx = (shownPx - 1).coerceAtLeast(minPx) },
                        enabled = usable,
                        modifier = Modifier.semantics { contentDescription = shorter },
                    ) { Text("−") }
                    Slider(
                        value = shownPx.toFloat(),
                        onValueChange = { lengthPx = it.roundToInt() },
                        valueRange = minPx.toFloat()..maxOf(minPx, maxPx).toFloat(),
                        enabled = usable,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        onClick = { lengthPx = (shownPx + 1).coerceAtMost(maxPx) },
                        enabled = usable,
                        modifier = Modifier.semantics { contentDescription = longer },
                    ) { Text("+") }
                }
                Button(
                    onClick = {
                        val calibration = CardCalibration.create(shownPx, graph.displayMetricsProvider.read(), System.currentTimeMillis())
                        scope.launch {
                            graph.calibrationRepository.saveManual(calibration)
                            onDone()
                        }
                    },
                    enabled = usable && !atLimit,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.calibration_save)) }
                TextButton(
                    onClick = {
                        scope.launch {
                            graph.calibrationRepository.useAutomatic()
                            onDone()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.calibration_skip)) }
            }
        }
    }
}

/** The display's own estimate, one number for both axes: the bar may lie along either (square pixels). */
private fun automaticMmPerPx(display: DisplaySnapshot): Double {
    val scale = PhysicalScaleProvider.fromDisplayMetrics(display.xdpi, display.ydpi, display.densityDpi)
    return (scale.mmPerPxX + scale.mmPerPxY) / 2.0
}

private fun startLength(state: CalibrationState, display: DisplaySnapshot, autoLengthPx: Int): Int =
    state.manual?.takeIf { it.appliesTo(display) }?.referencePx ?: autoLengthPx

private fun signed(percent: Double): String {
    val shown = if (abs(percent) < 0.05) 0.0 else percent // never "-0,0" or "+0,0"
    return (if (shown > 0) "+" else "") + Format.decimal(shown, 1)
}
