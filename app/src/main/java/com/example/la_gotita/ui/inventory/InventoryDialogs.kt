package com.example.la_gotita.ui.inventory

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.la_gotita.data.model.InventoryItem
import java.text.NumberFormat
import java.util.*
import android.graphics.BitmapFactory
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.io.InputStream
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
// Nuevos imports para Firebase y Coil
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import coil.compose.AsyncImage


@Composable
fun AddProductDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Double, Double, String, String) -> Unit
) {
    var productName by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var costPrice by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    // Estados para manejo de imagen
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var uploadedImageUrl by remember { mutableStateOf("") }
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf(0f) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Launcher para seleccionar imagen
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            isUploading = true
            uploadProgress = 0f

            // Subir imagen inmediatamente a Firebase Storage
            scope.launch {
                try {
                    // Verificar que el usuario esté autenticado
                    val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                    if (currentUser == null) {
                        throw Exception("Usuario no autenticado. Por favor, inicia sesión nuevamente.")
                    }

                    val storage = Firebase.storage
                    val timestamp = System.currentTimeMillis()
                    val fileName = "product_${timestamp}.jpg"
                    val ref = storage.reference.child("product_images/$fileName")

                    // Obtener los bytes de la imagen
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes()
                    inputStream?.close()

                    if (bytes != null) {
                        val uploadTask = ref.putBytes(bytes)
                        uploadTask.addOnProgressListener { taskSnapshot ->
                            val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount)
                            uploadProgress = progress.toFloat() / 100f
                        }.addOnSuccessListener {
                            // Subida exitosa
                        }.addOnFailureListener { exception ->
                            // Error en la subida
                            android.util.Log.e("AddProductDialog", "Error al subir imagen: ${exception.message}")
                            selectedImageUri = null
                            uploadedImageUrl = ""
                        }

                        uploadTask.await()
                        val downloadUrl = ref.downloadUrl.await()
                        uploadedImageUrl = downloadUrl.toString()
                    } else {
                        throw Exception("No se pudo leer la imagen")
                    }
                } catch (e: Exception) {
                    // En caso de error, mostrar mensaje y resetear estados
                    android.util.Log.e("AddProductDialog", "Error completo: ${e.message}", e)
                    android.widget.Toast.makeText(context, "Error al subir imagen: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    selectedImageUri = null
                    uploadedImageUrl = ""
                } finally {
                    isUploading = false
                }
            }
        }
    }

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

                // Sección de imagen del producto
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                if (!isUploading) {
                                    imagePickerLauncher.launch("image/*")
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            isUploading -> {
                                CircularProgressIndicator(
                                    progress = { uploadProgress },
                                    strokeWidth = 4.dp
                                )
                            }
                            uploadedImageUrl.isNotEmpty() -> {
                                AsyncImage(
                                    model = uploadedImageUrl,
                                    contentDescription = "Imagen del producto",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            selectedImageUri != null -> {
                                AsyncImage(
                                    model = selectedImageUri,
                                    contentDescription = "Imagen seleccionada",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            else -> {
                                Icon(
                                    Icons.Filled.AddAPhoto,
                                    contentDescription = "Seleccionar imagen",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        when {
                            isUploading -> "Subiendo imagen... ${(uploadProgress * 100).toInt()}%"
                            uploadedImageUrl.isNotEmpty() -> "Imagen subida correctamente"
                            else -> "Toca para ${if (selectedImageUri == null) "agregar" else "cambiar"} imagen"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            isUploading -> MaterialTheme.colorScheme.primary
                            uploadedImageUrl.isNotEmpty() -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

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
                        // Pasar la URL de descarga de Firebase (no la URI local)
                        onConfirm(productName.trim(), priceDouble, costDouble, description.trim(), uploadedImageUrl)
                    }
                },
                enabled = productName.isNotBlank() &&
                         (price.toDoubleOrNull() ?: -1.0) >= 0.0 &&
                         !isUploading // Deshabilitar mientras se sube la imagen
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
    onDelete: (String) -> Unit,
    hasMovements: Boolean = false // Nuevo parámetro para verificar si tiene movimientos
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var editedName by remember { mutableStateOf(item.productName) }
    var editedPrice by remember { mutableStateOf(item.pricePerUnit.toString()) }
    var editedCost by remember { mutableStateOf(item.costPrice.toString()) }
    var editedDescription by remember { mutableStateOf(item.description) }

    // Estado para imagen (modo edición)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedLocalImage by remember { mutableStateOf<Uri?>(null) }
    var uploadedImageUrl by remember { mutableStateOf<String?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf(0f) }

    // Launcher para seleccionar imagen y subir a Firebase
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedLocalImage = uri
            isUploading = true
            uploadProgress = 0f
            scope.launch {
                try {
                    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val storage = Firebase.storage
                    val productKey = if (item.productId.isNotEmpty()) item.productId else item.id.ifEmpty { UUID.randomUUID().toString() }
                    val ref = storage.getReference("inventory/products/$productKey/main")
                    val metadata = StorageMetadata.Builder().setContentType(mime).build()
                    val task = ref.putFile(uri, metadata)
                    task.addOnProgressListener { snap ->
                        val t = snap.totalByteCount.takeIf { it > 0 } ?: 1L
                        uploadProgress = snap.bytesTransferred.toFloat() / t.toFloat()
                    }
                    task.await()
                    uploadedImageUrl = ref.downloadUrl.await().toString()
                } catch (_: Exception) {
                    // Silencioso; podríamos agregar manejo de error de UI si se desea
                } finally {
                    isUploading = false
                }
            }
        }
    }

    val numberFormat = NumberFormat.getCurrencyInstance(Locale("es", "GT"))

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
                            // Sección de imagen del producto (modo edición)
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { imagePickerLauncher.launch("image/*") },
                                    contentAlignment = Alignment.Center
                                ) {
                                    when {
                                        isUploading -> {
                                            CircularProgressIndicator(progress = { uploadProgress }, strokeWidth = 4.dp)
                                        }
                                        selectedLocalImage != null -> {
                                            AsyncImage(
                                                model = selectedLocalImage,
                                                contentDescription = "Imagen seleccionada",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                        uploadedImageUrl?.isNotEmpty() == true -> {
                                            AsyncImage(
                                                model = uploadedImageUrl,
                                                contentDescription = "Imagen subida",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                        item.imageUri.isNotEmpty() -> {
                                            AsyncImage(
                                                model = item.imageUri,
                                                contentDescription = "Imagen actual",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                        else -> {
                                            Icon(
                                                Icons.Filled.CameraAlt,
                                                contentDescription = "Agregar imagen",
                                                modifier = Modifier.size(32.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    if (item.imageUri.isEmpty() && uploadedImageUrl.isNullOrEmpty()) "Toca para agregar imagen" else "Toca para cambiar imagen",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

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
                            // Sección de imagen del producto (modo visualización)
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (item.imageUri.isNotEmpty()) {
                                        AsyncImage(
                                            model = item.imageUri,
                                            contentDescription = "Imagen del producto",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            Icons.Filled.Image,
                                            contentDescription = "Imagen del producto",
                                            modifier = Modifier.size(32.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            // Sección: Producto y descripción (sin título "Información del Producto")
                            LabelBlock(label = "Producto", value = item.productName, prominent = true)
                            if (item.description.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                LabelBlock(label = "Descripción", value = item.description)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Sección: Cantidad y Estado (dos columnas)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SectionContainer(modifier = Modifier.weight(1f), title = "Cantidad") {
                                    LabelBlock(label = "Cantidad", value = "${item.quantity} unidades", showLabel = false)
                                }
                                SectionContainer(modifier = Modifier.weight(1f), title = "Estado") {
                                    LabelBlock(label = "Estado", value = if (item.isAvailable) "Disponible" else "No disponible", showLabel = false)
                                }
                            }

                            // Sección: Precios (dos columnas) y total
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SectionContainer(modifier = Modifier.weight(1f), title = "Precio de Costo") {
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
                        DetailItem("Fecha de Entrada", item.entryDate)
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
                // Eliminar (izquierda) - Solo habilitado si no tiene movimientos
                IconButton(
                    onClick = {
                        if (!hasMovements) {
                            showDeleteConfirmation = true
                        }
                    },
                    enabled = !hasMovements
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Eliminar",
                        tint = if (hasMovements) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.error
                    )
                }
                // Modificar o Guardar/Cancelar (centro)
                Row {
                    if (isEditing) {
                        // Cancelar edición
                        IconButton(onClick = {
                            isEditing = false
                            // Resetear valores editados
                            editedName = item.productName
                            editedPrice = item.pricePerUnit.toString()
                            editedCost = item.costPrice.toString()
                            editedDescription = item.description
                            selectedLocalImage = null
                            uploadedImageUrl = null
                            isUploading = false
                            uploadProgress = 0f
                        }) {
                            Icon(Icons.Filled.Cancel, contentDescription = "Cancelar", tint = MaterialTheme.colorScheme.error)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        // Guardar cambios
                        IconButton(
                            onClick = {
                                val priceDouble = editedPrice.toDoubleOrNull() ?: 0.0
                                val costDouble = editedCost.toDoubleOrNull() ?: 0.0
                                val newImage = uploadedImageUrl ?: item.imageUri
                                onEdit(item.copy(productName = editedName.trim(), pricePerUnit = priceDouble, costPrice = costDouble, description = editedDescription.trim(), imageUri = newImage))
                                isEditing = false
                            },
                            enabled = editedName.isNotBlank() && (editedPrice.toDoubleOrNull() ?: -1.0) >= 0.0 && !isUploading
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = "Guardar", tint = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        IconButton(onClick = { isEditing = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Modificar", tint = MaterialTheme.colorScheme.primary)
                        }
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
            text = {
                Column {
                    Text("¿Estás seguro de que quieres eliminar este producto del inventario?")
                    if (hasMovements) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Este producto tiene movimientos registrados y no puede ser eliminado.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(item.id)
                        showDeleteConfirmation = false
                    },
                    enabled = !hasMovements,
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
