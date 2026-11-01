package com.resolvex.app.ui.theme

import android.app.Activity
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

private val DarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = NavyDeep,
    secondary = ElectricBlue,
    onSecondary = Color.White,
    tertiary = NeonPurple,
    background = NavyDeep,
    surface = NavyDark,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

private val LightColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = NavyDeep,
    secondary = ElectricBlue,
    onSecondary = Color.White,
    tertiary = NeonPurple,
    background = NavyDeep,
    surface = NavyDark,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun ResolvexTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
