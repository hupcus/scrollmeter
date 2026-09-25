package com.scrollmeter.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Fallback palette for API 29–30 (no dynamic color). Final colours are a Phase 4 decision.
private val Blue = Color(0xFF1E4FD8)
private val BlueLight = Color(0xFFB4C5FF)
private val Teal = Color(0xFF00696E)
private val TealLight = Color(0xFF80D4DA)

/** The calibration bar — the instruction calls it "modrá čára", so it stays blue under dynamic colour. */
val CalibrationBarBlue = Color(0xFF2F6BFF)

private val LightColors = lightColorScheme(primary = Blue, secondary = Teal)
private val DarkColors = darkColorScheme(primary = BlueLight, secondary = TealLight)

@Composable
fun ScrollMeterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
