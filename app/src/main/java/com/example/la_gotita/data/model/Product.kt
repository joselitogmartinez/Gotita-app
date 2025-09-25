package com.example.la_gotita.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class Product(
    @PrimaryKey val productId: String = "",
    val name: String = "",
    val description: String? = null,
    val price: Double = 0.0,
    var stock: Int = 0,
    val imageUrl: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)