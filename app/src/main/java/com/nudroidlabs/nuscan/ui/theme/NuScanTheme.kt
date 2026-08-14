package com.nudroidlabs.nuscan.ui.theme

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

private val LightColors = lightColorScheme(
    primary = Color(0xFF176B5B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA9F2DE),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF4C635B),
    secondaryContainer = Color(0xFFCFE9DF),
    tertiary = Color(0xFF7A5900),
    tertiaryContainer = Color(0xFFFFDEA1),
    background = Color(0xFFF7FBF8),
    surface = Color(0xFFF7FBF8),
    surfaceVariant = Color(0xFFDBE5E0)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8DD6C3),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005143),
    onPrimaryContainer = Color(0xFFA9F2DE),
    secondary = Color(0xFFB3CCC3),
    secondaryContainer = Color(0xFF354B44),
    tertiary = Color(0xFFF1C05D),
    tertiaryContainer = Color(0xFF5C4300)
)

@Composable
fun NuScanTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) DarkColors else LightColors
    }

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
