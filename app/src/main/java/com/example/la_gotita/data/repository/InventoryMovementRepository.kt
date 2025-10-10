package com.example.la_gotita.data.repository

import com.example.la_gotita.data.model.InventoryMovement
import com.example.la_gotita.data.model.MovementSource
import com.example.la_gotita.data.model.MovementType
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

object InventoryMovementRepository {
    private val movementsByProduct = mutableMapOf<String, MutableList<InventoryMovement>>()

    suspend fun getAllForProduct(productId: String): List<InventoryMovement> {
        delay(5)
        return movementsByProduct[productId]?.sortedByDescending { it.date } ?: emptyList()
    }

    suspend fun getByProductAndMonth(productId: String, monthIndex0: Int, year: Int): List<InventoryMovement> {
        delay(5)
        val cal = Calendar.getInstance()
        return getAllForProduct(productId).filter { m ->
            cal.time = m.date
            cal.get(Calendar.MONTH) == monthIndex0 && cal.get(Calendar.YEAR) == year
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
        movementsByProduct.getOrPut(productId) { mutableListOf() }.add(0, movement)
        return movement
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
        movementsByProduct.getOrPut(productId) { mutableListOf() }.add(0, movement)
        return movement
    }

    suspend fun exportMonthlyCsv(productId: String, monthIndex0: Int, year: Int): String {
        val list = getByProductAndMonth(productId, monthIndex0, year)
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val header = "Fecha,Tipo,Cantidad,Descripción,Usuario,Fuente\n"
        val rows = list.joinToString("\n") { m ->
            val tipo = if (m.type == MovementType.ENTRY) "Entrada" else "Salida"
            val fuente = when (m.source) { MovementSource.MANUAL -> "Manual"; MovementSource.SALE -> "Venta" }
            "${df.format(m.date)},${tipo},${m.quantity},\"${m.description.replace("\"", "''")}\",${m.userName},${fuente}"
        }
        return header + rows
    }
}
