// filepath: c:\Users\Auxiliar Compras CC\AndroidStudioProjects\LaGotita\app\src\main\java\com\example\la_gotita\data\repository\InventoryRepository.kt
package com.example.la_gotita.data.repository

import com.example.la_gotita.data.model.InventoryItem
import kotlinx.coroutines.delay
import java.util.*

/**
 * Repositorio en memoria temporal. Sustituir por implementación real (Room / Firestore) más adelante.
 */
class InventoryRepository {
    // Almacenamiento en memoria
    private val items = mutableListOf<InventoryItem>()

    suspend fun getAll(): List<InventoryItem> {
        // Simulación de carga
        delay(150)
        return items.toList()
    }

    suspend fun addProduct(
        productName: String,
        pricePerUnit: Double = 0.0,
        registeredById: String = "",
        registeredByName: String = ""
    ): InventoryItem {
        delay(50)
        val item = InventoryItem(
            id = UUID.randomUUID().toString(),
            productId = UUID.randomUUID().toString(),
            productName = productName,
            quantity = 0,
            pricePerUnit = pricePerUnit,
            batchNumber = generateBatchCode(productName),
            registeredBy = registeredById,
            registeredByName = registeredByName,
            entryDate = Date()
        )
        items += item
        return item
    }

    suspend fun addStock(
        productId: String,
        quantity: Int,
        pricePerUnit: Double,
        batchNumber: String?,
        notes: String
    ): InventoryItem? {
        delay(50)
        val existingItem = items.find { it.productId == productId }
        return if (existingItem != null) {
            val updatedItem = existingItem.copy(
                quantity = existingItem.quantity + quantity,
                pricePerUnit = if (pricePerUnit > 0) pricePerUnit else existingItem.pricePerUnit,
                batchNumber = batchNumber ?: existingItem.batchNumber,
                notes = if (notes.isNotBlank()) notes else existingItem.notes
            )
            val index = items.indexOfFirst { it.id == existingItem.id }
            if (index != -1) {
                items[index] = updatedItem
            }
            updatedItem
        } else null
    }

    suspend fun update(item: InventoryItem): InventoryItem {
        delay(50)
        val index = items.indexOfFirst { it.id == item.id }
        if (index != -1) {
            items[index] = item
        }
        return item
    }

    suspend fun delete(itemId: String): Boolean {
        delay(50)
        return items.removeIf { it.id == itemId }
    }

    private fun generateBatchCode(productName: String): String {
        val prefix = productName.take(3).uppercase()
        val timestamp = System.currentTimeMillis().toString().takeLast(6)
        return "$prefix-$timestamp"
    }
}
