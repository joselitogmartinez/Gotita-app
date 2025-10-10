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
    val availableAfter: Int = 0 // saldo disponible después del movimiento
)
