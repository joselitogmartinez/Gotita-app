package com.example.la_gotita.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey val uid: String = "",
    val name: String? = null,
    val email: String? = null,
    val active: Boolean = true,
    val role: String = UserRole.EMPLOYEE.name
)

enum class UserRole {
    ADMIN,
    EMPLOYEE
}