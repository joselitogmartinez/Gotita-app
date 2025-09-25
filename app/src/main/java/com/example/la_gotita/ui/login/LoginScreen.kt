package com.example.la_gotita.ui.login

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.la_gotita.data.model.UserRole
import com.example.la_gotita.utils.BiometricAuthListener
import com.example.la_gotita.utils.BiometricAuthManager
import kotlinx.coroutines.delay

private const val TAG_LOGIN_SCREEN = "LoginScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onLoginSuccess: (UserRole) -> Unit
) {
    val context = LocalContext.current
    val activity = LocalContext.current as? AppCompatActivity
    val focusManager = LocalFocusManager.current

    val email by authViewModel.email.collectAsState()
    val password by authViewModel.password.collectAsState()
    val loginUiState by authViewModel.loginUiState.collectAsState()
    val showBiometricPromptFlag by authViewModel.showBiometricPrompt.collectAsState()
    val justLoggedInWithCredentials by authViewModel.justLoggedInWithCredentials.collectAsState()

    var passwordVisible by remember { mutableStateOf(false) }
    var showPasswordResetDialog by remember { mutableStateOf(false) }
    var emailForPasswordReset by remember { mutableStateOf("") }

    val biometricAuthManager = remember(activity) {
        if (activity != null) {
            Log.d(TAG_LOGIN_SCREEN, "BiometricAuthManager: Activity available, creating/getting manager.")
            BiometricAuthManager(activity, object : BiometricAuthListener {
                override fun onBiometricAuthSuccess() {
                    Log.d(TAG_LOGIN_SCREEN, "BiometricAuthListener: onBiometricAuthSuccess")
                    authViewModel.onBiometricAuthenticationSuccess(context) // Pasar context
                }

                override fun onBiometricAuthError(errorCode: Int, errString: CharSequence) {
                    Log.e(TAG_LOGIN_SCREEN, "BiometricAuthListener: onBiometricAuthError. Code: ${errorCode}, Message: ${errString}")
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_CANCELED // Otro código común de cancelación
                    ) {
                        Toast.makeText(context, "Error biométrico: ${errString}", Toast.LENGTH_LONG).show()
                    }
                    authViewModel.onBiometricAuthenticationCancelledOrError()
                }

                override fun onBiometricAuthFailed() {
                    Log.w(TAG_LOGIN_SCREEN, "BiometricAuthListener: onBiometricAuthFailed")
                    Toast.makeText(context, "Fallo la autenticación biométrica.", Toast.LENGTH_SHORT).show()
                    authViewModel.onBiometricAuthenticationCancelledOrError()
                }
            })
        } else {
            Log.w(TAG_LOGIN_SCREEN, "BiometricAuthManager: Activity is NULL, BiometricAuthManager not created.")
            null
        }
    }

    val canAuthenticate by remember(biometricAuthManager) {
        derivedStateOf {
            val result = biometricAuthManager?.canAuthenticate() ?: false
            Log.d(TAG_LOGIN_SCREEN, "Biometric Check: canAuthenticate() result: ${result}")
            result
        }
    }

    // Efecto para intentar login biométrico inicial si no hay sesión de Firebase
    LaunchedEffect(key1 = authViewModel, key2 = activity) {
        if (loginUiState.firebaseUser == null && activity != null) {
            Log.d(TAG_LOGIN_SCREEN, "Initial LaunchedEffect: No Firebase user. Attempting initial biometric login.")
            authViewModel.attemptInitialBiometricLogin(context)
        } else {
            Log.d(TAG_LOGIN_SCREEN, "Initial LaunchedEffect: Firebase user: ${loginUiState.firebaseUser?.uid} or no activity. Skipping initial biometric attempt.")
        }
    }

    // Efecto para manejar el flag justLoggedInWithCredentials
    LaunchedEffect(justLoggedInWithCredentials) {
        if (justLoggedInWithCredentials) {
            Log.d(TAG_LOGIN_SCREEN, "justLoggedInWithCredentials is true. Resetting flag in ViewModel.")
            // Podríamos añadir un pequeño delay si es necesario para que otros efectos reaccionen primero,
            // pero usualmente la actualización de estado en el ViewModel y la recolección aquí son suficientes.
            // delay(50) // Descomentar para probar si hay problemas de timing
            authViewModel.resetJustLoggedInWithCredentialsFlag()
        }
    }

    // Efecto para mostrar el prompt biométrico
    LaunchedEffect(showBiometricPromptFlag, biometricAuthManager, activity) {
        Log.d(TAG_LOGIN_SCREEN, "ShowPromptEffect(v2): showBiometricPromptFlag: ${showBiometricPromptFlag}")
        if (showBiometricPromptFlag && biometricAuthManager != null && activity != null) {
            Log.d(TAG_LOGIN_SCREEN, "ShowPromptEffect(v2): Invocando showBiometricPrompt() sin prechequeo estricto.")
            biometricAuthManager.showBiometricPrompt()
        }
    }

    // Efecto para manejar la navegación post-login (cuando userAppRole se establece)
    LaunchedEffect(loginUiState.userAppRole) {
        loginUiState.userAppRole?.let { role ->
            Log.d(TAG_LOGIN_SCREEN, "NavigationEffect: Login successful, userAppRole: ${role}. Navigating.")
            onLoginSuccess(role)
            // La lógica de `toggleBiometricPreference` ahora está dentro de `fetchUserDetails`
            // cuando `isCredentialLogin` es true, o se llama explícitamente desde la UI de configuración.
            // Aquí ya no necesitamos llamarla directamente para habilitar por defecto.
        }
    }


    if (showPasswordResetDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordResetDialog = false },
            title = { Text("Restablecer Contraseña") },
            text = {
                OutlinedTextField(
                    value = emailForPasswordReset,
                    onValueChange = { emailForPasswordReset = it },
                    label = { Text("Correo Electrónico Registrado") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (emailForPasswordReset.isNotBlank()) {
                            authViewModel.sendPasswordResetEmail(emailForPasswordReset, context)
                            showPasswordResetDialog = false
                        } else {
                            Toast.makeText(context, "Por favor, ingresa tu correo.", Toast.LENGTH_SHORT).show()
                        }
                    })
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (emailForPasswordReset.isNotBlank()) {
                        authViewModel.sendPasswordResetEmail(emailForPasswordReset, context)
                        showPasswordResetDialog = false
                    } else {
                        Toast.makeText(context, "Por favor, ingresa tu correo.", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Enviar Enlace") }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordResetDialog = false }) { Text("Cancelar") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Bienvenido a La Gotita", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Inicia sesión para continuar", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(32.dp))

        // Mostrar indicador de carga si isLoading es true Y no estamos a punto de mostrar el prompt biométrico
        // (porque el prompt biométrico tiene su propia UI de sistema)
        if (loginUiState.isLoading && !showBiometricPromptFlag) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.height(16.dp))
        }

        loginUiState.loginError?.let { errorMsg ->
            Text(
                text = errorMsg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        // Mostrar campos de login si el prompt biométrico no está activo o a punto de mostrarse
        if (!showBiometricPromptFlag) {
            OutlinedTextField(
                value = email,
                onValueChange = { authViewModel.onEmailChange(it) },
                label = { Text("Correo Electrónico") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = "Icono de Correo") }
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { authViewModel.onPasswordChange(it) },
                label = { Text("Contraseña") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    authViewModel.loginUser(context) // Pasar context
                }),
                leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = "Icono de Contraseña") },
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = image, contentDescription = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña")
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = {
                        emailForPasswordReset = email // Pre-fill
                        showPasswordResetDialog = true
                    },
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    Text("¿Olvidaste tu contraseña?", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    focusManager.clearFocus()
                    authViewModel.loginUser(context) // Pasar context
                },
                enabled = !loginUiState.isLoading,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Iniciar Sesión")
            }
        }

        // Botón explícito para biometría
        // Solo mostrar si la biometría está disponible y el prompt automático no está activo.
        if (activity != null && canAuthenticate && !showBiometricPromptFlag) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    Log.d(TAG_LOGIN_SCREEN, "Explicit Biometric button clicked.")
                    // Al hacer clic explícito, no nos importa justLoggedInWithCredentials, el usuario lo pide.
                    // El ViewModel (attemptInitial o una nueva función para explícito) manejaría el 'last candidate'.
                    // Por ahora, si es explícito, asumimos que es el 'last candidate' el que se debe usar.
                    authViewModel.attemptInitialBiometricLogin(context) // Reutilizamos esta lógica para el botón explícito
                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Filled.Fingerprint, contentDescription = "Usar Huella")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ingresar con Huella")
            }
        } else if (activity != null && !canAuthenticate && !showBiometricPromptFlag) {
            // Text("Biometría no configurada en este dispositivo.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
