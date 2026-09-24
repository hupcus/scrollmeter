package com.scrollmeter.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollmeter.app.AppGraph
import com.scrollmeter.app.R
import com.scrollmeter.app.calibration.CalibrationMethod
import com.scrollmeter.app.devtools.DevToolEntry
import com.scrollmeter.app.measurement.PhysicalScaleProvider
import com.scrollmeter.app.ui.components.ServiceStatus
import com.scrollmeter.app.ui.components.rememberServiceStatus
import com.scrollmeter.app.ui.components.rememberUsageGranted

/**
 * Nastavení — the Phase 5 core: whether the service runs, whether time in app is on, and the way to
 * Přesnost měření; debug builds list the developer screens here (ADR-010). Phase 6 adds the goal,
 * units, notifications, excluded apps, data and privacy sections (spec §44).
 */
@Composable
fun SettingsScreen(
    graph: AppGraph,
    devTools: List<DevToolEntry>,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onOpenAccuracy: () -> Unit,
    onOpenDevTool: (String) -> Unit,
) {
    val context = LocalContext.current
    val status = rememberServiceStatus(graph)
    val usageGranted = rememberUsageGranted(graph)
    val calibration by graph.calibrationRepository.state.collectAsStateWithLifecycle(initialValue = null)
    val display = remember(LocalConfiguration.current.orientation) { graph.displayMetricsProvider.read() }
    val manual = calibration?.let { PhysicalScaleProvider.resolve(it, display).method == CalibrationMethod.MANUAL_CARD }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.settings_section_measurement), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Card(Modifier.fillMaxWidth()) {
            SettingRow(
                title = stringResource(R.string.settings_service),
                value = stringResource(
                    when (status) {
                        ServiceStatus.ON -> R.string.settings_on
                        ServiceStatus.ENABLED_NOT_RUNNING -> R.string.home_status_not_running
                        ServiceStatus.OFF -> R.string.settings_off
                    },
                ),
                onClick = onOpenAccessibilitySettings,
            )
            HorizontalDivider()
            SettingRow(
                title = stringResource(R.string.settings_usage_access),
                value = stringResource(if (usageGranted) R.string.settings_on else R.string.settings_off),
                // Granted: straight to the system page, where it can be revoked; otherwise the disclosure first.
                onClick = { if (usageGranted) graph.usageAccessChecker.openSettings(context) else onOpenUsageAccess() },
            )
            HorizontalDivider()
            SettingRow(
                title = stringResource(R.string.settings_accuracy),
                value = manual?.let { stringResource(if (it) R.string.home_accuracy_manual_short else R.string.home_accuracy_auto_short) },
                onClick = onOpenAccuracy,
            )
        }
        if (devTools.isNotEmpty()) {
            Text(stringResource(R.string.home_devtools_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            devTools.forEach { tool ->
                OutlinedButton(onClick = { onOpenDevTool(tool.key) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(tool.titleRes)) }
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, value: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
