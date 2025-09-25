package com.example.la_gotita.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.la_gotita.data.model.Sale
import kotlinx.coroutines.flow.Flow

@Dao
interface SaleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(sale: Sale)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSales(sales: List<Sale>)

    @Update
    suspend fun updateSale(sale: Sale)

    @Delete
    suspend fun deleteSale(sale: Sale)

    @Query("SELECT * FROM sales WHERE saleId = :saleId")
    fun getSaleById(saleId: String): Flow<Sale?>

    @Query("SELECT * FROM sales ORDER BY saleDate DESC")
    fun getAllSales(): Flow<List<Sale>>

    // Ejemplo: Obtener ventas no sincronizadas
    @Query("SELECT * FROM sales WHERE isSynced = 0 ORDER BY saleDate ASC")
    fun getUnsyncedSales(): Flow<List<Sale>>

    @Query("DELETE FROM sales")
    suspend fun clearAllSales()

    // Podríamos añadir más consultas específicas, como ventas por cliente,
    // ventas por empleado, ventas en un rango de fechas, etc., según se necesite.
}