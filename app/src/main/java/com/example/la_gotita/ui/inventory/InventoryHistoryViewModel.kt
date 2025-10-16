package com.example.la_gotita.ui.inventory

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.la_gotita.data.model.InventoryMovement
import com.example.la_gotita.data.model.MovementSource
import com.example.la_gotita.data.model.MovementType
import com.example.la_gotita.data.repository.InventoryRepository
import com.example.la_gotita.data.repository.InventoryMovementRepository
import com.example.la_gotita.utils.PdfGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

// Data class para resumen mensual
data class MonthSummary(
    val monthIndex0: Int,
    val entries: Int,
    val exits: Int,
    val total: Int,
    val available: Int,
    val hasStock: Boolean
)

// Estado de UI para Historial de un producto
data class InventoryHistoryUiState(
    val isLoading: Boolean = false,
    val productId: String = "",
    val productName: String = "",
    val productQuantity: Int = 0,
    val productPrice: Double = 0.0, // Precio de venta para cálculos financieros
    val monthIndex0: Int = Calendar.getInstance().get(Calendar.MONTH),
    val year: Int = Calendar.getInstance().get(Calendar.YEAR),
    val typeFilter: MovementType? = null, // null = Todos
    val showAllMonths: Boolean = false, // true cuando se selecciona "Todos"
    val movements: List<InventoryMovement> = emptyList(),
    val monthlyMovements: List<InventoryMovement> = emptyList(), // movimientos sin filtrar del mes seleccionado
    val monthlySummaries: List<MonthSummary> = emptyList(), // resúmenes por mes para vista "Todos"
    val monthlyAvailableStock: Int = 0, // stock disponible del mes seleccionado
    val error: String? = null
)

