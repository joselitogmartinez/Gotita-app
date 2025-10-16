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
private const val KEY_LAST_BIOMETRIC_CANDIDATE_USER_ID = "last_biometric_candidate_user_id"
private const val KEY_LAST_BIOMETRIC_CANDIDATE_EMAIL = "last_biometric_candidate_email"
private const val KEY_LAST_BIOMETRIC_CANDIDATE_PASSWORD = "last_biometric_candidate_password"
private const val KEY_USER_NAME = "user_name"
private const val KEY_USER_EMAIL = "user_email"
private const val TAG_AUTH_VIEW_MODEL = "AuthViewModel"

data class LoginUiState(
    val isLoading: Boolean = false,
    val loginError: String? = null,
    val firebaseUser: FirebaseUser? = null,
    val userAppRole: UserRole? = null
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
        Log.d(TAG_AUTH_VIEW_MODEL, "Setting biometric preference for user $userId to $enabled")
        getPrefs(context).edit().putBoolean("$KEY_BIOMETRIC_ENABLED_FOR_USER_PREFIX$userId", enabled).apply()
    }

    private fun isBiometricPreferenceEnabled(context: Context, userId: String): Boolean {
        val isEnabled = getPrefs(context).getBoolean("$KEY_BIOMETRIC_ENABLED_FOR_USER_PREFIX$userId", false)
        Log.d(TAG_AUTH_VIEW_MODEL, "Checking biometric preference for user $userId. Is enabled: $isEnabled")
        return isEnabled
    }

    private fun setLastBiometricCandidateUserId(context: Context, userId: String?) {
        Log.d(TAG_AUTH_VIEW_MODEL, "Setting last biometric candidate user ID to: $userId")
        getPrefs(context).edit().putString(KEY_LAST_BIOMETRIC_CANDIDATE_USER_ID, userId).apply()
    }

    private fun getLastBiometricCandidateUserId(context: Context): String? {
        val userId = getPrefs(context).getString(KEY_LAST_BIOMETRIC_CANDIDATE_USER_ID, null)
        Log.d(TAG_AUTH_VIEW_MODEL, "Getting last biometric candidate user ID: $userId")
        return userId
    }

    private fun setLastBiometricCandidateEmail(context: Context, email: String?) {
        Log.d(TAG_AUTH_VIEW_MODEL, "Setting last biometric candidate email to: $email")
        getPrefs(context).edit().putString(KEY_LAST_BIOMETRIC_CANDIDATE_EMAIL, email).apply()
    }

    private fun getLastBiometricCandidateEmail(context: Context): String? {
        val email = getPrefs(context).getString(KEY_LAST_BIOMETRIC_CANDIDATE_EMAIL, null)
        Log.d(TAG_AUTH_VIEW_MODEL, "Getting last biometric candidate email: $email")
        return email
    }

    private fun setLastBiometricCandidatePassword(context: Context, password: String?) {
        Log.d(TAG_AUTH_VIEW_MODEL, "Setting last biometric candidate password.")
        getPrefs(context).edit().putString(KEY_LAST_BIOMETRIC_CANDIDATE_PASSWORD, password).apply()
    }

    private fun getLastBiometricCandidatePassword(context: Context): String? {
        val password = getPrefs(context).getString(KEY_LAST_BIOMETRIC_CANDIDATE_PASSWORD, null)
        Log.d(TAG_AUTH_VIEW_MODEL, "Getting last biometric candidate password.")
        return password
    }

    private fun setEncryptedCredentials(context: Context, email: String, password: String) {
        val prefs = getPrefs(context)
        prefs.edit()
            .putString(KEY_LAST_BIOMETRIC_CANDIDATE_EMAIL, email)
            .putString(KEY_LAST_BIOMETRIC_CANDIDATE_PASSWORD, password)
            .apply()
        Log.d(TAG_AUTH_VIEW_MODEL, "Encrypted credentials saved.")
    }

    private fun clearEncryptedCredentials(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit()
            .remove(KEY_LAST_BIOMETRIC_CANDIDATE_EMAIL)
            .remove(KEY_LAST_BIOMETRIC_CANDIDATE_PASSWORD)
            .apply()
        Log.d(TAG_AUTH_VIEW_MODEL, "Encrypted credentials cleared.")
    }

    private fun setUserName(context: Context, userId: String) {
        val userName = userId.split("@").firstOrNull() ?: ""
        Log.d(TAG_AUTH_VIEW_MODEL, "Setting user name for $userId to $userName")
        getPrefs(context).edit().putString(KEY_USER_NAME, userName).apply()
    }

    private fun getUserName(context: Context): String {
        val userName = getPrefs(context).getString(KEY_USER_NAME, "")
        Log.d(TAG_AUTH_VIEW_MODEL, "Getting user name: $userName")
        return userName ?: ""
    }

    private fun setUserEmail(context: Context, email: String) {
        Log.d(TAG_AUTH_VIEW_MODEL, "Setting user email to $email")
        getPrefs(context).edit().putString(KEY_USER_EMAIL, email).apply()
    }

    private fun getUserEmail(context: Context): String {
        val email = getPrefs(context).getString(KEY_USER_EMAIL, "")
        Log.d(TAG_AUTH_VIEW_MODEL, "Getting user email: $email")
        return email ?: ""
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

    fun shouldAttemptBiometricOnLaunch(context: Context): Boolean {
        return getLastBiometricCandidateUserId(context)?.let { isBiometricPreferenceEnabled(context, it) } == true
    }

    fun attemptInitialBiometricLogin(context: Context) {
        viewModelScope.launch {
            val candidateUserId = getLastBiometricCandidateUserId(context)
            Log.d(TAG_AUTH_VIEW_MODEL, "attemptInitialBiometricLogin: Candidate User ID from Prefs: $candidateUserId")
            if (candidateUserId != null && isBiometricPreferenceEnabled(context, candidateUserId)) {
                Log.d(TAG_AUTH_VIEW_MODEL, "attemptInitialBiometricLogin: Conditions met for user $candidateUserId. Showing biometric prompt.")
                _loginUiState.update { it.copy(isLoading = true, loginError = null) }
                _showBiometricPrompt.value = true
            } else {
                Log.d(TAG_AUTH_VIEW_MODEL, "attemptInitialBiometricLogin: Conditions NOT met. No biometric prompt. Candidate: $candidateUserId")
                _loginUiState.update { it.copy(isLoading = false) }
                _showBiometricPrompt.value = false
            }
        }
    }

    fun onBiometricAuthenticationSuccess(context: Context) {
        viewModelScope.launch {
            _showBiometricPrompt.value = false
            val candidateUserId = getLastBiometricCandidateUserId(context)
            val candidateEmail = getLastBiometricCandidateEmail(context)
            val candidatePassword = getLastBiometricCandidatePassword(context)

            Log.d(TAG_AUTH_VIEW_MODEL, "onBiometricAuthenticationSuccess: Last Biometric Candidate User ID: $candidateUserId")

            if (candidateUserId != null && candidateEmail != null && candidatePassword != null) {
                Log.d(TAG_AUTH_VIEW_MODEL, "Attempting Firebase sign-in with saved credentials for biometric auth")
                _loginUiState.update { it.copy(isLoading = true, loginError = null) }

                authBackend.signIn(candidateEmail, candidatePassword) { result ->
                    if (result.isSuccess) {
                        val firebaseUser = result.getOrNull()
                        if (firebaseUser != null) {
                            Log.d(TAG_AUTH_VIEW_MODEL, "Firebase Sign-In successful via biometric for user ${firebaseUser.uid}. Fetching details.")
                            fetchUserDetails(
                                userIdToFetch = firebaseUser.uid,
                                context = context,
                                isBiometricAuthSuccess = true
                            )
                        } else {
                            _loginUiState.update { it.copy(isLoading = false, loginError = "Error de login inesperado.") }
                        }
                    } else {
                        val ex = result.exceptionOrNull()
                        Log.e(TAG_AUTH_VIEW_MODEL, "Biometric login failed: ${ex?.message}", ex)
                        val errorMessage = "Error al iniciar sesión con biometría. Por favor, inicia sesión con tus credenciales."
                        _loginUiState.update { it.copy(isLoading = false, loginError = errorMessage) }
                    }
                }
            } else {
                Log.e(TAG_AUTH_VIEW_MODEL, "onBiometricAuthenticationSuccess: Missing credentials. Cannot proceed.")
                _loginUiState.update {
                    it.copy(isLoading = false, loginError = "Error biométrico: No se encontraron credenciales guardadas. Inicia sesión con tu correo y contraseña.")
                }
            }
        }
    }

    fun onBiometricAuthenticationCancelledOrError() {
        Log.d(TAG_AUTH_VIEW_MODEL, "onBiometricAuthenticationCancelledOrError called.")
        _showBiometricPrompt.value = false
        _loginUiState.update { it.copy(isLoading = false) }
    }

    fun saveUserSession(context: Context, user: FirebaseUser, appUser: User? = null) {
        val userName = appUser?.name ?: user.displayName ?: user.email?.substringBefore("@") ?: "Usuario"
        val userEmail = user.email ?: ""

        Log.d(TAG_AUTH_VIEW_MODEL, "Saving user session for ${user.uid} with name: $userName")

        getPrefs(context).edit()
            .putString(KEY_USER_NAME, userName)
            .putString(KEY_USER_EMAIL, userEmail)
            .putBoolean("is_logged_in", true)
            .apply()
    }

    fun getSavedUserName(context: Context): String {
        val savedName = getPrefs(context).getString(KEY_USER_NAME, "Usuario") ?: "Usuario"
        Log.d(TAG_AUTH_VIEW_MODEL, "Getting saved user name: $savedName")
        return savedName
    }

    fun clearUserSession(context: Context) {
        Log.d(TAG_AUTH_VIEW_MODEL, "Clearing user session")
        getPrefs(context).edit()
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_EMAIL)
            .remove("is_logged_in")
            .apply()
    }

    fun toggleBiometricPreference(context: Context, userId: String, enabled: Boolean) {
        setBiometricPreference(context, userId, enabled)
        val message: String
        if (enabled) {
            setLastBiometricCandidateUserId(context, userId)
            message = "Biometría habilitada para inicio rápido."
            Log.i(TAG_AUTH_VIEW_MODEL, "Biometric preference ENABLED for user $userId. Set as last candidate.")
        } else {
            message = "Biometría deshabilitada."
            if (userId == getLastBiometricCandidateUserId(context)) {
                setLastBiometricCandidateUserId(context, null)
                Log.i(TAG_AUTH_VIEW_MODEL, "Biometric preference DISABLED for user $userId. Cleared as last candidate.")
            } else {
                Log.i(TAG_AUTH_VIEW_MODEL, "Biometric preference DISABLED for user $userId (was not the last candidate).")
            }
            _showBiometricPrompt.value = false
        }
        uiMessenger.showMessage(context, message)
    }

    fun loginUser(context: Context) {
        val currentEmail = _email.value.trim()
        val currentPassword = _password.value
        Log.d(TAG_AUTH_VIEW_MODEL, "loginUser called with email: $currentEmail")

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
                    setEncryptedCredentials(context, currentEmail, currentPassword)
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
        Log.d(TAG_AUTH_VIEW_MODEL, "fetchUserDetails for userId: $userIdToFetch, isBiometric: $isBiometricAuthSuccess, isCredential: $isCredentialLogin")
        _loginUiState.update { it.copy(isLoading = true, loginError = null) }
        userDataSource.fetchUser(userIdToFetch,
            onSuccess = { document ->
                val currentFbUser = authBackend.currentUser
                if (document != null && document.exists()) {
                    val appUser = document.toObject(User::class.java)
                    val role = appUser?.role?.let { roleName ->
                        try { UserRole.valueOf(roleName.uppercase()) } catch (_: IllegalArgumentException) { null }
                    }
                    Log.d(TAG_AUTH_VIEW_MODEL, "fetchUserDetails success for $userIdToFetch. Role: $role. Current Firebase user: ${currentFbUser?.uid}")

                    if (appUser?.active != true) {
                        Log.w(TAG_AUTH_VIEW_MODEL, "Usuario $userIdToFetch está desactivado. Bloqueando acceso.")
                        authBackend.signOut()
                        val errorMessage = "Tu cuenta ha sido desactivada. Contacta al administrador."
                        _loginUiState.update { it.copy(isLoading = false, loginError = errorMessage, userAppRole = null, firebaseUser = null) }
                        if (isCredentialLogin) _justLoggedInWithCredentials.value = true
                        return@fetchUser
                    }

                    if (isBiometricAuthSuccess) {
                        val userName = appUser?.name ?: appUser?.email?.substringBefore("@") ?: "Usuario"
                        val userEmail = appUser?.email ?: ""

                        Log.d(TAG_AUTH_VIEW_MODEL, "Saving biometric user session for $userIdToFetch with name: $userName")

                        getPrefs(context).edit()
                            .putString(KEY_USER_NAME, userName)
                            .putString(KEY_USER_EMAIL, userEmail)
                            .putBoolean("is_logged_in", true)
                            .apply()
                    } else if (currentFbUser != null) {
                        saveUserSession(context, currentFbUser, appUser)
                    }

                    if (isCredentialLogin && currentFbUser?.uid == userIdToFetch) {
                        toggleBiometricPreference(context, userIdToFetch, true)
                        _justLoggedInWithCredentials.value = true
                    } else if (isBiometricAuthSuccess) {
                        setLastBiometricCandidateUserId(context, userIdToFetch)
                        Log.i(TAG_AUTH_VIEW_MODEL, "Biometric auth success: $userIdToFetch (re)confirmed as last candidate.")
                    }
                    _loginUiState.update {
                        it.copy(isLoading = false, userAppRole = role, firebaseUser = currentFbUser)
                    }
                } else {
                    val currentFbUser = authBackend.currentUser
                    val emailLower = currentFbUser?.email?.trim()?.lowercase()
                    if (isCredentialLogin && currentFbUser != null && !emailLower.isNullOrBlank()) {
                        Log.w(TAG_AUTH_VIEW_MODEL, "User doc not found. Trying to create from invitation for email: $emailLower")
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
                Log.e(TAG_AUTH_VIEW_MODEL, "fetchUserDetails failed for $userIdToFetch: ${exception.message}", exception)
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
            clearUserSession(context)
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

    // Nueva función para verificar y refrescar custom claims
    fun checkAndRefreshAdminClaims() {
        val currentUser = authBackend.currentUser
        if (currentUser != null) {
            Log.d(TAG_AUTH_VIEW_MODEL, "Verificando custom claims para usuario: ${currentUser.uid}")

            // Forzar renovación del token para obtener claims actualizados
            currentUser.getIdToken(true)
                .addOnSuccessListener { result ->
                    val isAdmin = result.claims["admin"] == true
                    Log.d(TAG_AUTH_VIEW_MODEL, "Custom claims verificados - isAdmin: $isAdmin")
                    Log.d(TAG_AUTH_VIEW_MODEL, "Todos los claims: ${result.claims}")

                    // Actualizar el estado si el usuario es admin pero el role no está reflejado
                    if (isAdmin && _loginUiState.value.userAppRole != UserRole.ADMIN) {
                        Log.i(TAG_AUTH_VIEW_MODEL, "Usuario tiene claim admin=true, actualizando role a ADMIN")
                        _loginUiState.update {
                            it.copy(userAppRole = UserRole.ADMIN)
                        }
                    } else if (!isAdmin && _loginUiState.value.userAppRole == UserRole.ADMIN) {
                        Log.w(TAG_AUTH_VIEW_MODEL, "Usuario NO tiene claim admin=true, bajando role a EMPLOYEE")
                        _loginUiState.update {
                            it.copy(userAppRole = UserRole.EMPLOYEE)
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG_AUTH_VIEW_MODEL, "Error al verificar custom claims: ${exception.message}", exception)
                }
        } else {
            Log.w(TAG_AUTH_VIEW_MODEL, "No hay usuario autenticado para verificar claims")
        }
    }

    // Función para forzar actualización del token después de que se asignen claims
    fun forceTokenRefresh(context: Context) {
        val currentUser = authBackend.currentUser
        if (currentUser != null) {
            Log.d(TAG_AUTH_VIEW_MODEL, "Forzando actualización del token...")
            _loginUiState.update { it.copy(isLoading = true) }

            currentUser.getIdToken(true)
                .addOnSuccessListener { result ->
                    val isAdmin = result.claims["admin"] == true
                    Log.d(TAG_AUTH_VIEW_MODEL, "Token actualizado - isAdmin: $isAdmin")

                    // Re-fetch user details para actualizar el role
                    fetchUserDetails(
                        userIdToFetch = currentUser.uid,
                        context = context,
                        isBiometricAuthSuccess = false,
                        isCredentialLogin = false
                    )

                    uiMessenger.showMessage(context, "Permisos actualizados correctamente")
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG_AUTH_VIEW_MODEL, "Error al actualizar token: ${exception.message}", exception)
                    _loginUiState.update { it.copy(isLoading = false) }
                    uiMessenger.showMessage(context, "Error al actualizar permisos")
                }
        }
    }
}
