package com.example.la_gotita.ui.inventory

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import com.example.la_gotita.data.model.InventoryItem
import com.example.la_gotita.ui.navigation.SafeBackHandler
import java.text.NumberFormat
import java.util.*
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.launch
import androidx.compose.foundation.isSystemInDarkTheme
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.InputStream
import kotlin.math.roundToInt
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    onBack: () -> Unit,
    viewModel: InventoryViewModel = viewModel()
) {
    SafeBackHandler(onBack = onBack)

    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showAddProductDialog by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<InventoryItem?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadInventory()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestión de Inventario") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadInventory() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Actualizar")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddProductDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Agregar Producto") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Resumen del inventario
            InventorySummaryCard(items = uiState.filteredItems)

            Spacer(modifier = Modifier.height(16.dp))

            // Vista de productos en lista
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.filteredItems.isEmpty()) {
                EmptyInventoryView()
            } else {
                ProductList(
                    items = uiState.filteredItems,
                    onProductClick = { item -> selectedItem = item }
                )
            }
        }
    }

    // Diálogo para agregar producto
    if (showAddProductDialog) {
        AddProductDialog(
            onDismiss = { showAddProductDialog = false },
            onConfirm = { productName, pricePerUnit ->
                viewModel.addProduct(productName.uppercase(), pricePerUnit, context)
                showAddProductDialog = false
            }
        )
    }

    // Diálogo de detalles del producto
    selectedItem?.let { item ->
        InventoryDetailDialog(
            item = item,
            onDismiss = { selectedItem = null },
            onEdit = { updatedItem ->
                viewModel.updateInventoryItem(updatedItem, context)
                selectedItem = null
            },
            onDelete = { itemId ->
                viewModel.deleteInventoryItem(itemId, context)
                selectedItem = null
            }
        )
    }

    // Mostrar errores
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            viewModel.clearError()
        }
    }
}

@Composable
private fun ProductList(
    items: List<InventoryItem>,
    onProductClick: (InventoryItem) -> Unit
) {
    // Estado auxiliar: mapa de id a Uri de imagen
    val imageUris = remember { mutableStateMapOf<String, Uri?>() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items) { item ->
            ProductListItem(
                item = item,
                imageUri = imageUris[item.id],
                onImageSelected = { uri -> imageUris[item.id] = uri },
                onClick = { onProductClick(item) }
            )
        }
    }
}

