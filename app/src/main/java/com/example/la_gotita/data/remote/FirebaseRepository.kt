package com.example.la_gotita.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

/**
 * Repositorio base para operaciones Firebase
 */
abstract class FirebaseRepository {
    protected val firestore: FirebaseFirestore = Firebase.firestore

    companion object {
        const val COLLECTION_PRODUCTS = "products"
        const val COLLECTION_MOVEMENTS = "movements"
        const val COLLECTION_USERS = "users"
    }
}
