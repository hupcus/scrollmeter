package com.scrollmeter.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.scrollmeter.app.AppGraph
import com.scrollmeter.app.R
import com.scrollmeter.app.ui.components.ScreenHeader
import com.scrollmeter.app.ui.components.launchWrite

/** The four paragraphs of the prominent disclosure, word for word from spec §30. */
@Composable
fun ColumnScope.DisclosureTexts() {
    listOf(R.string.disclosure_why, R.string.disclosure_what, R.string.disclosure_not, R.string.disclosure_local)
        .forEach { Text(stringResource(it), style = MaterialTheme.typography.bodyLarge) }
}

/**
 * The disclosure on its own screen, for the way into the accessibility settings from outside the
 * onboarding (dashboard banner, Nastavení) while it has not been accepted (spec §30, ADR-032).
 * Only the explicit button counts as consent; "Zpět" or leaving the screen does not.
 */
@Composable
fun DisclosureScreen(graph: AppGraph, onAccepted: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // A double tap must not open the settings twice.
    var accepting by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(stringResource(R.string.disclosure_title), onBack)
        DisclosureTexts()
        Button(
            onClick = {
                accepting = true
                scope.launchWrite(context, onFailure = { accepting = false }) {
                    graph.settingsRepository.setPrivacyDisclosureAccepted(true)
                    onAccepted()
                }
            },
            enabled = !accepting,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.disclosure_accept)) }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.back)) }
    }
}
