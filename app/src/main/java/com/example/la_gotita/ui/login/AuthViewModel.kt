package com.example.la_gotita.ui.login

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.la_gotita.data.model.User
import com.example.la_gotita.data.model.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// SharedPreferences Constants
private const val PREFS_NAME = "LaGotitaAppPrefs"
private const val KEY_BIOMETRIC_ENABLED_FOR_USER_PREFIX = "biometric_enabled_for_"
private const val KEY_LAST_BIOMETRIC_CANDIDATE_USER_ID = "last_biometric_candidate_user_id" // NUEVA CLAVE
private const val TAG_AUTH_VIEW_MODEL = "AuthViewModel"

data class LoginUiState(
    val isLoading: Boolean = false,
    val loginError: String? = null,
    val firebaseUser: FirebaseUser? = null, // Usuario de Firebase (con sesión activa)
    val userAppRole: UserRole? = null
    // pseudoLoggedInUserId eliminado
)

interface AuthBackend {
    val currentUser: FirebaseUser?
    fun addAuthStateListener(listener: (FirebaseUser?) -> Unit)
    fun signIn(email: String, password: String, onResult: (Result<FirebaseUser>) -> Unit)
    fun signOut()
}

class RealAuthBackend : AuthBackend {
    private val delegate = FirebaseAuth.getInstance()
    override val currentUser: FirebaseUser? get() = delegate.currentUser
    override fun addAuthStateListener(listener: (FirebaseUser?) -> Unit) {
        delegate.addAuthStateListener { auth -> listener(auth.currentUser) }
    }
    override fun signIn(email: String, password: String, onResult: (Result<FirebaseUser>) -> Unit) {
        delegate.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = task.result?.user
                    if (user != null) onResult(Result.success(user)) else onResult(Result.failure(IllegalStateException("FirebaseUser null")))
                } else {
                    onResult(Result.failure(task.exception ?: Exception("Login error")))
                }
            }
    }
    override fun signOut() { delegate.signOut() }
}

interface UserDataSource {
    fun fetchUser(userId: String, onSuccess: (DocumentSnapshot?) -> Unit, onFailure: (Exception) -> Unit)
    fun fetchInvitationByEmail(emailLower: String, onSuccess: (DocumentSnapshot?) -> Unit, onFailure: (Exception) -> Unit)
    fun createUserDoc(userId: String, user: com.example.la_gotita.data.model.User, onSuccess: () -> Unit, onFailure: (Exception) -> Unit)
}

class RealUserDataSource : UserDataSource {
    private val firestore = FirebaseFirestore.getInstance()
    override fun fetchUser(userId: String, onSuccess: (DocumentSnapshot?) -> Unit, onFailure: (Exception) -> Unit) {
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { onSuccess(it) }
            .addOnFailureListener { onFailure(it) }
    }
    override fun fetchInvitationByEmail(emailLower: String, onSuccess: (DocumentSnapshot?) -> Unit, onFailure: (Exception) -> Unit) {
        firestore.collection("invitations").document(emailLower).get()
            .addOnSuccessListener { onSuccess(it) }
            .addOnFailureListener { onFailure(it) }
    }
    override fun createUserDoc(userId: String, user: com.example.la_gotita.data.model.User, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val map = hashMapOf(
            "name" to user.name,
            "email" to user.email,
            "active" to user.active,
            "role" to user.role
        )
        firestore.collection("users").document(userId).set(map)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }
}

interface UiMessenger { fun showMessage(context: Context, text: String) }
class RealUiMessenger : UiMessenger { override fun showMessage(context: Context, text: String) { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() } }

