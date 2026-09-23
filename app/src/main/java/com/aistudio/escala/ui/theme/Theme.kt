package com.aistudio.escala.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = EscalaPrimaryDark,
    onPrimary = EscalaOnPrimaryDark,
    primaryContainer = EscalaPrimaryContainerDark,
    onPrimaryContainer = EscalaOnPrimaryContainerDark,
    secondary = EscalaSecondaryDark,
    onSecondary = EscalaOnSecondaryDark,
    secondaryContainer = EscalaSecondaryContainerDark,
    onSecondaryContainer = EscalaOnSecondaryContainerDark,
    tertiary = EscalaTertiaryDark,
    onTertiary = EscalaOnTertiaryDark,
    tertiaryContainer = EscalaTertiaryContainerDark,
    onTertiaryContainer = EscalaOnTertiaryContainerDark,
    background = EscalaBackgroundDark,
    onBackground = EscalaOnBackgroundDark,
    surface = EscalaSurfaceDark,
    onSurface = EscalaOnSurfaceDark,
    surfaceVariant = EscalaSurfaceVariantDark,
    onSurfaceVariant = EscalaOnSurfaceVariantDark,
    outline = EscalaOutlineDark,
    outlineVariant = EscalaOutlineVariantDark,
    error = EscalaErrorTextDark,
    onError = EscalaOnPrimaryDark,
    errorContainer = EscalaErrorBgDark,
    onErrorContainer = EscalaErrorTextDark
)

private val LightColorScheme = lightColorScheme(
    primary = EscalaPrimaryLight,
    onPrimary = EscalaOnPrimaryLight,
    primaryContainer = EscalaPrimaryContainerLight,
    onPrimaryContainer = EscalaOnPrimaryContainerLight,
    secondary = EscalaSecondaryLight,
    onSecondary = EscalaOnSecondaryLight,
    secondaryContainer = EscalaSecondaryContainerLight,
    onSecondaryContainer = EscalaOnSecondaryContainerLight,
    tertiary = EscalaTertiaryLight,
    onTertiary = EscalaOnTertiaryLight,
    tertiaryContainer = EscalaTertiaryContainerLight,
    onTertiaryContainer = EscalaOnTertiaryContainerLight,
    background = EscalaBackgroundLight,
    onBackground = EscalaOnBackgroundLight,
    surface = EscalaSurfaceLight,
    onSurface = EscalaOnSurfaceLight,
    surfaceVariant = EscalaSurfaceVariantLight,
    onSurfaceVariant = EscalaOnSurfaceVariantLight,
    outline = EscalaOutlineLight,
    outlineVariant = EscalaOutlineVariantLight,
    error = EscalaErrorTextLight,
    onError = EscalaOnPrimaryLight,
    errorContainer = EscalaErrorBgLight,
    onErrorContainer = EscalaErrorTextLight
)

@Composable
fun EscalaTheme(
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

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.surface.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

