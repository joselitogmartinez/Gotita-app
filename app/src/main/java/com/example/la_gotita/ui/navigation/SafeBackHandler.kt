package com.example.la_gotita.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
fun SafeBackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit
) {
    BackHandler(enabled = enabled, onBack = onBack)
}
