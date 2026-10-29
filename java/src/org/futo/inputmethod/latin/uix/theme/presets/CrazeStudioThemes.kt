package org.futo.inputmethod.latin.uix.theme.presets

import androidx.compose.ui.graphics.Color
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.KeyboardColorScheme
import org.futo.inputmethod.latin.uix.extendedDarkColorScheme
import org.futo.inputmethod.latin.uix.extendedLightColorScheme
import org.futo.inputmethod.latin.uix.theme.ThemeOption

private val crazeNightScheme = extendedDarkColorScheme(
    primary = Color(0xFFC5B8FF),
    onPrimary = Color(0xFF26185F),
    primaryContainer = Color(0xFF392B70),
    onPrimaryContainer = Color(0xFFE9E1FF),
    secondary = Color(0xFF78E2CF),
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF174E43),
    onSecondaryContainer = Color(0xFFB3F3E5),
    tertiary = Color(0xFFFFC58A),
    onTertiary = Color(0xFF4D2800),
    tertiaryContainer = Color(0xFF70400A),
    onTertiaryContainer = Color(0xFFFFDDBB),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF9C92BC),
    outlineVariant = Color(0xFF49435E),
    surface = Color(0xFF12121C),
    onSurface = Color(0xFFE9E3F5),
    onSurfaceVariant = Color(0xFFC9C1D9),
    surfaceContainerHighest = Color(0xFF302D40),
    shadow = Color.Black.copy(alpha = 0.7f),
    keyboardSurface = Color(0xFF151522),
    keyboardSurfaceDim = Color(0xFF10101A),
    keyboardContainer = Color(0xFF242438),
    keyboardContainerVariant = Color(0xFF1C1C2D),
    onKeyboardContainer = Color(0xFFEDE8FF),
    keyboardPress = Color(0xFF383852),
    keyboardFade0 = Color(0xFF151522),
    keyboardFade1 = Color(0xFF151522),
    primaryTransparent = Color(0xFFC5B8FF).copy(alpha = 0.3f),
    onSurfaceTransparent = Color(0xFFE9E3F5).copy(alpha = 0.1f),
)

private val crazeDayScheme = extendedLightColorScheme(
    primary = Color(0xFF5843B5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF21105D),
    secondary = Color(0xFF006B60),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB5F0E5),
    onSecondaryContainer = Color(0xFF00201B),
    tertiary = Color(0xFF805500),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDEA8),
    onTertiaryContainer = Color(0xFF291800),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF79748B),
    outlineVariant = Color(0xFFD0C9DD),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1C1B22),
    onSurfaceVariant = Color(0xFF494650),
    surfaceContainerHighest = Color(0xFFE9E3F2),
    shadow = Color.Black.copy(alpha = 0.25f),
    keyboardSurface = Color(0xFFF0EDF8),
    keyboardSurfaceDim = Color(0xFFE7E3F0),
    keyboardContainer = Color.White,
    keyboardContainerVariant = Color(0xFFF7F4FC),
    onKeyboardContainer = Color(0xFF242035),
    keyboardPress = Color(0xFFDED8EF),
    keyboardFade0 = Color(0xFFE9E3F2),
    keyboardFade1 = Color(0xFFE9E3F2),
    primaryTransparent = Color(0xFF5843B5).copy(alpha = 0.3f),
    onSurfaceTransparent = Color(0xFF1C1B22).copy(alpha = 0.1f),
)

private fun withKeyRoundness(scheme: KeyboardColorScheme, roundness: Float) =
    scheme.copy(
        extended = scheme.extended.copy(
            advancedThemeOptions = scheme.extended.advancedThemeOptions.copy(
                keyRoundness = roundness
            )
        )
    )

val CrazeNight = ThemeOption(
    dynamic = false,
    key = "CrazeNight",
    name = R.string.theme_craze_night,
    available = { true },
) { withKeyRoundness(crazeNightScheme, 1.3f) }

val CrazeDay = ThemeOption(
    dynamic = false,
    key = "CrazeDay",
    name = R.string.theme_craze_day,
    available = { true },
) { withKeyRoundness(crazeDayScheme, 0.65f) }
