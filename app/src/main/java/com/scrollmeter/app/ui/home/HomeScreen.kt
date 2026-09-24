package com.scrollmeter.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollmeter.app.AppGraph
import com.scrollmeter.app.R
import com.scrollmeter.app.calibration.CalibrationMethod
import com.scrollmeter.app.devtools.DevToolEntry
import com.scrollmeter.app.measurement.PhysicalScale
import com.scrollmeter.app.measurement.PhysicalScaleProvider
import com.scrollmeter.app.ui.components.Format

/** Spec §32: never claim data is being collected unless the service is actually running. */
enum class ServiceStatus { ON, ENABLED_NOT_RUNNING, OFF }

/**
 * POC home: service status with the way to Accessibility settings, the RAM-only total since the
 * service started, the calibration in use with the way to the accuracy screen, and (debug builds)
 * the developer screens. Replaced by the dashboard in Phase 4.
 */
@Composable
fun HomeScreen(
    graph: AppGraph,
    devTools: List<DevToolEntry>,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenAccuracy: () -> Unit,
    onCalibrate: () -> Unit,
    onOpenDevTool: (Int) -> Unit,
) {
    val connected by graph.monitor.serviceConnected.collectAsStateWithLifecycle()
    val totals by graph.monitor.totals.collectAsStateWithLifecycle()
    val calibration by graph.calibrationRepository.state.collectAsStateWithLifecycle(initialValue = null)
    val display = remember(LocalConfiguration.current.orientation) { graph.displayMetricsProvider.read() }
    var enabledInSettings by remember { mutableStateOf(graph.statusChecker.isEnabled()) }
    LifecycleResumeEffect(Unit) {
        enabledInSettings = graph.statusChecker.isEnabled()
        onPauseOrDispose { }
    }
    LaunchedEffect(connected) { enabledInSettings = graph.statusChecker.isEnabled() }
    val status = when {
        connected -> ServiceStatus.ON
        enabledInSettings -> ServiceStatus.ENABLED_NOT_RUNNING
        else -> ServiceStatus.OFF
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.bodyMedium)
            StatusCard(status, onOpenAccessibilitySettings)
            if (status == ServiceStatus.ON) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.home_live_title), style = MaterialTheme.typography.titleMedium)
                        Text(Format.metres(totals.countedMm), style = MaterialTheme.typography.displaySmall)
                        Text(
                            stringResource(
                                R.string.home_live_events,
                                Format.integer(totals.direct + totals.fallback),
                                Format.integer(totals.events - totals.excluded),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(stringResource(R.string.home_live_note), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            calibration?.let { AccuracyCard(PhysicalScaleProvider.resolve(it, display), onOpenAccuracy, onCalibrate) }
            if (devTools.isNotEmpty()) {
                Text(stringResource(R.string.home_devtools_title), style = MaterialTheme.typography.titleMedium)
                devTools.forEachIndexed { index, tool ->
                    OutlinedButton(onClick = { onOpenDevTool(index) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(tool.titleRes))
                    }
                }
            }
        }
    }
}

/** Spec §8: offer the card calibration until it is done; afterwards show what it gives. */
@Composable
private fun AccuracyCard(scale: PhysicalScale, onOpenAccuracy: () -> Unit, onCalibrate: () -> Unit) {
    val manual = scale.method == CalibrationMethod.MANUAL_CARD
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.home_accuracy_title), style = MaterialTheme.typography.titleMedium)
            Text(
                if (manual) {
                    stringResource(R.string.home_accuracy_manual, Format.decimal(scale.mmPerPxY, 4))
                } else {
                    stringResource(R.string.home_accuracy_auto)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!manual) {
                Button(onClick = onCalibrate) { Text(stringResource(R.string.home_calibrate)) }
            }
            OutlinedButton(onClick = onOpenAccuracy) { Text(stringResource(R.string.home_accuracy_open)) }
        }
    }
}

@Composable
private fun StatusCard(status: ServiceStatus, onOpenAccessibilitySettings: () -> Unit) {
    val (title, body) = when (status) {
        ServiceStatus.ON -> R.string.home_status_on to R.string.home_status_on_body
        ServiceStatus.ENABLED_NOT_RUNNING -> R.string.home_status_not_running to R.string.home_status_not_running_body
        ServiceStatus.OFF -> R.string.home_status_off to R.string.home_status_off_body
    }
    val colors = if (status == ServiceStatus.ON) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    }
    Card(Modifier.fillMaxWidth(), colors = colors) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
            if (status == ServiceStatus.OFF) {
                Button(onClick = onOpenAccessibilitySettings) { Text(stringResource(R.string.home_enable_button)) }
            } else {
                OutlinedButton(onClick = onOpenAccessibilitySettings) { Text(stringResource(R.string.home_settings_button)) }
            }
        }
    }
}
