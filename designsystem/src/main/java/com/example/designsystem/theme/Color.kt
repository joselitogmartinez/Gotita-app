package com.example.designsystem.theme

import androidx.compose.ui.graphics.Color

// Brand Palette (Base Tokens)
object BrandColors {
    val Purple80 = Color(0xFFD0BCFF)
    val PurpleGrey80 = Color(0xFFCCC2DC)
    val Pink80 = Color(0xFFEFB8C8)

    val Purple40 = Color(0xFF6650A4)
    val PurpleGrey40 = Color(0xFF625B71)
    val Pink40 = Color(0xFF7D5260)

    val Success = Color(0xFF2E7D32)
    val Warning = Color(0xFFF9A825)
    val Error = Color(0xFFB3261E)
}

// App-specific tokens for header chips (light and dark variants)
object AppColorTokens {
    // Light theme tokens
    val ChipGreenBgLight = Color(0xFFE8F5E9)
    val ChipGreenTextLight = Color(0xFF2E7D32)
    val ChipBlueBgLight = Color(0xFFE3F2FD)
    val ChipBlueTextLight = Color(0xFF1565C0)
    val ChipBlue2BgLight = Color(0xFFE1F5FE)
    val ChipBlue2TextLight = Color(0xFF0277BD)
    // Red tokens for exits
    val ChipRedBgLight = Color(0xFFFFEBEE) // soft red background
    val ChipRedTextLight = Color(0xFFD32F2F) // stronger red text

    // Dark theme tokens (darker backgrounds, lighter text)
    val ChipGreenBgDark = Color(0xFF1B5E20).copy(alpha = 0.12f)
    val ChipGreenTextDark = Color(0xFF81C784)
    val ChipBlueBgDark = Color(0xFF0D47A1).copy(alpha = 0.12f)
    val ChipBlueTextDark = Color(0xFF90CAF9)
    val ChipBlue2BgDark = Color(0xFF01579B).copy(alpha = 0.12f)
    val ChipBlue2TextDark = Color(0xFF81D4FA)
    // Red dark
    val ChipRedBgDark = Color(0xFF8A0000).copy(alpha = 0.12f)
    val ChipRedTextDark = Color(0xFFFF8A80)
}
