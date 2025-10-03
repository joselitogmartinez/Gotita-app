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
    onConfirm: (String, Double) -> Unit
) {
    var productName by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }

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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Ingresa el nombre del producto y el precio unitario. Podrás agregar stock posteriormente.",
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
                    if (productName.isNotBlank()) {
                        onConfirm(productName.trim(), priceDouble)
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
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
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
                            DetailItem("Producto", item.productName)
                            DetailItem("Cantidad", "${item.quantity} unidades")
                            DetailItem("Precio Unitario", if (item.pricePerUnit > 0) numberFormat.format(item.pricePerUnit) else "Q0.00")
                            DetailItem("Valor Total", if (item.quantity > 0 && item.pricePerUnit > 0) numberFormat.format(item.quantity * item.pricePerUnit) else "Q0.00")
                            DetailItem("Estado", if (item.isAvailable) "Disponible" else "No disponible")
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
                            onEdit(item.copy(productName = editedName.trim(), pricePerUnit = priceDouble))
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
