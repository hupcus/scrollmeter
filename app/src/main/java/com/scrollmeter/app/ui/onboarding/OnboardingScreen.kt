package com.scrollmeter.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollmeter.app.AppGraph
import com.scrollmeter.app.R
import com.scrollmeter.app.onboarding.AccessibilityGate
import com.scrollmeter.app.onboarding.OnboardingFlow
import com.scrollmeter.app.onboarding.OnboardingState
import com.scrollmeter.app.onboarding.OnboardingStep
import com.scrollmeter.app.ui.calibration.CalibrationScreen
import com.scrollmeter.app.ui.components.ServiceStatus
import com.scrollmeter.app.ui.components.launchWrite
import com.scrollmeter.app.ui.components.rememberServiceStatus
import com.scrollmeter.app.ui.usage.UsageAccessScreen

/**
 * First run (spec §31, ADR-032): Kolik toho nascrolluješ → Jak měření funguje → the prominent
 * disclosure (spec §30) → switching the service on, detected on return → calibration (card or the
 * automatic estimate) → optional time in app (D19). [OnboardingFlow] decides what may be skipped:
 * nothing before the service runs, everything after. The last step writes
 * `onboardingCompleted`, and MainActivity swaps to the app.
 */
@Composable
fun OnboardingScreen(graph: AppGraph, onOpenAccessibilitySettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = graph.settingsRepository
    val settings by repository.settings.collectAsStateWithLifecycle(initialValue = null)
    val calibration by graph.calibrationRepository.state.collectAsStateWithLifecycle(initialValue = null)
    val status = rememberServiceStatus(graph)
    var step by rememberSaveable { mutableStateOf(OnboardingStep.WELCOME) }
    var calibrating by rememberSaveable { mutableStateOf(false) }
    var openedSettings by rememberSaveable { mutableStateOf(false) }
    val state = OnboardingState(
        disclosureAccepted = settings?.privacyDisclosureAccepted == true,
        serviceEnabled = status != ServiceStatus.OFF,
    )

    fun finish() {
        scope.launchWrite(context) { repository.setOnboardingCompleted(true) }
    }
    fun forward() {
        if (!OnboardingFlow.canLeave(step, state)) return
        OnboardingFlow.next(step)?.let { step = it } ?: finish()
    }

    BackHandler(enabled = calibrating || OnboardingFlow.previous(step) != null) {
        if (calibrating) calibrating = false else OnboardingFlow.previous(step)?.let { step = it }
    }

    // Spec §31 screen 4: back from the settings with the service on → the next step by itself.
    LaunchedEffect(step, state.serviceEnabled, openedSettings) {
        if (step == OnboardingStep.ENABLE_SERVICE && openedSettings && state.serviceEnabled) {
            openedSettings = false
            forward()
        }
    }

    if (calibrating) {
        CalibrationScreen(graph, onBack = { calibrating = false }, onSaved = {
            calibrating = false
            forward()
        })
        return
    }

    when (step) {
        OnboardingStep.WELCOME -> StepPage(step, stringResource(R.string.onboarding_welcome_title), actions = {
            Button(onClick = ::forward, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_start)) }
        }) {
            Image(
                painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(120.dp).align(Alignment.CenterHorizontally),
            )
            Body(R.string.onboarding_welcome_body)
            Text(stringResource(R.string.onboarding_welcome_claim), style = MaterialTheme.typography.titleMedium)
        }

        OnboardingStep.HOW_IT_WORKS -> StepPage(step, stringResource(R.string.onboarding_how_title), actions = {
            Button(onClick = ::forward, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_next)) }
        }) {
            listOf(R.string.onboarding_how_delta, R.string.onboarding_how_not_content, R.string.onboarding_how_fling, R.string.onboarding_how_limits)
                .forEach { Bullet(it) }
        }

        OnboardingStep.DISCLOSURE -> StepPage(step, stringResource(R.string.disclosure_title), actions = {
            Button(
                onClick = {
                    scope.launchWrite(context) {
                        repository.setPrivacyDisclosureAccepted(true)
                        step = OnboardingStep.ENABLE_SERVICE
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.disclosure_accept)) }
        }) {
            DisclosureTexts()
        }

        OnboardingStep.ENABLE_SERVICE -> StepPage(step, stringResource(R.string.onboarding_enable_title), actions = {
            if (OnboardingFlow.canLeave(step, state)) {
                Button(onClick = ::forward, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_next)) }
            } else {
                Button(
                    onClick = {
                        when (AccessibilityGate.route(state.disclosureAccepted)) {
                            AccessibilityGate.Route.SHOW_DISCLOSURE -> step = OnboardingStep.DISCLOSURE
                            AccessibilityGate.Route.OPEN_SETTINGS -> {
                                openedSettings = true
                                onOpenAccessibilitySettings()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.disclosure_open_settings)) }
            }
        }) {
            ServiceState(state.serviceEnabled)
            Body(R.string.onboarding_enable_steps)
            Hint(R.string.onboarding_enable_restricted)
            Hint(R.string.onboarding_enable_force_stop)
        }

        OnboardingStep.CALIBRATION -> {
            val manual = calibration?.manual != null
            StepPage(step, stringResource(R.string.onboarding_calibration_title), actions = {
                if (manual) {
                    Button(onClick = ::forward, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_next)) }
                    OutlinedButton(onClick = { calibrating = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.accuracy_recalibrate)) }
                } else {
                    Button(onClick = { calibrating = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_calibration_card)) }
                    // No card calibration stored = the automatic estimate is what measures: nothing to write.
                    OutlinedButton(onClick = ::forward, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_calibration_auto)) }
                }
            }) {
                Body(R.string.onboarding_calibration_body)
                if (manual) Body(R.string.onboarding_calibration_done)
            }
        }

        // D19: optional — "Povolit" or "Teď ne" both finish; granted on return finishes too.
        OnboardingStep.USAGE_ACCESS -> UsageAccessScreen(
            graph,
            onGranted = ::finish,
            onBack = ::finish,
            header = { StepIndicator(step) },
        )
    }
}

@Composable
private fun StepPage(step: OnboardingStep, title: String, actions: @Composable ColumnScope.() -> Unit, body: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        bottomBar = {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = actions,
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StepIndicator(step)
            Text(title, style = MaterialTheme.typography.headlineMedium)
            body()
        }
    }
}

@Composable
fun StepIndicator(step: OnboardingStep) {
    val total = OnboardingFlow.steps.size
    val number = OnboardingFlow.number(step)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(progress = { number.toFloat() / total }, modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.onboarding_step, number, total), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Body(text: Int) = Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)

@Composable
private fun Hint(text: Int) =
    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Bullet(text: Int) {
    Row {
        Text("•", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
    }
}

/** Spec §32: says only what is true — switched on or not. */
@Composable
private fun ServiceState(enabled: Boolean) {
    val colors = if (enabled) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    }
    Card(Modifier.fillMaxWidth(), colors = colors) {
        Text(
            stringResource(if (enabled) R.string.onboarding_enable_done else R.string.onboarding_enable_waiting),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}
