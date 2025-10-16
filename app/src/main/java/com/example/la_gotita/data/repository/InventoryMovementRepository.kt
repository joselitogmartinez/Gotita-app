package com.example.la_gotita.data.repository

import com.example.la_gotita.data.model.InventoryMovement
import com.example.la_gotita.data.model.FirebaseInventoryMovement
import com.example.la_gotita.data.model.toFirebaseMovement
import com.example.la_gotita.data.model.MovementSource
import com.example.la_gotita.data.model.MovementType
import com.example.la_gotita.data.remote.FirebaseRepository
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Repositorio de movimientos que usa Firebase Firestore
 */
object InventoryMovementRepository : FirebaseRepository() {

    suspend fun getAllForProduct(productId: String): List<InventoryMovement> {
        return try {
            val snapshot = firestore.collection(COLLECTION_MOVEMENTS)
                .whereEqualTo("productId", productId)
                .orderBy("date", Query.Direction.DESCENDING)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                doc.toObject(FirebaseInventoryMovement::class.java)?.toInventoryMovement()
            }
        } catch (e: Exception) {
            throw Exception("Error al cargar movimientos: ${e.message}")
        }
    }

    suspend fun getByProductAndMonth(productId: String, monthIndex0: Int, year: Int): List<InventoryMovement> {
        return try {
            val cal = Calendar.getInstance()
            cal.set(year, monthIndex0, 1, 0, 0, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val startDate = cal.time

            cal.add(Calendar.MONTH, 1)
            cal.add(Calendar.MILLISECOND, -1)
            val endDate = cal.time

            val snapshot = firestore.collection(COLLECTION_MOVEMENTS)
                .whereEqualTo("productId", productId)
                .whereGreaterThanOrEqualTo("date", startDate)
                .whereLessThanOrEqualTo("date", endDate)
                .orderBy("date", Query.Direction.DESCENDING)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                doc.toObject(FirebaseInventoryMovement::class.java)?.toInventoryMovement()
            }
        } catch (e: Exception) {
            throw Exception("Error al cargar movimientos del mes: ${e.message}")
        }
    }

    suspend fun addEntry(
        productId: String,
        productName: String,
        quantity: Int,
        description: String,
        userName: String = "",
        availableAfter: Int = 0
    ): InventoryMovement {
        return try {
            val movement = InventoryMovement(
                id = UUID.randomUUID().toString(),
                productId = productId,
                productNameSnapshot = productName,
                type = MovementType.ENTRY,
                quantity = quantity,
                description = description,
                userName = userName,
                date = Date(),
                source = MovementSource.MANUAL,
                availableAfter = availableAfter
            )

            firestore.collection(COLLECTION_MOVEMENTS)
                .document(movement.id)
                .set(movement.toFirebaseMovement())
                .await()

            movement
        } catch (e: Exception) {
            throw Exception("Error al registrar entrada: ${e.message}")
        }
    }

    suspend fun addExit(
        productId: String,
        productName: String,
        quantity: Int,
        description: String,
        userName: String = "",
        source: MovementSource = MovementSource.MANUAL,
        availableAfter: Int = 0
    ): InventoryMovement {
        return try {
            val movement = InventoryMovement(
                id = UUID.randomUUID().toString(),
                productId = productId,
                productNameSnapshot = productName,
                type = MovementType.EXIT,
                quantity = quantity,
                description = description,
                userName = userName,
                date = Date(),
                source = source,
                availableAfter = availableAfter
            )

            firestore.collection(COLLECTION_MOVEMENTS)
                .document(movement.id)
                .set(movement.toFirebaseMovement())
                .await()

            movement
        } catch (e: Exception) {
            throw Exception("Error al registrar salida: ${e.message}")
        }
    }

    suspend fun exportMonthlyCsv(productId: String, monthIndex0: Int, year: Int): String {
        return try {
            val list = getByProductAndMonth(productId, monthIndex0, year)
            val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val header = "Fecha,Tipo,Cantidad,Descripción,Usuario,Fuente\n"
            val rows = list.joinToString("\n") { m ->
                val tipo = if (m.type == MovementType.ENTRY) "Entrada" else "Salida"
                val fuente = when (m.source) {
                    MovementSource.MANUAL -> "Manual"
                    MovementSource.SALE -> "Venta"
                }
                "${df.format(m.date)},${tipo},${m.quantity},\"${m.description.replace("\"", "''")}\",${m.userName},${fuente}"
            }
            header + rows
        } catch (e: Exception) {
            throw Exception("Error al exportar CSV: ${e.message}")
        }
    }

    suspend fun hasMovements(productId: String): Boolean {
        return try {
            val snapshot = firestore.collection(COLLECTION_MOVEMENTS)
                .whereEqualTo("productId", productId)
                .limit(1)
                .get()
                .await()

            !snapshot.isEmpty
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateMovement(
        movementId: String,
        newQuantity: Int,
        newDescription: String,
        newAvailableAfter: Int
    ) {
        try {
            val updates = hashMapOf<String, Any>(
                "quantity" to newQuantity,
                "description" to newDescription,
                "availableAfter" to newAvailableAfter
            )

            firestore.collection(COLLECTION_MOVEMENTS)
                .document(movementId)
                .update(updates)
                .await()
        } catch (e: Exception) {
            throw Exception("Error al actualizar movimiento: ${e.message}")
        }
    }

    suspend fun voidMovement(
        movementId: String,
        voidReason: String,
        voidedBy: String,
        newAvailableAfter: Int,
        newDescription: String,
        originalQuantity: Int
    ) {
        try {
            val updates = hashMapOf<String, Any>(
                "isVoided" to true,
                "voidReason" to voidReason,
                "voidedAt" to Date(),
                "voidedBy" to voidedBy,
                "availableAfter" to newAvailableAfter,
                "description" to newDescription,
                "quantity" to 0 // Poner la cantidad en 0
            )

            firestore.collection(COLLECTION_MOVEMENTS)
                .document(movementId)
                .update(updates)
                .await()
        } catch (e: Exception) {
            throw Exception("Error al anular movimiento: ${e.message}")
        }
    }
}
