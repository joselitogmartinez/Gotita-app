package com.example.la_gotita.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.la_gotita.data.model.Client
import com.example.la_gotita.data.model.Product
import com.example.la_gotita.data.model.Sale
import com.example.la_gotita.data.model.User

@Database(
    entities = [User::class, Product::class, Client::class, Sale::class],
    version = 2, // Subido a 2 por cambios en User (email, active)
    exportSchema = false // Puedes ponerlo a true si quieres exportar el esquema para control de versiones.
)
@TypeConverters(SaleItemTypeConverter::class) // Registra nuestro TypeConverter
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun productDao(): ProductDao
    abstract fun clientDao(): ClientDao
    abstract fun saleDao(): SaleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "la_gotita_database" // Nombre del archivo de la base de datos
                )
                // Aquí se pueden añadir migraciones si se cambian versiones
                // .addMigrations(...)
                .fallbackToDestructiveMigration() // Ojo: Borra y recrea si no hay migración. Útil en desarrollo.
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}