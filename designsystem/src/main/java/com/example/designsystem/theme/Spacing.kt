package com.example.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

data class Spacing(
    val x1: Int = 4,
    val x2: Int = 8,
    val x3: Int = 12,
    val x4: Int = 16,
    val x6: Int = 24,
    val x8: Int = 32
) {
    val dp1 get() = x1.dp
    val dp2 get() = x2.dp
    val dp3 get() = x3.dp
    val dp4 get() = x4.dp
    val dp6 get() = x6.dp
    val dp8 get() = x8.dp
}

val LocalSpacing = staticCompositionLocalOf { Spacing() }

