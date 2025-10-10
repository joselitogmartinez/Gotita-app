package com.example.la_gotita.data.model

import java.util.Date

data class InventoryItem(
    val id: String = "",
    val productId: String = "", // Si se vincula a tabla products, sino queda vacío
    val productName: String = "",
    val quantity: Int = 0,
    val pricePerUnit: Double = 0.0, // En quetzales
    val costPrice: Double = 0.0, // Precio de costo en quetzales (nuevo campo)
    val description: String = "", // Descripción del producto (nuevo campo)
    val isAvailable: Boolean = true,
    val entryDate: Date = Date(),
    val registeredBy: String = "", // UID del usuario que registró
    val registeredByName: String = "", // Nombre del usuario que registró
    val exitDate: Date? = null,
    val soldBy: String? = null, // UID del usuario que vendió
    val soldByName: String? = null, // Nombre del usuario que vendió
    val batchNumber: String = "", // Número de lote para agrupación
    val notes: String = ""
)

data class InventoryBatch(
    val batchNumber: String = "",
    val productName: String = "",
    val totalQuantity: Int = 0,
    val pricePerUnit: Double = 0.0,
    val items: List<InventoryItem> = emptyList(),
    val entryDate: Date = Date(),
    val registeredBy: String = "",
    val registeredByName: String = ""
)
