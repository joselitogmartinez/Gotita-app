package com.example.la_gotita.ui.inventory

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    onBack: () -> Unit,
    onOpenHistory: (String) -> Unit,
    viewModel: InventoryViewModel = viewModel()
) {
    SafeBackHandler(onBack = onBack)

    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showAddProductDialog by remember { mutableStateOf(false) }

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
                    onProductClick = { item -> onOpenHistory(item.productId) }
                )
            }
        }
    }

    // Diálogo para agregar producto
    if (showAddProductDialog) {
        AddProductDialog(
            onDismiss = { showAddProductDialog = false },
            onConfirm = { productName, pricePerUnit, costPrice, description ->
                viewModel.addProduct(productName.uppercase(), pricePerUnit, costPrice, description, context)
                showAddProductDialog = false
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
    val context = LocalContext.current

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

        // Contenido (tarjeta)
        Box(
            modifier = Modifier
                .onGloballyPositioned { coords: LayoutCoordinates -> cardHeightPx = coords.size.height.toFloat() }
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
                                reveal.animateTo(
                                    targetValue = (maxRevealPx + with(density) { 8.dp.toPx() }).coerceAtMost(maxRevealPx + 18f),
                                    animationSpec = tween(120, easing = FastOutSlowInEasing)
                                )
                                reveal.animateTo(maxRevealPx, animationSpec = tween(120, easing = FastOutSlowInEasing))
                                onClick()
                            }
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
                        width = 1.dp,
                        color = if (isDark) Color(0xFFE0E0E0) else Color(0xFFE0E0E0),
                        shape = RoundedCornerShape(16.dp)
                    ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                    // HEADER superior: icono a la izquierda y textos a la derecha (titulo ocupa todo el espacio)
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        val iconSize = 72.dp
                        val iconCorner = 12.dp
                        Box(
                            modifier = Modifier
                                .size(iconSize)
                                .clip(RoundedCornerShape(iconCorner))
                                .clickable { imagePickerLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            if (imageUri != null) {
                                val inputStream: InputStream? = try { context.contentResolver.openInputStream(imageUri) } catch (_: Exception) { null }
                                val bitmap = remember(imageUri) { inputStream?.use { BitmapFactory.decodeStream(it) } }
                                if (bitmap != null) {
                                    Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else {
                                    // Fallback: mostrar iniciales en bloque azul si no se pudo decodificar el bitmap
                                    val initials = remember(item.productName) { item.productName.trim().take(3).uppercase(Locale.getDefault()) }
                                    Box(modifier = Modifier
                                        .fillMaxSize()
                                        .background(color = Color(0xFFE3F2FD), shape = RoundedCornerShape(iconCorner)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = initials,
                                            color = Color(0xFF1565C0),
                                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 28.sp),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            } else {
                                // No hay imagen: mostrar las primeras 3 letras en un bloque azul con texto blanco
                                val initials = remember(item.productName) { item.productName.trim().take(3).uppercase(Locale.getDefault()) }
                                Box(modifier = Modifier
                                    .fillMaxSize()
                                    .background(color = Color(0xFFE3F2FD), shape = RoundedCornerShape(iconCorner)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = initials,
                                        color = Color(0xFF1565C0),
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 28.sp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Título ocupado todo el espacio disponible (máx 3 líneas)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.productName,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                color = Color(0xFF212121)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // BLOQUE DE UNIDADES: movido arriba (header), a la derecha
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "${item.quantity}",
                                fontSize = 42.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFD32F2F),
                                lineHeight = 42.sp
                            )
                            Text(
                                text = "unidades",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                color = Color(0xFF9E9E9E)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // FILA INFERIOR: 3 chips de precio compactos (altura flexible) alineados por abajo
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        //val chipShape = RoundedCornerShape(10.dp)
                        // Eliminado: variables no usadas para evitar warnings
                        // Reducimos la altura mínima para chips aún más compactos pero sin recortar el texto
                        //val chipPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)

                        fun splitParts(value: Double): Pair<String, String> {
                            val s = String.format(Locale.US, "%.2f", value)
                            val parts = s.split('.')
                            return parts[0] to parts.getOrElse(1) { "00" }
                        }

                        @Composable
                        fun PriceChip(title: String, amount: Double, color: Color) {
                            val (intPart, decPart) = splitParts(amount)
                            val titleFont = 14.sp
                            val priceFontDefault = 22.sp
                            val decFontDefault = 19.sp
                            val minFont = 12.sp
                            val chipPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)

                            var chipWidthPx by remember { mutableStateOf(0) }
                            var priceFont by remember { mutableStateOf(priceFontDefault) }
                            var decFont by remember { mutableStateOf(decFontDefault) }

                            val densityLocal = LocalDensity.current
                            val textMeasurer = rememberTextMeasurer()

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { coords -> chipWidthPx = coords.size.width },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(2.dp, color),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                // Ajuste dinámico: reducimos las fuentes si el ancho total del texto excede el ancho disponible
                                LaunchedEffect(chipWidthPx, intPart, decPart) {
                                    if (chipWidthPx > 0) {
                                        var pf = priceFontDefault
                                        var df = decFontDefault

                                        // Reservamos un margen interior por padding + separación
                                        val reservedPx = with(densityLocal) { (chipPadding.calculateLeftPadding(LayoutDirection.Ltr) + chipPadding.calculateRightPadding(LayoutDirection.Ltr)).toPx() } + with(densityLocal) { 12.dp.toPx() }
                                        val availablePx = chipWidthPx - reservedPx

                                        // Loop seguro: reducimos hasta que quepa o hasta minFont
                                        while (true) {
                                            val priceLayout = textMeasurer.measure(
                                                text = AnnotatedString("Q$intPart"),
                                                style = TextStyle(fontSize = pf, fontWeight = FontWeight.ExtraBold)
                                            )
                                            val decLayout = textMeasurer.measure(
                                                text = AnnotatedString(".${decPart}"),
                                                style = TextStyle(fontSize = df, fontWeight = FontWeight.ExtraBold)
                                            )

                                            val totalTextWidth = priceLayout.size.width + decLayout.size.width + with(densityLocal) { 4.dp.toPx() }

                                            // Conversión segura: usar values numéricos para comparar y reducir
                                            val pfValue = pf.value
                                            val dfValue = df.value
                                            val minValue = minFont.value

                                            if (totalTextWidth <= availablePx || (pfValue <= minValue && dfValue <= minValue)) break

                                            // Reducir gradualmente: calcular nuevo valor en float y convertir a .sp
                                            val newPf = (pfValue - 1f).coerceAtLeast(minValue)
                                            val newDf = (dfValue - 1f).coerceAtLeast(minValue)
                                            pf = newPf.sp
                                            df = newDf.sp
                                        }

                                        priceFont = pf
                                        decFont = df
                                    }
                                }

                                Column(modifier = Modifier.fillMaxWidth().padding(chipPadding), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(title, color = color, fontWeight = FontWeight.SemiBold, fontSize = titleFont)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                                        Text(text = "Q$intPart", color = color, fontWeight = FontWeight.ExtraBold, fontSize = priceFont)
                                        Text(text = ".${decPart}", color = color, fontWeight = FontWeight.ExtraBold, fontSize = decFont, modifier = Modifier.padding(start = 4.dp))
                                    }
                                }
                            }
                        }

                        // Tres chips con el mismo estilo y altura flexible (si el número no cabe, el chip crecerá)
                        Box(modifier = Modifier.weight(1f)) { PriceChip("VENTA", item.pricePerUnit, Color(0xFF2E7D32)) }
                        Box(modifier = Modifier.weight(1f)) { PriceChip("COSTO", item.costPrice, Color(0xFFF57C00)) }
                        Box(modifier = Modifier.weight(1f)) { PriceChip("TOTAL", totalValue, Color(0xFFD32F2F)) }

                        // Si quieres un chip de unidades en esta fila, descomenta y usa heightIn(min = chipMinHeight)
                        /*
                        Card(
                            modifier = Modifier.width(110.dp).heightIn(min = chipMinHeight),
                            shape = chipShape,
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(0.dp, Color.Transparent),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxSize().padding(chipPadding), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "${item.quantity}", color = Color(0xFFD32F2F), fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = "unidades", style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp), color = Color(0xFF9E9E9E))
                            }
                        }
                        */
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
