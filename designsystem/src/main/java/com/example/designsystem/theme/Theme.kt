package com.example.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = BrandColors.Purple80,
    secondary = BrandColors.PurpleGrey80,
    tertiary = BrandColors.Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = BrandColors.Purple40,
    secondary = BrandColors.PurpleGrey40,
    tertiary = BrandColors.Pink40
)

val LocalThemeMode = staticCompositionLocalOf { ThemeMode.SYSTEM }

// App-level colors exposed to the app for components like header chips
data class AppColors(
    val chipGreenBg: Color,
    val chipGreenText: Color,
    val chipBlueBg: Color,
    val chipBlueText: Color,
    val chipBlue2Bg: Color,
    val chipBlue2Text: Color,
    val chipRedBg: Color,
    val chipRedText: Color
)

val LocalAppColors = staticCompositionLocalOf {
    AppColors(
        chipGreenBg = AppColorTokens.ChipGreenBgLight,
        chipGreenText = AppColorTokens.ChipGreenTextLight,
        chipBlueBg = AppColorTokens.ChipBlueBgLight,
        chipBlueText = AppColorTokens.ChipBlueTextLight,
        chipBlue2Bg = AppColorTokens.ChipBlue2BgLight,
        chipBlue2Text = AppColorTokens.ChipBlue2TextLight,
        chipRedBg = AppColorTokens.ChipRedBgLight,
        chipRedText = AppColorTokens.ChipRedTextLight
    )
}

@Composable
fun LaGotitaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    spacing: Spacing = Spacing(),
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    val appColors = if (isDark) {
        AppColors(
            chipGreenBg = AppColorTokens.ChipGreenBgDark,
            chipGreenText = AppColorTokens.ChipGreenTextDark,
            chipBlueBg = AppColorTokens.ChipBlueBgDark,
            chipBlueText = AppColorTokens.ChipBlueTextDark,
            chipBlue2Bg = AppColorTokens.ChipBlue2BgDark,
            chipBlue2Text = AppColorTokens.ChipBlue2TextDark,
            chipRedBg = AppColorTokens.ChipRedBgDark,
            chipRedText = AppColorTokens.ChipRedTextDark
        )
    } else {
        AppColors(
            chipGreenBg = AppColorTokens.ChipGreenBgLight,
            chipGreenText = AppColorTokens.ChipGreenTextLight,
            chipBlueBg = AppColorTokens.ChipBlueBgLight,
            chipBlueText = AppColorTokens.ChipBlueTextLight,
            chipBlue2Bg = AppColorTokens.ChipBlue2BgLight,
            chipBlue2Text = AppColorTokens.ChipBlue2TextLight,
            chipRedBg = AppColorTokens.ChipRedBgLight,
            chipRedText = AppColorTokens.ChipRedTextLight
        )
    }

    CompositionLocalProvider(LocalSpacing provides spacing, LocalThemeMode provides themeMode, LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LaGotitaTypography,
            content = content
        )
    }
}
