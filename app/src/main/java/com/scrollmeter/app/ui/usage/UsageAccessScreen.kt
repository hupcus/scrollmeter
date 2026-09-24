package com.scrollmeter.app.ui.usage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.scrollmeter.app.AppGraph
import com.scrollmeter.app.R

/**
 * The disclosure before Usage-access settings (ADR-021, User Data policy: app usage is sensitive).
 * Says what is read, what is stored, what is not, and that the app works without it; only the
 * explicit "Povolit" opens the settings. Coming back with access granted closes the screen.
 * Phase 7 reuses it as onboarding step 6.
 */
@Composable
fun UsageAccessScreen(graph: AppGraph, onGranted: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        if (graph.usageAccessChecker.isGranted()) onGranted()
        onPauseOrDispose { }
    }
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.usage_access_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.usage_access_intro), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.usage_access_what), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.usage_access_not), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.usage_access_optional), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.usage_access_steps), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { graph.usageAccessChecker.openSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.usage_access_allow))
            }
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.not_now)) }
        }
    }
}
