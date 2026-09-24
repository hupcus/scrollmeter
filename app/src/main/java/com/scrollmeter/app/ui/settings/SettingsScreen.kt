package com.scrollmeter.app.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollmeter.app.AppGraph
import com.scrollmeter.app.R
import com.scrollmeter.app.calibration.CalibrationMethod
import com.scrollmeter.app.devtools.DevToolEntry
import com.scrollmeter.app.format.DistanceFormatter
import com.scrollmeter.app.measurement.PhysicalScaleProvider
import com.scrollmeter.app.notifications.NotificationKind
import com.scrollmeter.app.settings.ThemePreference
import com.scrollmeter.app.settings.UnitPreference
import com.scrollmeter.app.ui.components.ChoiceDialog
import com.scrollmeter.app.ui.components.SectionTitle
import com.scrollmeter.app.ui.components.ServiceStatus
import com.scrollmeter.app.ui.components.SettingRow
import com.scrollmeter.app.ui.components.SwitchRow
import com.scrollmeter.app.ui.components.appLocale
import com.scrollmeter.app.ui.components.launchWrite
import com.scrollmeter.app.ui.components.rememberServiceStatus
import com.scrollmeter.app.ui.components.rememberUsageGranted
import kotlinx.coroutines.launch
import android.provider.Settings as AndroidSettings

/** Navigation out of Nastavení. */
class SettingsActions(
    val openAccessibilitySettings: () -> Unit,
    val openUsageAccess: () -> Unit,
    val openAccuracy: () -> Unit,
    val openExcluded: () -> Unit,
    val openExport: () -> Unit,
    val openPrivacy: () -> Unit,
    val openAbout: () -> Unit,
    val openDevTool: (String) -> Unit,
)

private enum class SettingsDialog { GOAL, UNITS, THEME, DELETE }

/**
 * Nastavení (spec §44): Měření (service, time in app, accuracy, goal, excluded apps), Jednotky,
 * Zobrazení, Oznámení (opt-in; POST_NOTIFICATIONS asked when one is switched on — ADR-030), Data
 * (CSV export, delete everything), Soukromí, O aplikaci; debug builds add the developer screens.
 */
