package com.scrollmeter.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.scrollmeter.app.R
import com.scrollmeter.app.format.DistanceFormatter
import com.scrollmeter.app.format.GoalInput
import com.scrollmeter.app.settings.Settings
import com.scrollmeter.app.settings.UnitPreference
import java.util.Locale

/**
 * Spec §25: 100 m … 5 km, or a custom value (10 m … 100 km) typed in m or km. A goal is not a
 * limit — the note says so.
 */
@Composable
fun GoalDialog(currentMm: Double, unit: UnitPreference, locale: Locale, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    val presets = Settings.GOAL_PRESETS_MM
    var custom by rememberSaveable { mutableStateOf(currentMm !in presets) }
    var chosen by rememberSaveable { mutableDoubleStateOf(currentMm) }
    var kilometres by rememberSaveable { mutableStateOf(currentMm >= 1_000_000.0) }
    var text by rememberSaveable {
        mutableStateOf(if (currentMm in presets) "" else trimmed(currentMm / if (currentMm >= 1_000_000.0) 1_000_000.0 else 1_000.0, locale))
    }
    val customMm = GoalInput.parseMm(text, kilometres, locale)
    // Choosing "Vlastní" puts the cursor in the field; a saved custom value opens without the keyboard.
    val field = remember { FocusRequester() }
    LaunchedEffect(custom) { if (custom && text.isEmpty()) field.requestFocus() }
    val result = if (custom) customMm else chosen

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.goal_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.goal_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                presets.forEach { mm ->
                    Option(DistanceFormatter.format(mm, unit, locale), selected = !custom && chosen == mm) {
                        custom = false
                        chosen = mm
                    }
                }
                Option(stringResource(R.string.goal_custom), selected = custom) { custom = true }
                if (custom) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text(stringResource(R.string.goal_custom_label)) },
                        singleLine = true,
                        isError = text.isNotBlank() && customMm == null,
                        supportingText = { if (text.isNotBlank() && customMm == null) Text(stringResource(R.string.goal_custom_error)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().focusRequester(field),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !kilometres, onClick = { kilometres = false }, label = { Text(stringResource(R.string.goal_unit_m)) })
                        FilterChip(selected = kilometres, onClick = { kilometres = true }, label = { Text(stringResource(R.string.goal_unit_km)) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { result?.let(onSave) }, enabled = result != null) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun Option(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun trimmed(value: Double, locale: Locale): String =
    java.text.NumberFormat.getNumberInstance(locale).apply {
        isGroupingUsed = false
        maximumFractionDigits = 3
    }.format(value)

/**
 * Spec §45: one button per outcome — delete the data, or the data plus the settings and the card
 * calibration — and a way out. Nothing is deleted before one of the two is tapped.
 */
@Composable
fun DeleteDataDialog(onDismiss: () -> Unit, onDelete: (alsoSettings: Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_title)) },
        text = { Text(stringResource(R.string.delete_body)) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { onDelete(false) }) { Text(stringResource(R.string.delete_data), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { onDelete(true) }) { Text(stringResource(R.string.delete_everything), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