@Composable
private fun ProductListItem(
    item: InventoryItem,
    imageUri: Uri?,
    onImageSelected: (Uri?) -> Unit,
    onClick: () -> Unit
) {
    // Parámetros visuales
    val isDark = isSystemInDarkTheme()
    val swipeColor = if (isDark) Color(0xFFFFA726) else Color(0xFFF57C00)
    val priceBg = if (isDark) Color(0xFF1B5E20) else Color(0xFF2E7D32)
    val context = LocalContext.current // Corregido: obtener contexto aquí

    // Dimensiones
    val density = LocalDensity.current
    val maxRevealPx = with(density) { 96.dp.toPx() }

    // Animación del desplazamiento (reveal)
    val scope = rememberCoroutineScope()
    val reveal = remember { Animatable(0f) }

    val totalValue = item.quantity * item.pricePerUnit

    // Picker para seleccionar imagen
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onImageSelected(uri)
    }

    // Estado para la altura de la tarjeta
    var cardHeightPx by remember { mutableStateOf(0f) }

    Box(modifier = Modifier.fillMaxWidth()) {
        // Cápsula solo en el área revelada, nunca debajo de la tarjeta
        if (reveal.value > 0f && cardHeightPx > 0f) {
            val capsuleMinPx = cardHeightPx
            val capsuleWidthPx = reveal.value.coerceAtLeast(capsuleMinPx)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(with(density) { reveal.value.toDp() })
                    .align(Alignment.CenterEnd),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .width(with(density) { capsuleWidthPx.toDp() })
                        .height(with(density) { cardHeightPx.toDp() })
                        .background(color = swipeColor, shape = RoundedCornerShape(percent = 50)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Contenido (tarjeta) que se desplaza a la izquierda hasta 96dp con animación suave al soltar
        Box(
            modifier = Modifier
                .onGloballyPositioned { coords: LayoutCoordinates ->
                    cardHeightPx = coords.size.height.toFloat()
                }
                .offset { IntOffset(x = -reveal.value.roundToInt(), y = 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        val newTarget = (reveal.value + (-delta)).coerceIn(0f, maxRevealPx)
                        scope.launch { reveal.snapTo(newTarget) }
                    },
                    onDragStopped = {
                        val threshold = maxRevealPx * 0.6f
                        val shouldOpen = reveal.value >= threshold
                        scope.launch {
                            if (shouldOpen) {
                                // pequeño rebote hacia adelante y luego acción
                                reveal.animateTo(
                                    targetValue = (maxRevealPx + with(density) { 8.dp.toPx() }).coerceAtMost(maxRevealPx + 18f),
                                    animationSpec = tween(120, easing = FastOutSlowInEasing)
                                )
                                reveal.animateTo(maxRevealPx, animationSpec = tween(120, easing = FastOutSlowInEasing))
                                onClick()
                            }
                            // retorno suave al cero
                            reveal.animateTo(0f, animationSpec = tween(260, easing = FastOutSlowInEasing))
                        }
                    }
                )
        ) {
            Card(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.5.dp,
                        color = if (isDark) Color(0xFFCFD8DC) else Color(0xFFB0BEC5),
                        shape = RoundedCornerShape(12.dp)
                    ),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF23272A).copy(alpha = 0.85f) else Color(0xFFF7FAFC).copy(alpha = 0.85f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Icono o imagen
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { imagePickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (imageUri != null) {
                            val inputStream: InputStream? = try { context.contentResolver.openInputStream(imageUri) } catch (e: Exception) { null }
                            val bitmap = remember(imageUri) {
                                inputStream?.use { BitmapFactory.decodeStream(it) }
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop // Import corregido
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Inventory,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Inventory,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.productName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Chip de precio (verde adaptado a tema, más transparente)
                        val priceBgAlpha = if (isDark) Color(0xFF1B5E20).copy(alpha = 0.85f) else Color(0xFF2E7D32).copy(alpha = 0.85f)
                        Box(
                            modifier = Modifier
                                .background(
                                    color = priceBgAlpha,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Q${"%.2f".format(item.pricePerUnit)}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Valor total en un cuadro amarillo, igual que el precio unitario, más transparente
                        val totalBgAlpha = if (isDark) Color(0xFFFFF176).copy(alpha = 0.85f) else Color(0xFFFFEB3B).copy(alpha = 0.85f)
                        Box(
                            modifier = Modifier
                                .background(
                                    color = totalBgAlpha,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Valor total: Q${"%.2f".format(totalValue)}",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Total de unidades: número grande en rojo y etiqueta pequeña debajo
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${item.quantity}",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "unidades",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyInventoryView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.Inventory,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "No hay productos en el inventario",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Agrega productos usando el botón +",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InventorySummaryCard(items: List<InventoryItem>) {
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("es", "GT"))
    val totalProducts = items.size
    val totalQuantity = items.sumOf { it.quantity }
    val totalValue = items.sumOf { it.quantity * it.pricePerUnit }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SummaryItem(
                title = "Productos",
                value = totalProducts.toString(),
                icon = Icons.Filled.Category
            )
            SummaryItem(
                title = "Unidades",
                value = totalQuantity.toString(),
                icon = Icons.Filled.Inventory
            )
            SummaryItem(
                title = "Valor Total",
                value = currencyFormat.format(totalValue),
                icon = Icons.Filled.AttachMoney
            )
        }
    }
}

@Composable
private fun SummaryItem(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
