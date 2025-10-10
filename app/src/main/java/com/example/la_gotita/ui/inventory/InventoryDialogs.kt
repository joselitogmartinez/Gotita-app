package com.example.la_gotita.ui.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.la_gotita.data.model.InventoryItem
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*


@Composable
fun AddProductDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Double, Double, String) -> Unit
) {
    var productName by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var costPrice by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Agregar Nuevo Producto",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Ingresa el nombre del producto, la descripción y los precios. Podrás agregar stock posteriormente.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = productName,
                    onValueChange = { productName = it.uppercase() },
                    label = { Text("Nombre del Producto") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Ej: AGUA PURA 500ML") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Breve descripción del producto") },
                    maxLines = 3
                )
                // Mostrar primero el campo Precio de Costo (ahora arriba del Precio Unitario)
                OutlinedTextField(
                    value = costPrice,
                    onValueChange = {
                        if (it.matches(Regex("^\\d*\\.?\\d*$")) || it.isEmpty()) {
                            costPrice = it
                        }
                    },
                    label = { Text("Precio de Costo (Q)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Ej: 3.00") },
                    prefix = { Text("Q ") },
                    singleLine = true
                )
                // Luego el campo Precio Unitario (Precio de Venta)
                OutlinedTextField(
                    value = price,
                    onValueChange = {
                        if (it.matches(Regex("^\\d*\\.?\\d*$")) || it.isEmpty()) {
                            price = it
                        }
                    },
                    label = { Text("Precio Unitario (Q)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Ej: 5.50") },
                    prefix = { Text("Q ") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val priceDouble = price.toDoubleOrNull() ?: 0.0
                    val costDouble = costPrice.toDoubleOrNull() ?: 0.0
                    if (productName.isNotBlank()) {
                        onConfirm(productName.trim(), priceDouble, costDouble, description.trim())
                    }
                },
                enabled = productName.isNotBlank() && (price.toDoubleOrNull() ?: -1.0) >= 0.0
            ) {
                Text("Agregar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun InventoryDetailDialog(
    item: InventoryItem,
    onDismiss: () -> Unit,
    onEdit: (InventoryItem) -> Unit,
    onDelete: (String) -> Unit
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var editedName by remember { mutableStateOf(item.productName) }
    var editedPrice by remember { mutableStateOf(item.pricePerUnit.toString()) }
    var editedCost by remember { mutableStateOf(item.costPrice.toString()) }
    var editedDescription by remember { mutableStateOf(item.description) }

    val numberFormat = NumberFormat.getCurrencyInstance(Locale("es", "GT"))
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Inventory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Detalles del Producto")
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Información principal
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (isEditing) {
                            OutlinedTextField(
                                value = editedName,
                                onValueChange = { editedName = it },
                                label = { Text("Nombre del Producto") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = editedDescription,
                                onValueChange = { editedDescription = it },
                                label = { Text("Descripción") },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 3
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = editedCost,
                                onValueChange = {
                                    if (it.matches(Regex("^\\d*\\.?\\d*$")) || it.isEmpty()) {
                                        editedCost = it
                                    }
                                },
                                label = { Text("Precio de Costo (Q)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth(),
                                prefix = { Text("Q ") },
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = editedPrice,
                                onValueChange = {
                                    if (it.matches(Regex("^\\d*\\.?\\d*$")) || it.isEmpty()) {
                                        editedPrice = it
                                    }
                                },
                                label = { Text("Precio Unitario (Q)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth(),
                                prefix = { Text("Q ") },
                                singleLine = true
                            )
                        } else {
                            // Sección: Producto y descripción
                            SectionContainer(title = "Información del Producto") {
                                LabelBlock(label = "Producto", value = item.productName, prominent = true)
                                if (item.description.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    LabelBlock(label = "Descripción", value = item.description)
                                }
                            }

                            // Sección: Cantidad y Estado (dos columnas)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SectionContainer(modifier = Modifier.weight(1f), title = "Cantidad") {
                                    // Evitamos repetir "Cantidad" dentro del bloque ya que el título de la sección lo muestra
                                    LabelBlock(label = "Cantidad", value = "${item.quantity} unidades", showLabel = false)
                                }
                                SectionContainer(modifier = Modifier.weight(1f), title = "Estado") {
                                    // Evitamos repetir "Estado" dentro del bloque
                                    LabelBlock(label = "Estado", value = if (item.isAvailable) "Disponible" else "No disponible", showLabel = false)
                                }
                            }

                            // Sección: Precios (dos columnas) y total
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SectionContainer(modifier = Modifier.weight(1f), title = "Precio de Costo") {
                                    // El título de la sección ya muestra "Precio de Costo"
                                    LabelBlock(
                                        label = "Precio de Costo",
                                        value = if (item.costPrice > 0) numberFormat.format(item.costPrice) else "Q0.00",
                                        showLabel = false
                                    )
                                }
                                SectionContainer(modifier = Modifier.weight(1f), title = "Precio Unitario") {
                                    LabelBlock(
                                        label = "Precio Unitario",
                                        value = if (item.pricePerUnit > 0) numberFormat.format(item.pricePerUnit) else "Q0.00",
                                        showLabel = false
                                    )
                                }
                            }
                            SectionContainer(title = "Valor Total") {
                                // Valor Total ya está como título de la sección
                                LabelBlock(
                                    label = "Valor Total",
                                    value = if (item.quantity > 0 && item.pricePerUnit > 0) numberFormat.format(item.quantity * item.pricePerUnit) else "Q0.00",
                                    prominent = true,
                                    showLabel = false
                                )
                            }
                        }
                    }
                }

                // Información de registro
                Card {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            "Información de Registro",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        DetailItem("Fecha de Entrada", dateFormat.format(item.entryDate))
                        if (item.registeredByName.isNotEmpty()) {
                            DetailItem("Registrado por", item.registeredByName)
                        }
                        if (item.notes.isNotEmpty()) {
                            DetailItem("Notas", item.notes)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Eliminar (izquierda)
                IconButton(onClick = { showDeleteConfirmation = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                }
                // Modificar o Guardar (centro)
                if (isEditing) {
                    IconButton(
                        onClick = {
                            val priceDouble = editedPrice.toDoubleOrNull() ?: 0.0
                            val costDouble = editedCost.toDoubleOrNull() ?: 0.0
                            onEdit(item.copy(productName = editedName.trim(), pricePerUnit = priceDouble, costPrice = costDouble, description = editedDescription.trim()))
                        },
                        enabled = editedName.isNotBlank() && (editedPrice.toDoubleOrNull() ?: -1.0) >= 0.0
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "Guardar", tint = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    IconButton(onClick = { isEditing = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Modificar", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                // Cerrar (derecha)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        dismissButton = null
    )

    // Confirmación de eliminación
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Confirmar Eliminación") },
            text = { Text("¿Estás seguro de que quieres eliminar este producto del inventario?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(item.id)
                        showDeleteConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}

// Modern section container with subtle background and rounded corners
@Composable
private fun SectionContainer(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
        }
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                content()
            }
        }
    }
}

// Label above value block used inside sections
@Composable
private fun LabelBlock(
    label: String,
    value: String,
    prominent: Boolean = false,
    showLabel: Boolean = true
) {
    if (showLabel) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
    }
    Text(
        text = value,
        style = if (prominent) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}
