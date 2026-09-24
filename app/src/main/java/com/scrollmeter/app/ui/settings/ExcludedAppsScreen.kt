package com.scrollmeter.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollmeter.app.AppGraph
import com.scrollmeter.app.R
import com.scrollmeter.app.apps.ExclusionReason
import com.scrollmeter.app.apps.ExclusionSuggestion
import com.scrollmeter.app.ui.components.AppIcon
import com.scrollmeter.app.ui.components.ScreenHeader
import com.scrollmeter.app.ui.components.SectionTitle
import com.scrollmeter.app.ui.components.launchWrite
import com.scrollmeter.app.ui.components.rememberAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Vyloučené aplikace (spec §14, §44, ADR-031): the home screen, the keyboard and the system UI as
 * detected on this phone are suggested first; below, every app with stored data (and any excluded
 * one without), by name. A switch excludes from the next event on and hides the app's stored data.
 */
@Composable
fun ExcludedAppsScreen(graph: AppGraph, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings by graph.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    val seen by remember { graph.scrollRepository.seenPackages() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val suggestions by produceState(initialValue = emptyList<ExclusionSuggestion>()) {
        value = withContext(Dispatchers.IO) { graph.suggestedExclusions.detect() }
    }
    val excluded = settings?.excludedPackages ?: return
    val suggested = suggestions.map { it.packageName }.toSet()
    val others = (seen.toSet() + excluded) - suggested - graph.ownPackage
    val labels by produceState(initialValue = emptyMap<String, String>(), others) {
        value = withContext(Dispatchers.IO) { others.associateWith { graph.appInfoProvider.label(it) } }
    }
    val sorted = others.sortedWith(compareBy<String> { (labels[it] ?: it).lowercase() }.thenBy { it })

    fun toggle(pkg: String, on: Boolean) {
        scope.launchWrite(context) { graph.settingsRepository.setExcluded(pkg, on) }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ScreenHeader(stringResource(R.string.excluded_title), onBack)
                Text(stringResource(R.string.excluded_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (suggestions.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.excluded_suggested)) }
            items(suggestions, key = { "s:" + it.packageName }) { s ->
                ExcludedRow(graph, s.packageName, stringResource(reasonLabel(s.reason)), s.packageName in excluded) { toggle(s.packageName, it) }
            }
        }
        item { SectionTitle(stringResource(R.string.excluded_seen)) }
        if (sorted.isEmpty()) {
            item { Text(stringResource(R.string.excluded_none_seen), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(sorted, key = { it }) { pkg ->
            ExcludedRow(graph, pkg, null, pkg in excluded) { toggle(pkg, it) }
        }
    }
}

@Composable
private fun ExcludedRow(graph: AppGraph, packageName: String, reason: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val info by rememberAppInfo(graph.appInfoProvider, packageName, 36.dp)
    val label = info?.label ?: packageName
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(info, label)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            // An app we cannot see (no launcher entry, ADR-008) has only its package name: show it whole, once.
            val named = label != packageName
            Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = if (named) 1 else 2, overflow = TextOverflow.Ellipsis)
            Text(
                reason ?: if (named) packageName else stringResource(R.string.excluded_no_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

private fun reasonLabel(reason: ExclusionReason): Int = when (reason) {
    ExclusionReason.LAUNCHER -> R.string.reason_launcher
    ExclusionReason.KEYBOARD -> R.string.reason_keyboard
    ExclusionReason.SYSTEM_UI -> R.string.reason_system_ui
}
