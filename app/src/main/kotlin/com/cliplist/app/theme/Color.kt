package com.cliplist.app.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Brand palette — used on API < 31 (no dynamic color).
// Matches the app icon's teal→lime gradient (chosen 2026-05-30).
private val BrandTeal = Color(0xFF0E7C7B)
private val BrandLime = Color(0xFFA8CF45)

// On-colors are set explicitly: the defaults come from Material's baseline (purple) palette and
// don't contrast our brand hues — text drawn with onPrimary must stay readable on the gradients.
val LightColorScheme = lightColorScheme(
    primary     = BrandTeal,
    onPrimary   = Color.White,
    secondary   = BrandLime,
    onSecondary = Color(0xFF263A00),   // dark olive
    tertiary    = Color(0xFF2E6B5E),   // deep green — loader hub / third accent
    onTertiary  = Color.White,
)

val DarkColorScheme = darkColorScheme(
    primary     = Color(0xFF6FD3CD),
    onPrimary   = Color(0xFF003735),   // dark teal on the pastel primary
    secondary   = Color(0xFFBFE38A),
    onSecondary = Color(0xFF263A00),
    tertiary    = Color(0xFF9CE0C4),   // light mint-green — loader hub / third accent
    onTertiary  = Color(0xFF00382B),
)
