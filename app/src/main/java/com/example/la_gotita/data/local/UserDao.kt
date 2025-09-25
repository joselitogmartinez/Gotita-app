package com.example.la_gotita.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.la_gotita.data.model.User
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Update
    suspend fun updateUser(user: User)

    @Query("SELECT * FROM users WHERE uid = :uid")
    fun getUserByUid(uid: String): Flow<User?>

    // Podríamos añadir más consultas si son necesarias, por ejemplo,
    // para obtener todos los usuarios o usuarios por rol, aunque
    // para la autenticación inicial, getUserByUid es la más crucial.
}