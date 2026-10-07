package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Video2Photo Premium Dark Palette
val NavyBackground = Color(0xFF080C16)
val NavySurface = Color(0xFF101728)
val NavySurfaceCard = Color(0xFF162035)
val NavySurfaceCardBorder = Color(0xFF263554)
val NavySurfaceElevated = Color(0xFF1D2942)

val ElectricIndigo = Color(0xFF6366F1)
val VibrantPurple = Color(0xFF8B5CF6)
val NeonCyan = Color(0xFF38BDF8)
val EmeraldGreen = Color(0xFF10B981)
val AmberWarning = Color(0xFFF59E0B)
val RoseError = Color(0xFFF43F5E)

val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

val AccentGradient = Brush.horizontalGradient(
    colors = listOf(ElectricIndigo, VibrantPurple, NeonCyan)
)

val CardGradient = Brush.verticalGradient(
    colors = listOf(NavySurfaceCard, NavySurface)
)

val GlowGradient = Brush.radialGradient(
    colors = listOf(Color(0x336366F1), Color(0x00000000))
)
