package com.example.la_gotita.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Modelo para productos en Firestore
 */
data class FirebaseInventoryItem(
    @DocumentId
    val id: String = "",
    val productId: String = "",
    val productName: String = "",
    val quantity: Int = 0,
    val pricePerUnit: Double = 0.0,
    val costPrice: Double = 0.0,
    val description: String = "",
    val batchNumber: String = "",
    val registeredBy: String = "",
    val registeredByName: String = "",
    val entryDate: String = "",
    val isAvailable: Boolean = true,
    val notes: String = "",
    val imageUri: String = "",
    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
) {
    // Constructor sin argumentos requerido por Firestore
    constructor() : this("", "", "", 0, 0.0, 0.0, "", "", "", "", "", true, "", "", null, null)

    // Convertir a modelo local
    fun toInventoryItem(): InventoryItem {
        return InventoryItem(
            id = id,
            productId = productId,
            productName = productName,
            quantity = quantity,
            pricePerUnit = pricePerUnit,
            costPrice = costPrice,
            description = description,
            batchNumber = batchNumber,
            registeredBy = registeredBy,
            registeredByName = registeredByName,
            entryDate = entryDate,
            isAvailable = isAvailable,
            notes = notes,
            imageUri = imageUri
        )
    }
}

/**
 * Extensión para convertir de modelo local a Firebase
 */
fun InventoryItem.toFirebaseItem(): FirebaseInventoryItem {
    return FirebaseInventoryItem(
        id = id,
        productId = productId,
        productName = productName,
        quantity = quantity,
        pricePerUnit = pricePerUnit,
        costPrice = costPrice,
        description = description,
        batchNumber = batchNumber,
        registeredBy = registeredBy,
        registeredByName = registeredByName,
        entryDate = entryDate,
        isAvailable = isAvailable,
        notes = notes,
        imageUri = imageUri
    )
}
