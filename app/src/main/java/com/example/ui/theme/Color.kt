package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Authoritative Kanak Design System Tokens matching 9 Design Boards
// Light, high-contrast, professional FMCG ERP palette
val KanakBackground = Color(0xFFF8F9FA)      // Clean crisp light background
val KanakSurface = Color(0xFFFFFFFF)         // Pure white cards & sheets
val KanakSurfaceVariant = Color(0xFFF1F5F9)  // Soft light grey/blue container

val KanakPrimaryText = Color(0xFF10234A)     // Dark navy text (high contrast against KanakBackground & KanakSurface)
val KanakSecondaryText = Color(0xFF64748B)   // Slate grey text (high readability)
val KanakPrimaryBlue = Color(0xFF1769E8)     // Vibrant Royal Blue action
val KanakAccentLight = Color(0xFFEAF2FF)     // Pastel blue badge & indicator

val KanakSuccess = Color(0xFF138A50)         // Crisp emerald green
val KanakSuccessBackground = Color(0xFFE7F7EE) // Pastel green badge
val KanakError = Color(0xFFDB303B)           // Alert red
val KanakErrorBackground = Color(0xFFFDECEE) // Pastel red container
val KanakWarning = Color(0xFFD97706)         // Warm amber warning
val KanakWarningBackground = Color(0xFFFEF3C7) // Pastel yellow container
val KanakDivider = Color(0xFFE2E8F0)         // Clean 1dp subtle border

// Fallback dark tokens with high contrast white/light-grey text (never dark-on-dark)
val KanakBackgroundDark = Color(0xFF0C1322)
val KanakSurfaceDark = Color(0xFF162032)
val KanakSurfaceVariantDark = Color(0xFF1F2B42)
val KanakPrimaryTextDark = Color(0xFFFFFFFF)
val KanakSecondaryTextDark = Color(0xFFCBD5E1)
val KanakDividerDark = Color(0xFF334155)