@Composable
fun SettingsScreen(graph: AppGraph, devTools: List<DevToolEntry>, actions: SettingsActions) {
    val context = LocalContext.current
    val locale = appLocale()
    val scope = rememberCoroutineScope()
    val repository = graph.settingsRepository
    val status = rememberServiceStatus(graph)
    val usageGranted = rememberUsageGranted(graph)
    val settings by repository.settings.collectAsStateWithLifecycle(initialValue = null)
    val calibration by graph.calibrationRepository.state.collectAsStateWithLifecycle(initialValue = null)
    val display = remember(LocalConfiguration.current.orientation) { graph.displayMetricsProvider.read() }
    val manual = calibration?.let { PhysicalScaleProvider.resolve(it, display).method == CalibrationMethod.MANUAL_CARD }
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }

    var notificationsAllowed by remember { mutableStateOf(notificationsAllowed(context)) }
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = notificationsAllowed(context)
        onPauseOrDispose { }
    }
    var askingFor by rememberSaveable { mutableStateOf<NotificationKind?>(null) }
    // After a refusal (and on API 33+ the system stops asking after the second one) the switch stays
    // off; the blocked note below then shows the way to the system page instead of a dead switch.
    var permissionRefused by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val kind = askingFor
        askingFor = null
        notificationsAllowed = notificationsAllowed(context)
        if (!granted) permissionRefused = true
        if (granted && kind != null) scope.launchWrite(context) { repository.setNotify(kind, true) }
    }

    fun setNotify(kind: NotificationKind, on: Boolean) {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (on && needsPermission) {
            askingFor = kind
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            scope.launchWrite(context) { repository.setNotify(kind, on) }
        }
    }

    val current = settings ?: return
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)

        SectionTitle(stringResource(R.string.settings_section_measurement))
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
                onClick = actions.openAccessibilitySettings,
            )
            HorizontalDivider()
            SettingRow(
                title = stringResource(R.string.settings_usage_access),
                value = stringResource(if (usageGranted) R.string.settings_on else R.string.settings_off),
                // Granted: straight to the system page, where it can be revoked; otherwise the disclosure first.
                onClick = { if (usageGranted) graph.usageAccessChecker.openSettings(context) else actions.openUsageAccess() },
            )
            HorizontalDivider()
            SettingRow(
                title = stringResource(R.string.settings_accuracy),
                value = manual?.let { stringResource(if (it) R.string.home_accuracy_manual_short else R.string.home_accuracy_auto_short) },
                onClick = actions.openAccuracy,
            )
            HorizontalDivider()
            SettingRow(
                title = stringResource(R.string.settings_goal),
                value = DistanceFormatter.format(current.dailyGoalMm, current.unitPreference, locale),
                onClick = { dialog = SettingsDialog.GOAL },
            )
            HorizontalDivider()
            val excluded = current.excludedPackages.size
            SettingRow(
                title = stringResource(R.string.settings_excluded),
                value = if (excluded == 0) stringResource(R.string.settings_excluded_none) else pluralStringResource(R.plurals.settings_excluded_count, excluded, excluded),
                onClick = actions.openExcluded,
            )
        }

        SectionTitle(stringResource(R.string.settings_section_units))
        Card(Modifier.fillMaxWidth()) {
            SettingRow(stringResource(R.string.settings_units), stringResource(unitLabel(current.unitPreference))) { dialog = SettingsDialog.UNITS }
        }

        SectionTitle(stringResource(R.string.settings_section_display))
        Card(Modifier.fillMaxWidth()) {
            SettingRow(stringResource(R.string.settings_theme), stringResource(themeLabel(current.theme))) { dialog = SettingsDialog.THEME }
            HorizontalDivider()
            SwitchRow(stringResource(R.string.settings_comparisons), stringResource(R.string.settings_comparisons_body), current.showComparisons) { on ->
                scope.launchWrite(context) { repository.setShowComparisons(on) }
            }
        }

        SectionTitle(stringResource(R.string.settings_section_notifications))
        Card(Modifier.fillMaxWidth()) {
            SwitchRow(stringResource(R.string.notify_goal), stringResource(R.string.notify_goal_body), current.notifyGoal) { setNotify(NotificationKind.GOAL, it) }
            HorizontalDivider()
            SwitchRow(stringResource(R.string.notify_record), stringResource(R.string.notify_record_body), current.notifyRecord) { setNotify(NotificationKind.RECORD, it) }
            HorizontalDivider()
            SwitchRow(stringResource(R.string.notify_summary), stringResource(R.string.notify_summary_body), current.notifySummary) { setNotify(NotificationKind.SUMMARY, it) }
            val anyOn = current.notifyGoal || current.notifyRecord || current.notifySummary
            if ((anyOn || permissionRefused) && !notificationsAllowed) {
                HorizontalDivider()
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.notify_blocked), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { openNotificationSettings(context) }) { Text(stringResource(R.string.notify_open_settings)) }
                }
            }
        }

        SectionTitle(stringResource(R.string.settings_section_data))
        Card(Modifier.fillMaxWidth()) {
            SettingRow(stringResource(R.string.settings_export), stringResource(R.string.settings_export_body), actions.openExport)
            HorizontalDivider()
            SettingRow(stringResource(R.string.settings_delete), null) { dialog = SettingsDialog.DELETE }
        }

        SectionTitle(stringResource(R.string.settings_section_privacy))
        Card(Modifier.fillMaxWidth()) { SettingRow(stringResource(R.string.settings_privacy), null, actions.openPrivacy) }

        SectionTitle(stringResource(R.string.settings_section_about))
        Card(Modifier.fillMaxWidth()) { SettingRow(stringResource(R.string.settings_about), null, actions.openAbout) }

        if (devTools.isNotEmpty()) {
            SectionTitle(stringResource(R.string.home_devtools_title))
            devTools.forEach { tool ->
                OutlinedButton(onClick = { actions.openDevTool(tool.key) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(tool.titleRes)) }
            }
        }
    }

    when (dialog) {
        SettingsDialog.GOAL -> GoalDialog(
            currentMm = current.dailyGoalMm,
            unit = current.unitPreference,
            locale = locale,
            onDismiss = { dialog = null },
            onSave = { mm ->
                dialog = null
                scope.launchWrite(context) { repository.setDailyGoalMm(mm) }
            },
        )
        SettingsDialog.UNITS -> ChoiceDialog(
            title = stringResource(R.string.settings_section_units),
            options = UnitPreference.entries,
            selected = current.unitPreference,
            label = { stringResource(unitLabel(it)) },
            onSelect = {
                dialog = null
                scope.launchWrite(context) { repository.setUnitPreference(it) }
            },
            onDismiss = { dialog = null },
        )
        SettingsDialog.THEME -> ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemePreference.entries,
            selected = current.theme,
            label = { stringResource(themeLabel(it)) },
            onSelect = {
                dialog = null
                scope.launchWrite(context) { repository.setTheme(it) }
            },
            onDismiss = { dialog = null },
        )
        SettingsDialog.DELETE -> DeleteDataDialog(
            onDismiss = { dialog = null },
            onDelete = { alsoSettings ->
                dialog = null
                scope.launch {
                    val ok = graph.dataEraser.erase(alsoSettings)
                    Toast.makeText(context, if (ok) R.string.delete_done else R.string.delete_failed, Toast.LENGTH_LONG).show()
                }
            },
        )
        null -> Unit
    }
}

private fun unitLabel(unit: UnitPreference): Int = when (unit) {
    UnitPreference.AUTOMATIC -> R.string.unit_automatic
    UnitPreference.METRES -> R.string.unit_metres
    UnitPreference.KILOMETRES -> R.string.unit_kilometres
}

private fun themeLabel(theme: ThemePreference): Int = when (theme) {
    ThemePreference.SYSTEM -> R.string.theme_system
    ThemePreference.LIGHT -> R.string.theme_light
    ThemePreference.DARK -> R.string.theme_dark
}

/** Permission (API 33+) and the app's notification switch together. */
private fun notificationsAllowed(context: Context): Boolean {
    val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(AndroidSettings.ACTION_SETTINGS))
    }
}
