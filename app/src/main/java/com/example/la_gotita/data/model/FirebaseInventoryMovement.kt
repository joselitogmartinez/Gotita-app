package com.example.la_gotita.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Modelo para movimientos en Firestore
 */
data class FirebaseInventoryMovement(
    @DocumentId
    val id: String = "",
    val productId: String = "",
    val productNameSnapshot: String = "",
    val type: String = "", // "ENTRY" o "EXIT"
    val quantity: Int = 0,
    val description: String = "",
    val userName: String = "",
    val source: String = "", // "MANUAL" o "SALE"
    val availableAfter: Int = 0,
    @ServerTimestamp
    val date: Date? = null,
    @ServerTimestamp
    val createdAt: Date? = null,
    val isVoided: Boolean = false,
    val voidReason: String = "",
    val voidedAt: Date? = null,
    val voidedBy: String = ""
) {
    // Constructor sin argumentos requerido por Firestore
    constructor() : this("", "", "", "", 0, "", "", "", 0, null, null, false, "", null, "")

    // Convertir a modelo local
    fun toInventoryMovement(): InventoryMovement {
        return InventoryMovement(
            id = id,
            productId = productId,
            productNameSnapshot = productNameSnapshot,
            type = when (type) {
                "ENTRY" -> MovementType.ENTRY
                "EXIT" -> MovementType.EXIT
                else -> MovementType.ENTRY
            },
            quantity = quantity,
            description = description,
            userName = userName,
            date = date ?: Date(),
            source = when (source) {
                "MANUAL" -> MovementSource.MANUAL
                "SALE" -> MovementSource.SALE
                else -> MovementSource.MANUAL
            },
            availableAfter = availableAfter,
            isVoided = isVoided,
            voidReason = voidReason,
            voidedAt = voidedAt,
            voidedBy = voidedBy
        )
    }
}

/**
 * Extensión para convertir de modelo local a Firebase
 */
fun InventoryMovement.toFirebaseMovement(): FirebaseInventoryMovement {
    return FirebaseInventoryMovement(
        id = id,
        productId = productId,
        productNameSnapshot = productNameSnapshot,
        type = type.name,
        quantity = quantity,
        description = description,
        userName = userName,
        source = source.name,
        availableAfter = availableAfter,
        date = date,
        isVoided = isVoided,
        voidReason = voidReason,
        voidedAt = voidedAt,
        voidedBy = voidedBy
    )
}
