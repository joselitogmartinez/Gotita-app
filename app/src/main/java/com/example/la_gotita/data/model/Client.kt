package com.example.la_gotita.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clients")
data class Client(
    @PrimaryKey val clientId: String = "",
    val name: String = "",
    val address: String? = null,
    val phone: String? = null,
    val registrationDate: Long = System.currentTimeMillis(),
    val lastPurchaseDate: Long? = null
)