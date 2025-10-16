// filepath: c:\Users\Auxiliar Compras CC\AndroidStudioProjects\LaGotita\app\src\main\java\com\example\la_gotita\ui\inventory\InventoryViewModel.kt
package com.example.la_gotita.ui.inventory

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.la_gotita.data.model.InventoryItem
import com.example.la_gotita.data.repository.InventoryMovementRepository
import com.example.la_gotita.data.repository.InventoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Estado de la UI para la pantalla de Inventario.
 */
data class InventoryUiState(
    val isLoading: Boolean = false,
    val items: List<InventoryItem> = emptyList(),
    val filteredItems: List<InventoryItem> = emptyList(),
    val searchQuery: String = "",
    val error: String? = null
)

class InventoryViewModel(
    private val repository: InventoryRepository = InventoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState

    fun loadInventory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val data = repository.getAll()
                _uiState.update { current ->
                    val newState = current.copy(isLoading = false, items = data)
                    newState.copy(filteredItems = applyFilters(newState))
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Error al cargar inventario") }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { current ->
            val updated = current.copy(searchQuery = query)
            updated.copy(filteredItems = applyFilters(updated))
        }
    }

    fun addProduct(
        productName: String,
        pricePerUnit: Double,
        costPrice: Double,
        description: String,
        imageUri: String,
        context: Context
    ) {
        viewModelScope.launch {
            try {
                repository.addProduct(productName, pricePerUnit, costPrice, description, imageUri = imageUri)
                Toast.makeText(context, "Producto agregado", Toast.LENGTH_SHORT).show()
                loadInventory()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Error al agregar producto") }
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun addStockToProduct(
        productId: String,
        quantity: Int,
        pricePerUnit: Double,
        batchNumber: String?,
        notes: String,
        context: Context
    ) {
        viewModelScope.launch {
            try {
                repository.addStock(productId, quantity, pricePerUnit, batchNumber, notes)
                Toast.makeText(context, "Stock agregado", Toast.LENGTH_SHORT).show()
                loadInventory()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Error al agregar stock") }
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun updateInventoryItem(item: InventoryItem, context: Context) {
        viewModelScope.launch {
            try {
                repository.update(item)
                Toast.makeText(context, "Producto actualizado", Toast.LENGTH_SHORT).show()
                loadInventory()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Error al actualizar") }
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun updateProduct(updatedItem: InventoryItem) {
        viewModelScope.launch {
            try {
                repository.update(updatedItem)
                loadInventory()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Error al actualizar producto") }
            }
        }
    }

    fun deleteInventoryItem(itemId: String, context: Context) {
        viewModelScope.launch {
            try {
                repository.delete(itemId)
                Toast.makeText(context, "Producto eliminado", Toast.LENGTH_SHORT).show()
                loadInventory()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Error al eliminar") }
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun deleteProduct(productId: String, context: Context) {
        viewModelScope.launch {
            try {
                // Resolver item por id interno o por productId
                val item = _uiState.value.items.find { it.id == productId || it.productId == productId }
                val actualProductId = item?.productId ?: productId

                // Usar la eliminación atómica de Firebase que verifica movimientos y elimina en una transacción
                val deleted = repository.deleteProductIfNoMovements(actualProductId)

                if (deleted) {
                    Toast.makeText(context, "Producto eliminado exitosamente", Toast.LENGTH_SHORT).show()
                    loadInventory()
                } else {
                    Toast.makeText(context, "No se puede eliminar: existen movimientos (entradas/salidas) asociados al producto", Toast.LENGTH_LONG).show()
                }
             } catch (e: Exception) {
                 _uiState.update { it.copy(error = e.message ?: "Error al eliminar producto") }
                 Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
             }
         }
     }

     suspend fun hasProductMovements(productId: String): Boolean {
        return try {
            // Resolver item por id interno o por productId
            val item = _uiState.value.items.find { it.id == productId || it.productId == productId }
            val actualProductId = item?.productId ?: productId


            InventoryMovementRepository.hasMovements(actualProductId)
        } catch (e: Exception) {
            // En caso de error, asumir que tiene movimientos para prevenir eliminación
            true
        }
     }

    fun clearError() { _uiState.update { it.copy(error = null) } }

    private fun applyFilters(state: InventoryUiState): List<InventoryItem> {
        return state.items.asSequence()
            .filter { item ->
                val q = state.searchQuery.trim().lowercase()
                if (q.isEmpty()) true else item.productName.lowercase().contains(q) || item.batchNumber.lowercase().contains(q)
            }
            .sortedBy { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(it.entryDate) }
            .toList()
    }
}
