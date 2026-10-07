package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val Video2PhotoColorScheme = darkColorScheme(
    primary = ElectricIndigo,
    onPrimary = TextPrimary,
    primaryContainer = NavySurfaceElevated,
    onPrimaryContainer = TextPrimary,
    secondary = VibrantPurple,
    onSecondary = TextPrimary,
    secondaryContainer = NavySurfaceCard,
    onSecondaryContainer = TextPrimary,
    tertiary = NeonCyan,
    onTertiary = NavyBackground,
    background = NavyBackground,
    onBackground = TextPrimary,
    surface = NavySurface,
    onSurface = TextPrimary,
    surfaceVariant = NavySurfaceCard,
    onSurfaceVariant = TextSecondary,
    outline = NavySurfaceCardBorder,
    error = RoseError,
    onError = TextPrimary
)

@Composable
fun Video2PhotoTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = NavyBackground.toArgb()
                window.navigationBarColor = NavyBackground.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = Video2PhotoColorScheme,
        typography = Typography,
        content = content
    )
}
