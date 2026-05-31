package com.cliplist.app.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Brand palette — used on API < 31 (no dynamic color).
// Matches the app icon's teal→lime gradient (chosen 2026-05-30).
private val BrandTeal = Color(0xFF0E7C7B)
private val BrandLime = Color(0xFFA8CF45)

val LightColorScheme = lightColorScheme(
    primary   = BrandTeal,
    secondary = BrandLime,
)

val DarkColorScheme = darkColorScheme(
    primary   = Color(0xFF6FD3CD),
    secondary = Color(0xFFBFE38A),
)
