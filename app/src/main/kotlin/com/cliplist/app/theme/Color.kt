package com.cliplist.app.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Brand palette — used on API < 31 (no dynamic color).
// Inspired by the SanDisk Clip Sport's orange-and-blue palette.
private val BrandBlue   = Color(0xFF0053A4)
private val BrandOrange = Color(0xFFF5800F)

val LightColorScheme = lightColorScheme(
    primary   = BrandBlue,
    secondary = BrandOrange,
)

val DarkColorScheme = darkColorScheme(
    primary   = Color(0xFF9ECAFF),
    secondary = Color(0xFFFFB86C),
)
