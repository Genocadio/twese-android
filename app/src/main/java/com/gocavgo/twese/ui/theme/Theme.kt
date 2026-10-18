package com.gocavgo.twese.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val DarkColorScheme = darkColorScheme(
    primary = TwesePurpleDark,
    secondary = TwesePurpleDark,
    tertiary = TwesePurpleLight,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceAlt,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = DarkBackground,
    onBackground = DarkOnSurface,
    onSurface = DarkOnSurface,
    onSurfaceVariant = DarkMuted,
    outline = DarkOutline,
    error = CavgoRedDark
)

private val LightColorScheme = lightColorScheme(
    primary = TwesePurple,
    secondary = TwesePurple,
    tertiary = TwesePurpleLight,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceAlt,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = LightOnSurface,
    onBackground = LightOnSurface,
    onSurface = LightOnSurface,
    onSurfaceVariant = LightMuted,
    outline = LightOutline,
    error = CavgoRed
)

@Immutable
data class DriversColors(
    val primary: Color,
    val purple: Color,
    val purpleLight: Color,
    val blue: Color,
    val green: Color,
    val amber: Color,
    val red: Color,
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val topStart: Color
)

private val LightDriversColors = DriversColors(
    primary = TwesePurple,
    purple = TwesePurple,
    purpleLight = TwesePurpleLight,
    blue = TwesePurple,
    green = CavgoGreen,
    amber = CavgoAmber,
    red = CavgoRed,
    background = LightBackground,
    surface = LightSurface,
    surfaceAlt = LightSurfaceAlt,
    textPrimary = LightOnSurface,
    textSecondary = LightMuted,
    divider = LightOutline,
    topStart = LightTopStart
)

private val DarkDriversColors = DriversColors(
    primary = TwesePurpleDark,
    purple = TwesePurpleDark,
    purpleLight = TwesePurpleTintDark,
    blue = TwesePurpleDark,
    green = CavgoGreenDark,
    amber = CavgoAmberDark,
    red = CavgoRedDark,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceAlt = DarkSurfaceAlt,
    textPrimary = DarkOnSurface,
    textSecondary = DarkMuted,
    divider = DarkOutline,
    topStart = DarkTopStart
)

val LocalDriversColors = staticCompositionLocalOf { LightDriversColors }

@Composable
fun TweseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // False by default to maintain signature Twese Purple brand consistency
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Cap the system font scale to 1.2x to prevent layout breakage
    val fontScale = LocalDensity.current.fontScale.coerceIn(0.85f, 1.2f)

    CompositionLocalProvider(
        LocalDriversColors provides if (darkTheme) DarkDriversColors else LightDriversColors,
        LocalDensity provides Density(LocalDensity.current.density, fontScale = fontScale)
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}