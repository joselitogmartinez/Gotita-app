package com.example.la_gotita.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController

/**
 * SafeBackHandler: Maneja el botón atrás (AppBar o sistema) con un pequeño debounce para evitar
 * pops múltiples rápidos que dejen la pila vacía y generen pantallas negras.
 */
@Composable
fun SafeBackHandler(
    enabled: Boolean = true,
    lockMs: Long = 300,
    onBack: () -> Unit
) {
    val (locked, setLocked) = remember { mutableStateOf(false) }

    // Desbloqueo automático tras lockMs
    LaunchedEffect(locked) {
        if (locked) {
            kotlinx.coroutines.delay(lockMs)
            setLocked(false)
        }
    }

    BackHandler(enabled = enabled && !locked) {
        if (!locked) {
            setLocked(true)
            onBack()
        }
    }
}

/**
 * popBackOrNavigateTo: intenta hacer popBackStack(); si no puede, navega al destino raíz de forma segura.
 */
fun NavHostController.popBackOrNavigateTo(rootRoute: String) {
    val popped = this.popBackStack()
    if (!popped) {
        this.navigate(rootRoute) {
            popUpTo(rootRoute) { inclusive = false }
            launchSingleTop = true
            restoreState = true
        }
    }
}

/**
 * navigateTopLevel: navegación para destinos top-level (bottom bar / drawer) evitando apilar entradas.
 * Requiere el startDestinationId del grafo actual para popUpTo con save/restore state.
 */
fun NavHostController.navigateTopLevel(route: String, startDestinationId: Int) {
    try {
        this.navigate(route) {
            popUpTo(startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    } catch (e: Exception) {
        android.util.Log.e("NavUtils", "navigateTopLevel failed for route=$route", e)
        // fallback: intentar volver a la raíz
        try {
            this.navigate(startDestinationId.toString()) {
                popUpTo(startDestinationId) { inclusive = false }
                launchSingleTop = true
            }
        } catch (_: Exception) { /* swallow */ }
    }
}
