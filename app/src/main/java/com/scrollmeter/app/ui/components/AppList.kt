package com.scrollmeter.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scrollmeter.app.AppGraph
import com.scrollmeter.app.data.model.AppSummary
import com.scrollmeter.app.format.TimeFormatter

/**
 * One app of a period (ADR-036): icon, name, and on the same line the distance with the time in the
 * app in brackets — "1,21 km (3 h 40 min)"; an app with only time (YouTube, D19) "— (2 h 15 min)".
 * Without Usage access there is no time in app, so no brackets — never a 0.
 */
@Composable
fun AppRow(graph: AppGraph, app: AppSummary, usageGranted: Boolean, distance: (Double) -> String, onClick: () -> Unit) {
    val info by rememberAppInfo(graph.appInfoProvider, app.packageName, 36.dp)
    val label = info?.label ?: app.packageName
    val time = app.foregroundMs?.takeIf { usageGranted && it > 0 }?.let(TimeFormatter::duration)
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(info, label, 36.dp)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(app.distanceMm?.let(distance) ?: TimeFormatter.UNKNOWN, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        if (time != null) {
            Spacer(Modifier.width(6.dp))
            Text("($time)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}
