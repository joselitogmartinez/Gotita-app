package com.example.la_gotita.data.model

import java.util.Date

enum class MovementType { ENTRY, EXIT }
enum class MovementSource { MANUAL, SALE }

data class InventoryMovement(
    val id: String,
    val productId: String,
    val productNameSnapshot: String = "",
    val type: MovementType,
    val quantity: Int,
    val description: String = "",
    val userName: String = "",
    val date: Date = Date(),
    val source: MovementSource = MovementSource.MANUAL,
    val availableAfter: Int = 0, // saldo disponible después del movimiento
    val isVoided: Boolean = false, // indica si el movimiento está anulado
    val voidReason: String = "", // justificación de la anulación
    val voidedAt: Date? = null, // fecha de anulación
    val voidedBy: String = "" // usuario que anuló
)
