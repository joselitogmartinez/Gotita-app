package com.example.la_gotita.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// androidx.room.TypeConverters será necesario si se añade el TypeConverter aquí
// import androidx.room.TypeConverters 

@Entity(tableName = "sales")
data class Sale(
    @PrimaryKey val saleId: String = "",
    val clientId: String, // Podría ser una clave foránea en el futuro
    val clientNameSnapshot: String? = null,
    val employeeId: String, // Podría ser una clave foránea en el futuro
    val employeeNameSnapshot: String? = null,
    val items: List<SaleItem> = emptyList(), // Requerirá un TypeConverter
    val totalAmount: Double = 0.0,
    val discountAmount: Double = 0.0,
    val finalAmount: Double = 0.0,
    val paymentMethod: String? = null,
    val saleDate: Long = System.currentTimeMillis(),
    val notes: String? = null,
    val isSynced: Boolean = false
)

// SaleItem no es una @Entity por sí misma si se almacena como parte de Sale
// usando un TypeConverter. Es solo una data class regular.
data class SaleItem(
    val productId: String = "",
    val productNameSnapshot: String = "",
    val quantity: Int = 0,
    val priceAtSale: Double = 0.0,
    val subtotal: Double = 0.0
)