class AuthViewModel(
    private val authBackend: AuthBackend = RealAuthBackend(),
    private val userDataSource: UserDataSource = RealUserDataSource(),
    private val uiMessenger: UiMessenger = RealUiMessenger()
) : ViewModel() {

    private val rawFirebaseAuthForPasswordReset: FirebaseAuth = FirebaseAuth.getInstance()

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _loginUiState = MutableStateFlow(LoginUiState())
    val loginUiState: StateFlow<LoginUiState> = _loginUiState.asStateFlow()

    private val _showBiometricPrompt = MutableStateFlow(false)
    val showBiometricPrompt: StateFlow<Boolean> = _showBiometricPrompt.asStateFlow()

    private val _justLoggedInWithCredentials = MutableStateFlow(false)
    val justLoggedInWithCredentials: StateFlow<Boolean> = _justLoggedInWithCredentials.asStateFlow()

    // --- SharedPreferences Helpers ---
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun setBiometricPreference(context: Context, userId: String, enabled: Boolean) {
        Log.d(TAG_AUTH_VIEW_MODEL, "Setting biometric preference for user ${userId} to ${enabled}")
        getPrefs(context).edit().putBoolean("${KEY_BIOMETRIC_ENABLED_FOR_USER_PREFIX}${userId}", enabled).apply()
    }

    private fun isBiometricPreferenceEnabled(context: Context, userId: String): Boolean {
        val isEnabled = getPrefs(context).getBoolean("${KEY_BIOMETRIC_ENABLED_FOR_USER_PREFIX}${userId}", false)
        Log.d(TAG_AUTH_VIEW_MODEL, "Checking biometric preference for user ${userId}. Is enabled: ${isEnabled}")
        return isEnabled
    }

    private fun setLastBiometricCandidateUserId(context: Context, userId: String?) {
        Log.d(TAG_AUTH_VIEW_MODEL, "Setting last biometric candidate user ID to: ${userId}")
        getPrefs(context).edit().putString(KEY_LAST_BIOMETRIC_CANDIDATE_USER_ID, userId).apply()
    }

    private fun getLastBiometricCandidateUserId(context: Context): String? {
        val userId = getPrefs(context).getString(KEY_LAST_BIOMETRIC_CANDIDATE_USER_ID, null)
        Log.d(TAG_AUTH_VIEW_MODEL, "Getting last biometric candidate user ID: ${userId}")
        return userId
    }
    // --- End SharedPreferences Helpers ---

    init {
        Log.d(TAG_AUTH_VIEW_MODEL, "AuthViewModel initialized.")
        authBackend.addAuthStateListener { currentFbUser ->
            viewModelScope.launch {
                Log.d(TAG_AUTH_VIEW_MODEL, "AuthStateListener: currentFbUser = ${currentFbUser?.uid}")
                if (currentFbUser == null) {
                    _loginUiState.update { it.copy(firebaseUser = null, userAppRole = null, isLoading = false) }
                    Log.d(TAG_AUTH_VIEW_MODEL, "AuthStateListener: Firebase User is null. UI state reset for user session.")
                } else {
                    if (_loginUiState.value.firebaseUser?.uid != currentFbUser.uid || _loginUiState.value.userAppRole == null) {
                        _loginUiState.update { it.copy(firebaseUser = currentFbUser, isLoading = false, loginError = null) }
                        Log.d(TAG_AUTH_VIEW_MODEL, "AuthStateListener: User ${currentFbUser.uid} exists. State updated. Role needs fetch/check.")
                    }
                }
            }
        }
    }

    // Nueva función para que Splash decida flujo biométrico
    fun shouldAttemptBiometricOnLaunch(context: Context): Boolean {
        // Mostrar biometría si existe un candidato y la preferencia está activa, independientemente de si hay sesión Firebase
        return getLastBiometricCandidateUserId(context)?.let { isBiometricPreferenceEnabled(context, it) } == true
    }

    fun attemptInitialBiometricLogin(context: Context) {
        viewModelScope.launch {
            // No abortar por tener Firebase user: permitimos usar biometría como compuerta de acceso
            val candidateUserId = getLastBiometricCandidateUserId(context)
            Log.d(TAG_AUTH_VIEW_MODEL, "attemptInitialBiometricLogin: Candidate User ID from Prefs: ${candidateUserId}")
            if (candidateUserId != null && isBiometricPreferenceEnabled(context, candidateUserId)) {
                Log.d(TAG_AUTH_VIEW_MODEL, "attemptInitialBiometricLogin: Conditions met for user ${candidateUserId}. Showing biometric prompt.")
                _loginUiState.update { it.copy(isLoading = true, loginError = null) }
                _showBiometricPrompt.value = true
            } else {
                Log.d(TAG_AUTH_VIEW_MODEL, "attemptInitialBiometricLogin: Conditions NOT met. No biometric prompt. Candidate: ${candidateUserId}")
                _loginUiState.update { it.copy(isLoading = false) }
                _showBiometricPrompt.value = false
            }
        }
    }

    fun onBiometricAuthenticationSuccess(context: Context) {
        viewModelScope.launch {
            _showBiometricPrompt.value = false
            val candidateUserId = getLastBiometricCandidateUserId(context) // Usar siempre el último candidato guardado
            Log.d(TAG_AUTH_VIEW_MODEL, "onBiometricAuthenticationSuccess: Last Biometric Candidate User ID: ${candidateUserId}")

            if (candidateUserId != null) {
                // Procedemos a obtener los detalles de este usuario.
                // firebaseUser en loginUiState seguirá siendo null si no había sesión de Firebase.
                fetchUserDetails(
                    userIdToFetch = candidateUserId,
                    context = context,
                    isBiometricAuthSuccess = true
                )
            } else {
                Log.e(TAG_AUTH_VIEW_MODEL, "onBiometricAuthenticationSuccess: Last Biometric Candidate User ID is null. Cannot proceed.")
                _loginUiState.update {
                    it.copy(isLoading = false, loginError = "Error biométrico: No se pudo identificar al usuario.")
                }
            }
        }
    }

    fun onBiometricAuthenticationCancelledOrError() { // No necesita context ahora
        Log.d(TAG_AUTH_VIEW_MODEL, "onBiometricAuthenticationCancelledOrError called.")
        _showBiometricPrompt.value = false
        _loginUiState.update { it.copy(isLoading = false) } // Solo resetear isLoading
    }

    fun toggleBiometricPreference(context: Context, userId: String, enabled: Boolean) {
        setBiometricPreference(context, userId, enabled)
        val message: String
        if (enabled) {
            setLastBiometricCandidateUserId(context, userId)
            message = "Biometría habilitada para inicio rápido."
            Log.i(TAG_AUTH_VIEW_MODEL, "Biometric preference ENABLED for user ${userId}. Set as last candidate.")
        } else {
            message = "Biometría deshabilitada."
            if (userId == getLastBiometricCandidateUserId(context)) {
                setLastBiometricCandidateUserId(context, null)
                Log.i(TAG_AUTH_VIEW_MODEL, "Biometric preference DISABLED for user ${userId}. Cleared as last candidate.")
            } else {
                Log.i(TAG_AUTH_VIEW_MODEL, "Biometric preference DISABLED for user ${userId} (was not the last candidate).")
            }
            _showBiometricPrompt.value = false
        }
        uiMessenger.showMessage(context, message)
    }

    fun loginUser(context: Context) {
        val currentEmail = _email.value.trim()
        val currentPassword = _password.value
        Log.d(TAG_AUTH_VIEW_MODEL, "loginUser called with email: ${currentEmail}")

        if (currentEmail.isBlank() || currentPassword.isBlank()) {
            _loginUiState.update { it.copy(loginError = "Correo y contraseña no pueden estar vacíos.") }
            return
        }
        _loginUiState.update { it.copy(isLoading = true, loginError = null, userAppRole = null) }
        authBackend.signIn(currentEmail, currentPassword) { result ->
            if (result.isSuccess) {
                val firebaseUser = result.getOrNull()
                if (firebaseUser != null) {
                    Log.d(TAG_AUTH_VIEW_MODEL, "Firebase Sign-In successful for user ${firebaseUser.uid}. Fetching details.")
                    fetchUserDetails(
                        userIdToFetch = firebaseUser.uid,
                        context = context,
                        isBiometricAuthSuccess = false,
                        isCredentialLogin = true
                    )
                } else {
                    _loginUiState.update { it.copy(isLoading = false, loginError = "Error de login inesperado.") }
                }
            } else {
                val ex = result.exceptionOrNull()
                Log.e(TAG_AUTH_VIEW_MODEL, "Login failed: ${ex?.message}", ex)
                val errorMessage = when (ex?.message?.contains("INVALID_LOGIN_CREDENTIALS")) {
                    true -> "Credenciales incorrectas. Verifica tu correo y contraseña."
                    else -> ex?.message ?: "Error de login desconocido."
                }
                _loginUiState.update { it.copy(isLoading = false, loginError = errorMessage) }
            }
        }
    }

    private fun fetchUserDetails(
        userIdToFetch: String,
        context: Context,
        isBiometricAuthSuccess: Boolean = false,
        isCredentialLogin: Boolean = false
    ) {
        Log.d(TAG_AUTH_VIEW_MODEL, "fetchUserDetails for userId: ${userIdToFetch}, isBiometric: ${isBiometricAuthSuccess}, isCredential: ${isCredentialLogin}")
        _loginUiState.update { it.copy(isLoading = true, loginError = null) }
        userDataSource.fetchUser(userIdToFetch,
            onSuccess = { document ->
                val currentFbUser = authBackend.currentUser
                if (document != null && document.exists()) {
                    val appUser = document.toObject(User::class.java)
                    val role = appUser?.role?.let { roleName ->
                        try { UserRole.valueOf(roleName.uppercase()) } catch (_: IllegalArgumentException) { null }
                    }
                    Log.d(TAG_AUTH_VIEW_MODEL, "fetchUserDetails success for ${userIdToFetch}. Role: ${role}. Current Firebase user: ${currentFbUser?.uid}")

                    // Verificar si el usuario está activo
                    if (appUser?.active != true) {
                        Log.w(TAG_AUTH_VIEW_MODEL, "Usuario ${userIdToFetch} está desactivado. Bloqueando acceso.")
                        // Cerrar sesión de Firebase si el usuario está desactivado
                        authBackend.signOut()
                        val errorMessage = "Tu cuenta ha sido desactivada. Contacta al administrador."
                        _loginUiState.update { it.copy(isLoading = false, loginError = errorMessage, userAppRole = null, firebaseUser = null) }
                        if (isCredentialLogin) _justLoggedInWithCredentials.value = true
                        return@fetchUser
                    }

                    if (isCredentialLogin && currentFbUser?.uid == userIdToFetch) {
                        toggleBiometricPreference(context, userIdToFetch, true)
                        _justLoggedInWithCredentials.value = true
                    } else if (isBiometricAuthSuccess) {
                        setLastBiometricCandidateUserId(context, userIdToFetch)
                        Log.i(TAG_AUTH_VIEW_MODEL, "Biometric auth success: ${userIdToFetch} (re)confirmed as last candidate.")
                    }
                    _loginUiState.update {
                        it.copy(isLoading = false, userAppRole = role, firebaseUser = currentFbUser)
                    }
                } else {
                    // Documento no existe: si venimos de login por credenciales, intentamos crear doc desde invitación
                    val currentFbUser = authBackend.currentUser
                    val emailLower = currentFbUser?.email?.trim()?.lowercase()
                    if (isCredentialLogin && currentFbUser != null && !emailLower.isNullOrBlank()) {
                        Log.w(TAG_AUTH_VIEW_MODEL, "User doc not found. Trying to create from invitation for email: ${emailLower}")
                        userDataSource.fetchInvitationByEmail(emailLower,
                            onSuccess = { inviteDoc ->
                                val roleFromInvite = inviteDoc?.getString("role") ?: UserRole.EMPLOYEE.name
                                val nameFromInvite = inviteDoc?.getString("name")
                                val newUser = User(
                                    uid = currentFbUser.uid,
                                    name = nameFromInvite ?: currentFbUser.displayName,
                                    email = currentFbUser.email,
                                    active = true,
                                    role = roleFromInvite
                                )
                                userDataSource.createUserDoc(currentFbUser.uid, newUser,
                                    onSuccess = {
                                        Log.i(TAG_AUTH_VIEW_MODEL, "User doc created from invitation for ${currentFbUser.uid}")
                                        // Vuelve a cargar detalles para tener role correcto
                                        fetchUserDetails(currentFbUser.uid, context, isBiometricAuthSuccess = false, isCredentialLogin = false)
                                        toggleBiometricPreference(context, currentFbUser.uid, true)
                                        _justLoggedInWithCredentials.value = true
                                    },
                                    onFailure = { ex ->
                                        Log.e(TAG_AUTH_VIEW_MODEL, "Failed to create user doc: ${ex.message}", ex)
                                        _loginUiState.update { it.copy(isLoading = false, loginError = "No se pudo inicializar el usuario.", userAppRole = null) }
                                        _justLoggedInWithCredentials.value = true
                                    }
                                )
                            },
                            onFailure = { ex ->
                                Log.e(TAG_AUTH_VIEW_MODEL, "Failed to fetch invitation: ${ex.message}", ex)
                                _loginUiState.update { it.copy(isLoading = false, loginError = "Detalles del usuario no encontrados.", userAppRole = null) }
                                if (isCredentialLogin) _justLoggedInWithCredentials.value = true
                            }
                        )
                    } else {
                        val errorMessage = if (isBiometricAuthSuccess) {
                            "Usuario no encontrado con biometría. Por favor, inicie sesión."
                        } else {
                            "Detalles del usuario no encontrados."
                        }
                        _loginUiState.update { it.copy(isLoading = false, loginError = errorMessage, userAppRole = null) }
                        if (isCredentialLogin) _justLoggedInWithCredentials.value = true
                    }
                }
            },
            onFailure = { exception ->
                Log.e(TAG_AUTH_VIEW_MODEL, "fetchUserDetails failed for ${userIdToFetch}: ${exception.message}", exception)
                val errorMessage = if (isBiometricAuthSuccess) {
                    "Error al verificar usuario (biometría). Inicie sesión."
                } else {
                    "Error al obtener detalles: ${exception.message}"
                }
                _loginUiState.update { it.copy(isLoading = false, loginError = errorMessage, userAppRole = null) }
                if (isCredentialLogin) _justLoggedInWithCredentials.value = true
            }
        )
    }

    fun logoutUser(context: Context) {
        viewModelScope.launch {
            authBackend.signOut()
            // No borrar el último candidato biométrico: se debe mantener para login biométrico posterior
            _loginUiState.value = LoginUiState()
            _showBiometricPrompt.value = false
            _justLoggedInWithCredentials.value = false
        }
    }

    fun onEmailChange(newEmail: String) { _email.value = newEmail }
    fun onPasswordChange(newPassword: String) { _password.value = newPassword }

    fun sendPasswordResetEmail(email: String, context: Context) {
        val trimmed = email.trim()
        if (trimmed.isBlank()) {
            uiMessenger.showMessage(context, "Por favor, ingresa un correo electrónico.")
            return
        }
        Log.d(TAG_AUTH_VIEW_MODEL, "sendPasswordResetEmail called for: $trimmed")
        _loginUiState.update { it.copy(isLoading = true, loginError = null) }
        rawFirebaseAuthForPasswordReset.sendPasswordResetEmail(trimmed)
            .addOnCompleteListener { task ->
                _loginUiState.update { it.copy(isLoading = false) }
                if (task.isSuccessful) {
                    uiMessenger.showMessage(context, "Enlace enviado a $trimmed")
                } else {
                    val msg = task.exception?.message ?: "No se pudo enviar el correo."
                    uiMessenger.showMessage(context, "Error: $msg")
                }
            }
    }

    fun resetJustLoggedInWithCredentialsFlag() {
        Log.d(TAG_AUTH_VIEW_MODEL, "resetJustLoggedInWithCredentialsFlag")
        _justLoggedInWithCredentials.value = false
    }
}
