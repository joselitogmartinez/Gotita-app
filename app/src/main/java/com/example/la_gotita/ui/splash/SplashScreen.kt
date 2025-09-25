package com.example.la_gotita.ui.splash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.la_gotita.ui.login.AuthViewModel
import com.example.la_gotita.ui.navigation.AppScreenRoutes
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    authViewModel: AuthViewModel,
    onNavigate: (String) -> Unit
) {
    val uiState by authViewModel.loginUiState.collectAsState()
    val context = LocalContext.current
    val showBiometric by authViewModel.showBiometricPrompt.collectAsState()

    LaunchedEffect(Unit) {
        // Decidir rápidamente qué hacer.
        if (authViewModel.shouldAttemptBiometricOnLaunch(context)) {
            // Navegamos a Login para que el prompt biométrico pueda mostrarse desde la pantalla adecuada
            onNavigate(AppScreenRoutes.LOGIN)
            authViewModel.resetJustLoggedInWithCredentialsFlag()
            authViewModel.attemptInitialBiometricLogin(context)
        } else {
            // Si ya hay usuario autenticado y rol conocido, navegar; si no, a login.
            if (uiState.firebaseUser != null && uiState.userAppRole != null) {
                val route = when (uiState.userAppRole) {
                    com.example.la_gotita.data.model.UserRole.ADMIN -> AppScreenRoutes.ADMIN_DASHBOARD_ROOT
                    com.example.la_gotita.data.model.UserRole.EMPLOYEE -> AppScreenRoutes.EMPLOYEE_DASHBOARD
                    null -> AppScreenRoutes.LOGIN
                }
                onNavigate(route)
                authViewModel.resetJustLoggedInWithCredentialsFlag()
            } else {
                onNavigate(AppScreenRoutes.LOGIN)
                authViewModel.resetJustLoggedInWithCredentialsFlag()
            }
        }
    }

    // Si durante la pantalla splash se logra el rol por biometric, navegar.
    LaunchedEffect(uiState.userAppRole) {
        uiState.userAppRole?.let { role ->
            val route = when (role) {
                com.example.la_gotita.data.model.UserRole.ADMIN -> AppScreenRoutes.ADMIN_DASHBOARD_ROOT
                com.example.la_gotita.data.model.UserRole.EMPLOYEE -> AppScreenRoutes.EMPLOYEE_DASHBOARD
            }
            onNavigate(route)
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (showBiometric || uiState.isLoading) {
            CircularProgressIndicator()
        } else {
            Text("Cargando...", style = MaterialTheme.typography.bodyLarge)
        }
    }
}
