package com.example.la_gotita.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.la_gotita.data.model.Client
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClient(client: Client)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllClients(clients: List<Client>)

    @Update
    suspend fun updateClient(client: Client)

    @Delete
    suspend fun deleteClient(client: Client)

    @Query("SELECT * FROM clients WHERE clientId = :clientId")
    fun getClientById(clientId: String): Flow<Client?>

    @Query("SELECT * FROM clients ORDER BY name ASC")
    fun getAllClients(): Flow<List<Client>>

    @Query("DELETE FROM clients")
    suspend fun clearAllClients()
}