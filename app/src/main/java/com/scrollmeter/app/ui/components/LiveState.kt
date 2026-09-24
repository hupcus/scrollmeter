package com.scrollmeter.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollmeter.app.AppGraph
import com.scrollmeter.app.calibration.CalibrationConfidence
import com.scrollmeter.app.measurement.PhysicalScaleProvider
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay

/** Spec §32: never claim data is being collected unless the service is actually running. */
enum class ServiceStatus { ON, ENABLED_NOT_RUNNING, OFF }

/** Connected = running; otherwise whether it is switched on in the system settings, re-read on resume. */
@Composable
fun rememberServiceStatus(graph: AppGraph): ServiceStatus {
    val connected by graph.monitor.serviceConnected.collectAsStateWithLifecycle()
    var enabledInSettings by remember { mutableStateOf(graph.statusChecker.isEnabled()) }
    LifecycleResumeEffect(Unit) {
        enabledInSettings = graph.statusChecker.isEnabled()
        onPauseOrDispose { }
    }
    LaunchedEffect(connected) { enabledInSettings = graph.statusChecker.isEnabled() }
    return when {
        connected -> ServiceStatus.ON
        enabledInSettings -> ServiceStatus.ENABLED_NOT_RUNNING
        else -> ServiceStatus.OFF
    }
}

/** Usage access (ADR-021) can be granted or revoked in the system settings: re-read on resume. */
@Composable
fun rememberUsageGranted(graph: AppGraph): Boolean {
    var granted by remember { mutableStateOf(graph.usageAccessChecker.isGranted()) }
    LifecycleResumeEffect(Unit) {
        granted = graph.usageAccessChecker.isGranted()
        onPauseOrDispose { }
    }
    return granted
}

/** Today's local date: re-read on resume, and rolled over at midnight while the screen stays open. */
@Composable
fun rememberToday(): LocalDate {
    var today by remember { mutableStateOf(LocalDate.now()) }
    LifecycleResumeEffect(Unit) {
        today = LocalDate.now()
        onPauseOrDispose { }
    }
    LaunchedEffect(today) {
        val midnight = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        delay((midnight - System.currentTimeMillis()).coerceAtLeast(0) + 1_000)
        today = LocalDate.now()
    }
    return today
}

/**
 * The confidence of the scale in force now (ADR-016, ADR-024). Measurement quality rates stored data with it
 * (ADR-029); until the calibration is read it is MEDIUM — the automatic scale, the lower claim.
 */
@Composable
fun rememberCalibrationConfidence(graph: AppGraph): CalibrationConfidence {
    val calibration by graph.calibrationRepository.state.collectAsStateWithLifecycle(initialValue = null)
    val display = remember(LocalConfiguration.current.orientation) { graph.displayMetricsProvider.read() }
    return calibration?.let { PhysicalScaleProvider.resolve(it, display).confidence } ?: CalibrationConfidence.MEDIUM
}
