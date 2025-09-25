package com.example.la_gotita.data.repository

import com.example.la_gotita.data.model.User
import com.example.la_gotita.data.model.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

interface UsersRepository {
    fun observeUsers(): Flow<List<User>>
    suspend fun setRole(uid: String, role: UserRole): Result<Unit>
    suspend fun setActive(uid: String, active: Boolean): Result<Unit>
    suspend fun createUser(email: String, password: String, name: String?, role: UserRole): Result<String>
    suspend fun updateUser(uid: String, name: String?, email: String?): Result<Unit>
    suspend fun countActiveAdmins(): Int
}

class FirestoreUsersRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : UsersRepository {

    private val usersRef get() = db.collection("users")

    override fun observeUsers(): Flow<List<User>> = callbackFlow {
        val registration = usersRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val users = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(User::class.java)?.copy(uid = doc.id)
            } ?: emptyList()
            trySend(users)
        }
        awaitClose { registration.remove() }
    }

    override suspend fun setRole(uid: String, role: UserRole): Result<Unit> {
        return try {
            // Si se está cambiando un admin a employee, verificar que quede al menos uno activo
            val userDoc = usersRef.document(uid).get().await()
            val currentUser = userDoc.toObject(User::class.java)

            if (currentUser?.role == UserRole.ADMIN.name && role == UserRole.EMPLOYEE) {
                val activeAdmins = countActiveAdmins()
                if (activeAdmins <= 1) {
                    return Result.failure(Exception("No se puede cambiar el rol del último administrador"))
                }
            }

            usersRef.document(uid).update("role", role.name).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setActive(uid: String, active: Boolean): Result<Unit> {
        return try {
            // Si se está desactivando un admin, verificar que quede al menos uno activo
            if (!active) {
                val userDoc = usersRef.document(uid).get().await()
                val user = userDoc.toObject(User::class.java)
                if (user?.role == UserRole.ADMIN.name) {
                    val activeAdmins = countActiveAdmins()
                    if (activeAdmins <= 1) {
                        return Result.failure(Exception("No se puede desactivar el último administrador activo"))
                    }
                }
            }

            usersRef.document(uid).update("active", active).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createUser(email: String, password: String, name: String?, role: UserRole): Result<String> {
        return try {
            // Crear usuario en Firebase Auth
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user ?: throw Exception("Error al crear usuario en Firebase Auth")

            // Crear documento en Firestore con uid incluido como campo
            val user = User(
                uid = firebaseUser.uid,
                name = name,
                email = email,
                active = true,
                role = role.name
            )

            val userData = hashMapOf(
                "uid" to firebaseUser.uid, // Incluir uid como campo del documento
                "name" to user.name,
                "email" to user.email,
                "active" to user.active,
                "role" to user.role,
                "createdAt" to com.google.firebase.Timestamp.now()
            )

            usersRef.document(firebaseUser.uid).set(userData).await()

            Result.success(firebaseUser.uid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateUser(uid: String, name: String?, email: String?): Result<Unit> {
        return try {
            val updates = hashMapOf<String, Any?>(
                "name" to name,
                "email" to email,
                "updatedAt" to com.google.firebase.Timestamp.now()
            )
            usersRef.document(uid).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun countActiveAdmins(): Int {
        return try {
            val snapshot = usersRef
                .whereEqualTo("role", UserRole.ADMIN.name)
                .whereEqualTo("active", true)
                .get()
                .await()
            snapshot.size()
        } catch (e: Exception) {
            0
        }
    }
}
