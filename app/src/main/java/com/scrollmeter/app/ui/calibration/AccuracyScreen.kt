package com.scrollmeter.app.ui.calibration

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollmeter.app.AppGraph
import com.scrollmeter.app.R
import com.scrollmeter.app.calibration.CalibrationConfidence
import com.scrollmeter.app.calibration.CalibrationMethod
import com.scrollmeter.app.measurement.PhysicalScale
import com.scrollmeter.app.measurement.PhysicalScaleProvider
import com.scrollmeter.app.ui.components.Format
import com.scrollmeter.app.ui.components.appLocale

/**
 * Spec §33 accuracy screen: which calibration measures now, 1 pixel = x mm, when it was made,
 * and the way to recalibrate. Shows the scale the engine uses — the same [PhysicalScaleProvider.resolve].
 */
@Composable
fun AccuracyScreen(graph: AppGraph, onBack: () -> Unit, onCalibrate: () -> Unit) {
    val state by graph.calibrationRepository.state.collectAsStateWithLifecycle(initialValue = null)
    val display = remember(LocalConfiguration.current.orientation) { graph.displayMetricsProvider.read() }
    val locale = appLocale()

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                Text(stringResource(R.string.accuracy_title), style = MaterialTheme.typography.titleLarge)
            }
            val current = state ?: return@Column
            val scale = PhysicalScaleProvider.resolve(current, display)
            val manual = current.manual
            val manualActive = manual != null && scale.method == CalibrationMethod.MANUAL_CARD

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.accuracy_method_label), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(scale.method.labelRes()), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(scale.confidence.labelRes()), style = MaterialTheme.typography.bodyMedium)
                    Text(pixelLine(scale), style = MaterialTheme.typography.titleMedium)
                    if (manualActive) {
                        Text(stringResource(R.string.accuracy_calibrated_label), style = MaterialTheme.typography.labelLarge)
                        Text(Format.date(manual.calibratedAtMs, locale = locale), style = MaterialTheme.typography.bodyLarge)
                    } else {
                        Text(stringResource(R.string.accuracy_auto_note), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (manual != null && !manualActive) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(stringResource(R.string.accuracy_stale), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                stringResource(
                    R.string.accuracy_device,
                    "${display.manufacturer} ${display.model}".trim(),
                    display.widthPx,
                    display.heightPx,
                    Format.decimal(display.xdpi, 1, locale),
                    Format.decimal(display.ydpi, 1, locale),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(stringResource(R.string.accuracy_history_note), style = MaterialTheme.typography.bodySmall)
            Button(onClick = onCalibrate, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (manualActive) R.string.accuracy_recalibrate else R.string.accuracy_calibrate))
            }
        }
    }
}

/** "1 pixel = 0,0630 mm", or both axes when xdpi and ydpi differ at the shown precision. */
@Composable
internal fun pixelLine(scale: PhysicalScale): String {
    val locale = appLocale()
    val x = Format.decimal(scale.mmPerPxX, 4, locale)
    val y = Format.decimal(scale.mmPerPxY, 4, locale)
    return if (x == y) stringResource(R.string.accuracy_px_uniform, x) else stringResource(R.string.accuracy_px_axes, x, y)
}

@StringRes
internal fun CalibrationMethod.labelRes(): Int = when (this) {
    CalibrationMethod.MANUAL_CARD -> R.string.accuracy_method_manual
    CalibrationMethod.DISPLAY_METRICS -> R.string.accuracy_method_display
    CalibrationMethod.UNKNOWN -> R.string.accuracy_method_unknown
}

@StringRes
internal fun CalibrationConfidence.labelRes(): Int = when (this) {
    CalibrationConfidence.HIGH -> R.string.accuracy_confidence_high
    CalibrationConfidence.MEDIUM -> R.string.accuracy_confidence_medium
    CalibrationConfidence.LOW -> R.string.accuracy_confidence_low
}