class InventoryHistoryViewModel(
    private val inventoryRepo: InventoryRepository = InventoryRepository,
    private val movementRepo: InventoryMovementRepository = InventoryMovementRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InventoryHistoryUiState())
    val uiState: StateFlow<InventoryHistoryUiState> = _uiState

    fun load(productId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val product = inventoryRepo.getByProductId(productId)
                val productName = product?.productName ?: ""
                val productQty = product?.quantity ?: 0
                val productPrice = product?.pricePerUnit ?: 0.0

                if (_uiState.value.showAllMonths) {
                    loadAllMonthsSummary(productId, productName, productQty, productPrice)
                } else {
                    val moves = movementRepo.getByProductAndMonth(productId, _uiState.value.monthIndex0, _uiState.value.year)
                    val monthlyStock = calculateMonthlyAvailableStock(productId, _uiState.value.monthIndex0, _uiState.value.year)
                    val summaries = computeMonthlySummariesForYear(productId, _uiState.value.year)
                    _uiState.update { current ->
                        val filtered = applyFilter(moves, current.typeFilter)
                        current.copy(
                            isLoading = false,
                            productId = productId,
                            productName = productName,
                            productQuantity = productQty,
                            productPrice = productPrice,
                            movements = filtered,
                            monthlyMovements = moves,
                            monthlyAvailableStock = monthlyStock,
                            monthlySummaries = summaries
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Error al cargar historial") }
            }
        }
    }

    private suspend fun loadAllMonthsSummary(productId: String, productName: String, productQty: Int, productPrice: Double) {
        val currentYear = _uiState.value.year
        val summaries = computeMonthlySummariesForYear(productId, currentYear)

        _uiState.update { current ->
            current.copy(
                isLoading = false,
                productId = productId,
                productName = productName,
                productQuantity = productQty,
                productPrice = productPrice,
                monthlySummaries = summaries,
                movements = emptyList(),
                monthlyMovements = emptyList()
            )
        }
    }

    private suspend fun computeMonthlySummariesForYear(productId: String, year: Int): List<MonthSummary> {
        val allMovements = movementRepo.getAllForProduct(productId)
        val summaries = mutableListOf<MonthSummary>()
        for (monthIndex in 0..11) {
            val monthMovements = allMovements.filter { movement ->
                val cal = Calendar.getInstance()
                cal.time = movement.date
                cal.get(Calendar.MONTH) == monthIndex && cal.get(Calendar.YEAR) == year
            }
            val entries = monthMovements.filter { it.type == MovementType.ENTRY }.sumOf { it.quantity }
            val exits = monthMovements.filter { it.type == MovementType.EXIT }.sumOf { it.quantity }
            val total = entries - exits
            val available = total.coerceAtLeast(0)
            summaries.add(
                MonthSummary(
                    monthIndex0 = monthIndex,
                    entries = entries,
                    exits = exits,
                    total = total,
                    available = available,
                    hasStock = available > 0
                )
            )
        }
        return summaries
    }

    private suspend fun calculateMonthlyAvailableStock(productId: String, monthIndex0: Int, year: Int): Int {
        // Calcular el stock disponible específico del mes (no acumulativo)
        val allMovements = movementRepo.getAllForProduct(productId)
        val cal = Calendar.getInstance()

        // Filtrar movimientos solo del mes especificado
        val monthMovements = allMovements.filter { movement ->
            cal.time = movement.date
            val movementYear = cal.get(Calendar.YEAR)
            val movementMonth = cal.get(Calendar.MONTH)

            movementYear == year && movementMonth == monthIndex0
        }

        val monthEntries = monthMovements.filter { it.type == MovementType.ENTRY }.sumOf { it.quantity }
        val monthExits = monthMovements.filter { it.type == MovementType.EXIT }.sumOf { it.quantity }

        // Retorna el balance del mes específico (puede ser negativo si hay más salidas que entradas)
        return monthEntries - monthExits
    }

    fun setMonth(monthIndex0: Int) {
        if (_uiState.value.showAllMonths) return // No cambiar mes en vista "Todos"
        _uiState.update { it.copy(monthIndex0 = monthIndex0) }
        reloadMovements()
    }

    fun setShowAllMonths(showAll: Boolean) {
        _uiState.update { it.copy(showAllMonths = showAll) }
        if (showAll) {
            load(_uiState.value.productId)
        } else {
            // Volver a vista mensual normal
            reloadMovements()
        }
    }

    fun setTypeFilter(filter: MovementType?) {
        if (_uiState.value.showAllMonths) return // No filtrar en vista "Todos"
        // Actualiza el filtro y recalcula la lista filtrada a partir de monthlyMovements ya cargada
        _uiState.update { current ->
            val filtered = applyFilter(current.monthlyMovements, filter)
            current.copy(typeFilter = filter, movements = filtered)
        }
        // No llamamos a reloadMovements() para evitar reconsulta que pueda introducir vacíos temporales
    }

    private fun reloadMovements() {
        val pid = _uiState.value.productId
        if (pid.isBlank()) return
        viewModelScope.launch {
            val all = movementRepo.getByProductAndMonth(pid, _uiState.value.monthIndex0, _uiState.value.year)
            val monthlyStock = calculateMonthlyAvailableStock(pid, _uiState.value.monthIndex0, _uiState.value.year)
            val summaries = computeMonthlySummariesForYear(pid, _uiState.value.year)
            _uiState.update { st ->
                st.copy(
                    monthlyMovements = all,
                    movements = applyFilter(all, st.typeFilter),
                    monthlyAvailableStock = monthlyStock,
                    monthlySummaries = summaries
                )
            }
        }
    }

    private fun applyFilter(all: List<InventoryMovement>, type: MovementType?): List<InventoryMovement> {
        return when (type) {
            null -> all
            else -> all.filter { it.type == type }
        }
    }

    fun registerEntry(quantity: Int, description: String, context: Context, authViewModel: com.example.la_gotita.ui.login.AuthViewModel) {
        val pid = _uiState.value.productId
        val pname = _uiState.value.productName
        if (pid.isBlank() || quantity <= 0) return
        val userName = authViewModel.getSavedUserName(context)
        viewModelScope.launch {
            try {
                // Primero obtener el stock actual del producto
                val currentProduct = inventoryRepo.getByProductId(pid)
                val currentStock = currentProduct?.quantity ?: 0

                // Actualizar el inventario (incrementar stock)
                inventoryRepo.addStock(pid, quantity, pricePerUnit = 0.0, batchNumber = null, notes = description)

                // Calcular el nuevo saldo después de la entrada
                val saldoNuevo = currentStock + quantity

                // Registrar el movimiento de entrada
                movementRepo.addEntry(pid, pname, quantity, description, userName, availableAfter = saldoNuevo)

                Toast.makeText(context, "Entrada registrada exitosamente", Toast.LENGTH_SHORT).show()
                load(pid)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Error al registrar entrada") }
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun registerExit(quantity: Int, description: String, context: Context, authViewModel: com.example.la_gotita.ui.login.AuthViewModel) {
        val pid = _uiState.value.productId
        val pname = _uiState.value.productName
        val available = _uiState.value.productQuantity
        if (pid.isBlank() || quantity <= 0) return
        if (available < quantity) {
            Toast.makeText(context, "Cantidad supera el stock disponible (Disponible: $available)", Toast.LENGTH_LONG).show()
            return
        }
        val userName = authViewModel.getSavedUserName(context)
        viewModelScope.launch {
            try {
                // Primero obtener el stock actual del producto
                val currentProduct = inventoryRepo.getByProductId(pid)
                val currentStock = currentProduct?.quantity ?: 0

                // Actualizar el inventario (decrementar stock)
                inventoryRepo.removeStock(pid, quantity, notes = description)

                // Calcular el nuevo saldo después de la salida
                val saldoNuevo = (currentStock - quantity).coerceAtLeast(0)

                // Registrar el movimiento de salida
                movementRepo.addExit(pid, pname, quantity, description, userName, MovementSource.MANUAL, availableAfter = saldoNuevo)

                Toast.makeText(context, "Salida registrada exitosamente", Toast.LENGTH_SHORT).show()
                load(pid)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Error al registrar salida") }
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun updateMovement(
        movementId: String,
        oldQuantity: Int,
        newQuantity: Int,
        newDescription: String,
        movementType: MovementType,
        context: Context
    ) {
        val pid = _uiState.value.productId
        val pname = _uiState.value.productName
        if (pid.isBlank() || newQuantity <= 0) return

        viewModelScope.launch {
            try {
                // Calcular la diferencia en el inventario
                val quantityDifference = newQuantity - oldQuantity

                // Obtener el stock actual del producto
                val currentProduct = inventoryRepo.getByProductId(pid)
                val currentStock = currentProduct?.quantity ?: 0

                // Actualizar el inventario según el tipo de movimiento
                if (movementType == MovementType.ENTRY) {
                    // Para entradas: si aumenta la cantidad, agregar al stock; si disminuye, quitar
                    if (quantityDifference > 0) {
                        inventoryRepo.addStock(pid, quantityDifference, pricePerUnit = 0.0, batchNumber = null, notes = newDescription)
                    } else if (quantityDifference < 0) {
                        inventoryRepo.removeStock(pid, -quantityDifference, notes = newDescription)
                    }
                } else {
                    // Para salidas: si aumenta la cantidad, quitar más del stock; si disminuye, devolver al stock
                    if (quantityDifference > 0) {
                        // Verificar que hay suficiente stock
                        if (currentStock < quantityDifference) {
                            Toast.makeText(context, "Stock insuficiente para aumentar la salida", Toast.LENGTH_LONG).show()
                            return@launch
                        }
                        inventoryRepo.removeStock(pid, quantityDifference, notes = newDescription)
                    } else if (quantityDifference < 0) {
                        inventoryRepo.addStock(pid, -quantityDifference, pricePerUnit = 0.0, batchNumber = null, notes = newDescription)
                    }
                }

                // Obtener el nuevo stock después del cambio
                val updatedProduct = inventoryRepo.getByProductId(pid)
                val newStock = updatedProduct?.quantity ?: 0

                // Actualizar el documento del movimiento en Firestore
                movementRepo.updateMovement(movementId, newQuantity, newDescription, newStock)

                Toast.makeText(context, "Movimiento actualizado exitosamente", Toast.LENGTH_SHORT).show()
                load(pid)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Error al actualizar movimiento") }
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun voidMovement(
        movementId: String,
        movementType: MovementType,
        quantity: Int,
        voidReason: String,
        context: Context,
        authViewModel: com.example.la_gotita.ui.login.AuthViewModel
    ) {
        val pid = _uiState.value.productId
        if (pid.isBlank() || voidReason.isBlank()) {
            Toast.makeText(context, "La justificación es obligatoria", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            try {
                // Obtener el stock actual del producto
                val currentProduct = inventoryRepo.getByProductId(pid)
                val currentStock = currentProduct?.quantity ?: 0

                // Revertir el movimiento: Si era SALIDA, devolver al stock; si era ENTRADA, quitar del stock
                val newStock = if (movementType == MovementType.EXIT) {
                    // Era una salida, devolver la cantidad al stock
                    inventoryRepo.addStock(pid, quantity, pricePerUnit = 0.0, batchNumber = null, notes = "Anulación: $voidReason")
                    currentStock + quantity
                } else {
                    // Era una entrada, quitar la cantidad del stock
                    if (currentStock < quantity) {
                        Toast.makeText(context, "Stock insuficiente para anular esta entrada", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    inventoryRepo.removeStock(pid, quantity, notes = "Anulación: $voidReason")
                    currentStock - quantity
                }

                // Obtener nombre de usuario
                val userName = authViewModel.getSavedUserName(context)

                // Crear la nueva descripción con justificación y unidades anuladas
                val newDescription = "Anulado: $quantity unidades - $voidReason"

                // Marcar el movimiento como anulado en Firestore con cantidad en 0 y nueva descripción
                movementRepo.voidMovement(
                    movementId = movementId,
                    voidReason = voidReason,
                    voidedBy = userName,
                    newAvailableAfter = newStock,
                    newDescription = newDescription,
                    originalQuantity = quantity
                )

                Toast.makeText(context, "Movimiento anulado exitosamente", Toast.LENGTH_SHORT).show()
                load(pid)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Error al anular movimiento") }
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    suspend fun exportMonthlyCsv(): String? {
        val pid = _uiState.value.productId
        if (pid.isBlank()) return null
        return movementRepo.exportMonthlyCsv(pid, _uiState.value.monthIndex0, _uiState.value.year)
    }

    /**
     * Genera un reporte PDF con las tablas de unidades y dinero
     */
    fun generatePdfReport(context: Context, onSuccess: (Uri) -> Unit, onError: (String) -> Unit) {
        val state = _uiState.value

        if (state.productName.isBlank() || state.monthlySummaries.isEmpty()) {
            onError("No hay datos para generar el reporte")
            return
        }

        viewModelScope.launch {
            try {
                val result = PdfGenerator.generateInventoryReport(
                    context = context,
                    productName = state.productName,
                    summaries = state.monthlySummaries,
                    productPrice = state.productPrice,
                    year = state.year
                )

                result.fold(
                    onSuccess = { uri ->
                        Toast.makeText(context, "Reporte PDF generado exitosamente", Toast.LENGTH_SHORT).show()
                        onSuccess(uri)
                    },
                    onFailure = { exception ->
                        val errorMessage = "Error al generar el PDF: ${exception.message}"
                        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                        onError(errorMessage)
                    }
                )
            } catch (e: Exception) {
                val errorMessage = "Error inesperado: ${e.message}"
                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                onError(errorMessage)
            }
        }
    }

    /**
     * Comparte el reporte PDF generado
     */
    fun sharePdfReport(context: Context, uri: Uri) {
        try {
            PdfGenerator.shareReport(context, uri)
        } catch (e: Exception) {
            Toast.makeText(context, "Error al compartir el reporte: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
