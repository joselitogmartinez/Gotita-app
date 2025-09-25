package com.example.la_gotita.ui.admin

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.la_gotita.data.model.User
import com.example.la_gotita.data.model.UserRole
import com.example.la_gotita.data.repository.FirestoreUsersRepository
import com.example.la_gotita.data.repository.UsersRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class UserManagementViewModel(
    private val repo: UsersRepository = FirestoreUsersRepository()
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val users: List<User> = emptyList(),
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    init {
        observeUsers()
    }

    private fun observeUsers() {
        viewModelScope.launch {
            repo.observeUsers()
                .map { list -> list.sortedWith(compareBy<User> { it.role != UserRole.ADMIN.name }.thenBy { it.name ?: it.email ?: it.uid }) }
                .collect { users ->
                    _uiState.value = UiState(loading = false, users = users)
                }
        }
    }

    fun toggleActive(user: User, context: Context) {
        viewModelScope.launch {
            try {
                val result = repo.setActive(user.uid, !user.active)
                result.fold(
                    onSuccess = {
                        // Success - no need to show message, the UI will update automatically
                    },
                    onFailure = { exception ->
                        Toast.makeText(context, exception.message ?: "Error al actualizar estado", Toast.LENGTH_LONG).show()
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(context, "Error al actualizar estado: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun changeRole(user: User, newRole: UserRole, context: Context) {
        viewModelScope.launch {
            try {
                val result = repo.setRole(user.uid, newRole)
                result.fold(
                    onSuccess = {
                        Toast.makeText(context, "Rol actualizado a ${newRole.name}", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { exception ->
                        Toast.makeText(context, exception.message ?: "Error al cambiar rol", Toast.LENGTH_LONG).show()
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(context, "Error al cambiar rol: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun sendPasswordReset(user: User, context: Context) {
        val email = user.email
        if (email.isNullOrBlank()) {
            Toast.makeText(context, "El usuario no tiene email registrado.", Toast.LENGTH_SHORT).show()
            return
        }
        _uiState.value = _uiState.value.copy(loading = true)
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                _uiState.value = _uiState.value.copy(loading = false)
                if (task.isSuccessful) {
                    Toast.makeText(context, "Correo de restablecimiento enviado a $email", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "No se pudo enviar el correo: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    fun createUser(email: String, password: String, name: String?, role: UserRole, context: Context) {
        val trimmedEmail = email.trim()
        val trimmedPassword = password.trim()

        if (trimmedEmail.isBlank()) {
            Toast.makeText(context, "Ingresa un correo válido.", Toast.LENGTH_SHORT).show()
            return
        }

        if (trimmedPassword.length < 6) {
            Toast.makeText(context, "La contraseña debe tener al menos 6 caracteres.", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(loading = true)
                val result = repo.createUser(trimmedEmail, trimmedPassword, name?.trim()?.ifBlank { null }, role)

                result.fold(
                    onSuccess = { uid ->
                        _uiState.value = _uiState.value.copy(loading = false)
                        Toast.makeText(context, "Usuario creado exitosamente", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { exception ->
                        _uiState.value = _uiState.value.copy(loading = false)
                        val errorMessage = when {
                            exception.message?.contains("email-already-in-use") == true ->
                                "El correo ya está registrado"
                            exception.message?.contains("weak-password") == true ->
                                "La contraseña es muy débil"
                            exception.message?.contains("invalid-email") == true ->
                                "El formato del correo es inválido"
                            else -> "Error al crear usuario: ${exception.message}"
                        }
                        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(loading = false)
                Toast.makeText(context, "Error inesperado: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun updateUser(user: User, newName: String?, newEmail: String?, context: Context) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(loading = true)
                val result = repo.updateUser(user.uid, newName?.trim()?.ifBlank { null }, newEmail?.trim()?.ifBlank { null })

                result.fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(loading = false)
                        Toast.makeText(context, "Usuario actualizado exitosamente", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { exception ->
                        _uiState.value = _uiState.value.copy(loading = false)
                        Toast.makeText(context, "Error al actualizar: ${exception.message}", Toast.LENGTH_LONG).show()
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(loading = false)
                Toast.makeText(context, "Error inesperado: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun updateUserEmailAndName(user: User, newEmail: String, newName: String, context: Context) {
        updateUser(user, newName, newEmail, context)
    }
}
