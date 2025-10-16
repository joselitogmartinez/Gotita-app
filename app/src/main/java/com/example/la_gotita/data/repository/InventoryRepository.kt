// filepath: c:\Users\Auxiliar Compras CC\AndroidStudioProjects\LaGotita\app\src\main\java\com\example\la_gotita\data\repository\InventoryRepository.kt
package com.example.la_gotita.data.repository

import android.net.Uri
import androidx.core.net.toUri
import com.google.firebase.storage.FirebaseStorage
import com.example.la_gotita.data.model.FirebaseInventoryItem
import com.example.la_gotita.data.model.InventoryItem
import com.example.la_gotita.data.model.toFirebaseItem
import com.example.la_gotita.data.remote.FirebaseRepository
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

/**
 * Repositorio de inventario que usa Firebase Firestore
 */
object InventoryRepository : FirebaseRepository() {
    private val storage = FirebaseStorage.getInstance()

    suspend fun getAll(): List<InventoryItem> {
        return try {
            val snapshot = firestore.collection(COLLECTION_PRODUCTS)
                .orderBy("createdAt")
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                doc.toObject(FirebaseInventoryItem::class.java)?.toInventoryItem()
            }
        } catch (e: Exception) {
            throw Exception("Error al cargar inventario: ${e.message}")
        }
    }

    suspend fun getById(itemId: String): InventoryItem? {
        return try {
            val doc = firestore.collection(COLLECTION_PRODUCTS)
                .document(itemId)
                .get()
                .await()

            doc.toObject(FirebaseInventoryItem::class.java)?.toInventoryItem()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getByProductId(productId: String): InventoryItem? {
        return try {
            val snapshot = firestore.collection(COLLECTION_PRODUCTS)
                .whereEqualTo("productId", productId)
                .limit(1)
                .get()
                .await()

            snapshot.documents.firstOrNull()?.toObject(FirebaseInventoryItem::class.java)?.toInventoryItem()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun addProduct(
        productName: String,
        pricePerUnit: Double = 0.0,
        costPrice: Double = 0.0,
        description: String = "",
        registeredById: String = "",
        registeredByName: String = "",
        imageUri: String = ""
    ): InventoryItem {
        return try {
            val productId = UUID.randomUUID().toString()
            val item = InventoryItem(
                id = UUID.randomUUID().toString(),
                productId = productId,
                productName = productName,
                quantity = 0,
                pricePerUnit = pricePerUnit,
                costPrice = costPrice,
                description = description,
                batchNumber = generateBatchCode(productName),
                registeredBy = registeredById,
                registeredByName = registeredByName,
                entryDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                imageUri = imageUri // Ya viene como URL de Firebase o cadena vacía
            )

            // No necesitamos subir la imagen aquí porque ya viene como URL de Firebase
            val firebaseItem = item.toFirebaseItem()

            firestore.collection(COLLECTION_PRODUCTS)
                .document(item.id)
                .set(firebaseItem)
                .await()

            item
        } catch (e: Exception) {
            throw Exception("Error al agregar producto: ${e.message}")
        }
    }

    suspend fun addStock(
        productId: String,
        quantity: Int,
        pricePerUnit: Double,
        batchNumber: String?,
        notes: String
    ): InventoryItem? {
        return try {
            // Buscar documento por productId antes de la transacción para obtener el DocumentReference
            val querySnap = firestore.collection(COLLECTION_PRODUCTS)
                .whereEqualTo("productId", productId)
                .limit(1)
                .get()
                .await()

            val doc = querySnap.documents.firstOrNull()
                ?: throw Exception("Producto no encontrado")

            // Ejecutar transacción sobre el DocumentReference
            val resultFirebase = firestore.runTransaction { transaction ->
                val ds = transaction.get(doc.reference)
                val currentItem = ds.toObject(FirebaseInventoryItem::class.java)
                    ?: throw Exception("Error al leer producto")

                val updatedQuantity = currentItem.quantity + quantity
                val updatedFields = mapOf(
                    "quantity" to updatedQuantity,
                    "pricePerUnit" to (if (pricePerUnit > 0) pricePerUnit else currentItem.pricePerUnit),
                    "batchNumber" to (batchNumber ?: currentItem.batchNumber),
                    "notes" to (if (notes.isNotBlank()) notes else currentItem.notes),
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                transaction.update(doc.reference, updatedFields)

                // Construir objeto FirebaseInventoryItem para devolver
                currentItem.copy(
                    quantity = updatedQuantity,
                    pricePerUnit = if (pricePerUnit > 0) pricePerUnit else currentItem.pricePerUnit,
                    batchNumber = batchNumber ?: currentItem.batchNumber,
                    notes = if (notes.isNotBlank()) notes else currentItem.notes
                )
            }.await()

            resultFirebase.toInventoryItem()
        } catch (e: Exception) {
            throw Exception("Error al agregar stock: ${e.message}")
        }
    }

    suspend fun removeStock(
        productId: String,
        quantity: Int,
        notes: String
    ): InventoryItem? {
        return try {
            val querySnap = firestore.collection(COLLECTION_PRODUCTS)
                .whereEqualTo("productId", productId)
                .limit(1)
                .get()
                .await()

            val doc = querySnap.documents.firstOrNull()
                ?: throw Exception("Producto no encontrado")

            val resultFirebase = firestore.runTransaction { transaction ->
                val ds = transaction.get(doc.reference)
                val currentItem = ds.toObject(FirebaseInventoryItem::class.java)
                    ?: throw Exception("Error al leer producto")

                val newQty = (currentItem.quantity - quantity).coerceAtLeast(0)
                val updatedFields = mapOf(
                    "quantity" to newQty,
                    "notes" to (if (notes.isNotBlank()) notes else currentItem.notes),
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                transaction.update(doc.reference, updatedFields)

                currentItem.copy(
                    quantity = newQty,
                    notes = if (notes.isNotBlank()) notes else currentItem.notes
                )
            }.await()

            resultFirebase.toInventoryItem()
        } catch (e: Exception) {
            throw Exception("Error al remover stock: ${e.message}")
        }
    }

    private suspend fun uploadImageToFirebaseStorage(imageUri: String): String? {
        if (!imageUri.startsWith("content://")) return imageUri // Ya es URL pública
        val uri = imageUri.toUri()
        val ref = storage.reference.child("product_images/${System.currentTimeMillis()}_${uri.lastPathSegment}")
        val uploadTask = ref.putFile(uri).await() // Explicitly await UploadTask.TaskSnapshot
        val url = ref.downloadUrl.await() // Explicitly await Uri
        return url.toString()
    }

    suspend fun update(item: InventoryItem): InventoryItem {
        return try {
            var imageUrl = item.imageUri
            if (imageUrl.isNotEmpty() && imageUrl.startsWith("content://")) {
                imageUrl = uploadImageToFirebaseStorage(imageUrl) ?: ""
            }
            val firebaseItem = item.toFirebaseItem().copy(
                imageUri = imageUrl,
                updatedAt = null
            )

            // Guardar los campos y luego actualizar el timestamp del servidor
            firestore.collection(COLLECTION_PRODUCTS)
                .document(item.id)
                .set(firebaseItem)
                .await()

            // Actualizar solo el campo updatedAt con serverTimestamp
            firestore.collection(COLLECTION_PRODUCTS)
                .document(item.id)
                .update(mapOf("updatedAt" to FieldValue.serverTimestamp()))
                .await()

            item.copy(imageUri = imageUrl)
        } catch (e: Exception) {
            throw Exception("Error al actualizar producto: ${e.message}")
        }
    }

    suspend fun delete(itemId: String): Boolean {
        return try {
            firestore.collection(COLLECTION_PRODUCTS)
                .document(itemId)
                .delete()
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Elimina un producto de forma atómica solo si no tiene movimientos asociados
     * Retorna true si se eliminó, false si existen movimientos
     */
    suspend fun deleteProductIfNoMovements(productId: String): Boolean {
        return try {
            // Verificar movimientos fuera de la transacción (consulta rápida)
            val movesSnap = firestore.collection(COLLECTION_MOVEMENTS)
                .whereEqualTo("productId", productId)
                .limit(1)
                .get()
                .await()

            if (!movesSnap.isEmpty) return false

            // Buscar el producto por productId para obtener DocumentReference
            val prodSnap = firestore.collection(COLLECTION_PRODUCTS)
                .whereEqualTo("productId", productId)
                .limit(1)
                .get()
                .await()

            val doc = prodSnap.documents.firstOrNull() ?: return false

            // Ejecutar transacción sobre el DocumentReference para eliminar
            firestore.runTransaction { transaction ->
                transaction.delete(doc.reference)
                true
            }.await()
        } catch (e: Exception) {
            throw Exception("Error al eliminar producto: ${e.message}")
        }
    }

    private fun generateBatchCode(productName: String): String {
        val prefix = productName.take(3).uppercase()
        val timestamp = System.currentTimeMillis().toString().takeLast(6)
        return "$prefix-$timestamp"
    }
}
