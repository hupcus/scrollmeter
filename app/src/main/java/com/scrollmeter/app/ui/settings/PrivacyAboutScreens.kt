package com.scrollmeter.app.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollmeter.app.AppGraph
import com.scrollmeter.app.BuildConfig
import com.scrollmeter.app.R
import com.scrollmeter.app.measurement.PhysicalScaleProvider
import com.scrollmeter.app.ui.calibration.labelRes
import com.scrollmeter.app.ui.calibration.pixelLine
import com.scrollmeter.app.ui.components.ScreenHeader

/**
 * Soukromí (spec §29 text, D19 paragraph): what is read, what is stored, what never is, and why
 * the accessibility service. Phase 7 reuses the texts in the onboarding.
 */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(stringResource(R.string.privacy_title), onBack)
        listOf(
            R.string.privacy_intro, R.string.privacy_stores, R.string.privacy_never, R.string.privacy_usage,
            R.string.privacy_offline, R.string.privacy_accessibility,
        ).forEach { Text(stringResource(it), style = MaterialTheme.typography.bodyLarge) }
    }
}

/** O aplikaci (spec §44): version, device, the measuring method and the scale in force. */
@Composable
fun AboutScreen(graph: AppGraph, onBack: () -> Unit) {
    val calibration by graph.calibrationRepository.state.collectAsStateWithLifecycle(initialValue = null)
    val display = remember(LocalConfiguration.current.orientation) { graph.displayMetricsProvider.read() }
    val scale = calibration?.let { PhysicalScaleProvider.resolve(it, display) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(stringResource(R.string.about_title), onBack)
        Fact(stringResource(R.string.about_version), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        Fact(
            stringResource(R.string.about_device),
            "${Build.MANUFACTURER} ${Build.MODEL}\n" + stringResource(R.string.about_android, Build.VERSION.RELEASE, Build.VERSION.SDK_INT),
        )
        Fact(stringResource(R.string.about_method), stringResource(R.string.about_method_body))
        if (scale != null) {
            Fact(
                stringResource(R.string.about_scale),
                stringResource(scale.method.labelRes()) + "\n" + pixelLine(scale) + "\n" + stringResource(scale.confidence.labelRes()),
            )
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
