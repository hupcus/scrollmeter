package com.scrollmeter.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.scrollmeter.app.insights.LimitLevel

// Fallback palette for API 29–30 (no dynamic color). Final colours are a Phase 4 decision.
private val Blue = Color(0xFF1E4FD8)
private val BlueLight = Color(0xFFB4C5FF)
private val Teal = Color(0xFF00696E)
private val TealLight = Color(0xFF80D4DA)

/** The calibration bar — the instruction calls it "modrá čára", so it stays blue under dynamic colour. */
val CalibrationBarBlue = Color(0xFF2F6BFF)

private val LightColors = lightColorScheme(primary = Blue, secondary = Teal)
private val DarkColors = darkColorScheme(primary = BlueLight, secondary = TealLight)

/** Whether [ScrollMeterTheme] runs dark — the limit colours have their own dark variants. */
val LocalDarkTheme = staticCompositionLocalOf { false }

/**
 * The daily limit's colours (ADR-036): a card's [container] with [onContainer] text, and a chart's
 * [bar]. Fixed, not dynamic — green, orange and red must stay green, orange and red whatever the
 * wallpaper. Every text pair is at least 7:1.
 */
data class LimitColors(val container: Color, val onContainer: Color, val bar: Color)

private val LimitLight = mapOf(
    LimitLevel.UNDER to LimitColors(Color(0xFFC8E6C9), Color(0xFF1B5E20), Color(0xFF43A047)),
    LimitLevel.NEAR to LimitColors(Color(0xFFFFE0B2), Color(0xFF7A3E00), Color(0xFFFB8C00)),
    LimitLevel.OVER to LimitColors(Color(0xFFFFCDD2), Color(0xFF8E1B1B), Color(0xFFE53935)),
)
private val LimitDark = mapOf(
    LimitLevel.UNDER to LimitColors(Color(0xFF1E4620), Color(0xFFC8E6C9), Color(0xFF66BB6A)),
    LimitLevel.NEAR to LimitColors(Color(0xFF4E3200), Color(0xFFFFE0B2), Color(0xFFFFA726)),
    LimitLevel.OVER to LimitColors(Color(0xFF5B1A1A), Color(0xFFFFCDD2), Color(0xFFEF5350)),
)

/** Without a limit (NONE) the theme's own neutral colours. */
@Composable
fun limitColors(level: LimitLevel): LimitColors {
    val table = if (LocalDarkTheme.current) LimitDark else LimitLight
    return table[level] ?: MaterialTheme.colorScheme.let { LimitColors(it.surfaceContainerHigh, it.onSurface, it.primary) }
}

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
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
