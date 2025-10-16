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
import androidx.compose.foundation.shape.CircleShape
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.tasks.await

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
    var selectedProductForDetails by remember { mutableStateOf<InventoryItem?>(null) }

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
                    onProductClick = { item -> onOpenHistory(item.productId) },
                    onSwipeDetails = { item -> selectedProductForDetails = item }
                )
            }
        }
    }

    // Diálogo para agregar producto
    if (showAddProductDialog) {
        AddProductDialog(
            onDismiss = { showAddProductDialog = false },
            onConfirm = { productName, pricePerUnit, costPrice, description, imageUri ->
                viewModel.addProduct(productName.uppercase(), pricePerUnit, costPrice, description, imageUri, context)
                showAddProductDialog = false
            }
        )
    }

    // Panel de detalles del producto
    selectedProductForDetails?.let { item ->
        var hasMovements by remember { mutableStateOf(false) }

        // Verificar movimientos de forma asíncrona
        LaunchedEffect(item.id) {
            hasMovements = viewModel.hasProductMovements(item.id)
        }

         ProductDetailsDialog(
             product = item,
             onDismiss = { selectedProductForDetails = null },
             onEdit = { updatedItem ->
                 viewModel.updateProduct(updatedItem)
                 selectedProductForDetails = null
             },
             onDelete = { productId ->
                 viewModel.deleteProduct(productId, context)
                 selectedProductForDetails = null
             },
             hasMovements = hasMovements
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
    onProductClick: (InventoryItem) -> Unit,
    onSwipeDetails: (InventoryItem) -> Unit
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
                // Eliminado: onImageSelected ya no se pasa porque el item no abre selector desde la lista
                onClick = { onProductClick(item) },
                onSwipeDetails = { onSwipeDetails(item) }
            )
        }
    }
}

@Composable
private fun ProductListItem(
    item: InventoryItem,
    imageUri: Uri?,
    // eliminado: onImageSelected: (Uri?) -> Unit,
    onClick: () -> Unit,
    onSwipeDetails: () -> Unit
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
                                onSwipeDetails()
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
                        color = if (isDark) Color(0xFF424242) else Color(0xFFBDBDBD), // gris en modo claro
                        shape = RoundedCornerShape(16.dp)
                    ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF212121) else Color.White) // blanco en modo claro
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                    // HEADER superior: icono a la izquierda y textos a la derecha (titulo ocupa todo el espacio)
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        val iconSize = 72.dp
                        val iconCorner = 12.dp
                        Box(
                            modifier = Modifier.size(iconSize).clip(RoundedCornerShape(iconCorner)),
                            contentAlignment = Alignment.Center
                        ) {
                            val hasRemote = item.imageUri.isNotEmpty()
                            when {
                                hasRemote -> {
                                    AsyncImage(
                                        model = item.imageUri,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                imageUri != null -> {
                                    AsyncImage(
                                        model = imageUri,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                else -> {
                                    val initials = remember(item.productName) { item.productName.trim().take(3).uppercase(Locale.getDefault()) }
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(
                                            color = if (isDark) Color(0xFF263238) else Color(0xFFE3F2FD),
                                            shape = RoundedCornerShape(iconCorner)
                                        ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = initials,
                                            color = if (isDark) Color(0xFF90CAF9) else Color(0xFF1565C0),
                                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 28.sp),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Título ocupado todo el espacio disponible (máx 3 líneas)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.productName,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, lineHeight = 24.sp),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isDark) Color(0xFF90CAF9) else Color(0xFF1565C0) // celeste en oscuro, azul en claro
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
                                color = if (isDark) Color(0xFFFFA726) else Color(0xFFD32F2F),
                                lineHeight = 42.sp
                            )
                            Text(
                                text = "unidades",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                color = if (isDark) Color(0xFFB0BEC5) else MaterialTheme.colorScheme.onSurfaceVariant
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
                                colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF263238) else Color.White),
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
                        Box(modifier = Modifier.weight(1f)) { PriceChip("VENTA", item.pricePerUnit, if (isDark) Color(0xFF66BB6A) else Color(0xFF2E7D32)) }
                        Box(modifier = Modifier.weight(1f)) { PriceChip("COSTO", item.costPrice, if (isDark) Color(0xFFFFB74D) else Color(0xFFF57C00)) }
                        Box(modifier = Modifier.weight(1f)) { PriceChip("TOTAL", totalValue, if (isDark) Color(0xFFEF5350) else Color(0xFFD32F2F)) }
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

@Composable
fun ProductDetailsDialog(
    product: InventoryItem,
    onDismiss: () -> Unit,
    onEdit: (InventoryItem) -> Unit = {},
    onDelete: (String) -> Unit = {},
    hasMovements: Boolean = false // Nuevo parámetro para verificar si tiene movimientos
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var editedName by remember { mutableStateOf(product.productName) }
    var editedPrice by remember { mutableStateOf(product.pricePerUnit.toString()) }
    var editedCost by remember { mutableStateOf(product.costPrice.toString()) }
    var editedDescription by remember { mutableStateOf(product.description) }

    // Estado para imagen y subida
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
                    val productKey = if (product.productId.isNotEmpty()) product.productId else product.id.ifEmpty { UUID.randomUUID().toString() }
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
                    // todo: opcional mostrar error
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
                                        if (isEditing) imagePickerLauncher.launch("image/*")
                                    },
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
                                    product.imageUri.isNotEmpty() -> {
                                        AsyncImage(
                                            model = product.imageUri,
                                            contentDescription = "Imagen actual",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    else -> {
                                        if (isEditing) {
                                            Icon(
                                                Icons.Filled.CameraAlt,
                                                contentDescription = "Agregar imagen",
                                                modifier = Modifier.size(32.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            val initials = remember(product.productName) { product.productName.trim().take(3).uppercase(Locale.getDefault()) }
                                            Box(
                                                modifier = Modifier.fillMaxSize().background(
                                                    color = if (isSystemInDarkTheme()) Color(0xFF263238) else Color(0xFFE3F2FD),
                                                    shape = CircleShape
                                                ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = initials,
                                                    color = if (isSystemInDarkTheme()) Color(0xFF90CAF9) else Color(0xFF1565C0),
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            if (isEditing) {
                                Text(
                                    if (product.imageUri.isEmpty() && uploadedImageUrl.isNullOrEmpty()) "Toca para agregar imagen" else "Toca para cambiar imagen",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

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
                                modifier = Modifier.fillMaxWidth(),
                                prefix = { Text("Q ") },
                                singleLine = true
                            )
                        } else {
                            // Sección: Producto y descripción (sin título "Información del Producto")
                            LabelBlock(label = "Producto", value = product.productName, prominent = true)
                            if (product.description.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                LabelBlock(label = "Descripción", value = product.description)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Sección: Cantidad y Estado (dos columnas)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SectionContainer(modifier = Modifier.weight(1f), title = "Cantidad") {
                                    LabelBlock(label = "Cantidad", value = "${product.quantity} unidades", showLabel = false)
                                }
                                SectionContainer(modifier = Modifier.weight(1f), title = "Estado") {
                                    LabelBlock(label = "Estado", value = if (product.isAvailable) "Disponible" else "No disponible", showLabel = false)
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
                                        value = if (product.costPrice > 0) numberFormat.format(product.costPrice) else "Q0.00",
                                        showLabel = false
                                    )
                                }
                                SectionContainer(modifier = Modifier.weight(1f), title = "Precio Unitario") {
                                    LabelBlock(
                                        label = "Precio Unitario",
                                        value = if (product.pricePerUnit > 0) numberFormat.format(product.pricePerUnit) else "Q0.00",
                                        showLabel = false
                                    )
                                }
                            }
                            SectionContainer(title = "Valor Total") {
                                LabelBlock(
                                    label = "Valor Total",
                                    value = if (product.quantity > 0 && product.pricePerUnit > 0) numberFormat.format(product.quantity * product.pricePerUnit) else "Q0.00",
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
                        DetailItem("Fecha de Entrada", product.entryDate)
                        if (product.registeredByName.isNotEmpty()) {
                            DetailItem("Registrado por", product.registeredByName)
                        }
                        if (product.notes.isNotEmpty()) {
                            DetailItem("Notas", product.notes)
                        }
                    }
                }
            }
        },
        confirmButton = {
            // Row con 3 "slots" iguales para forzar extremos: izquierda / centro / derecha
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // IZQUIERDA (start)
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (isEditing) {
                        IconButton(onClick = {
                            isEditing = false
                            // Resetear valores editados
                            editedName = product.productName
                            editedPrice = product.pricePerUnit.toString()
                            editedCost = product.costPrice.toString()
                            editedDescription = product.description
                            selectedLocalImage = null
                            uploadedImageUrl = null
                            isUploading = false
                            uploadProgress = 0f
                        }) {
                            Icon(Icons.Filled.Cancel, contentDescription = "Cancelar", tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        if (!hasMovements) {
                            IconButton(onClick = { showDeleteConfirmation = true }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            IconButton(onClick = {}, enabled = false) {
                                Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                            }
                        }
                    }
                }

                // CENTRO (center)
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (!isEditing) {
                        IconButton(onClick = { isEditing = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Modificar", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                // DERECHA (end)
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    if (isEditing) {
                        IconButton(
                            onClick = {
                                val priceDouble = editedPrice.toDoubleOrNull() ?: 0.0
                                val costDouble = editedCost.toDoubleOrNull() ?: 0.0
                                val newImage = uploadedImageUrl ?: product.imageUri
                                onEdit(
                                    product.copy(
                                        productName = editedName.trim(),
                                        pricePerUnit = priceDouble,
                                        costPrice = costDouble,
                                        description = editedDescription.trim(),
                                        imageUri = newImage
                                    )
                                )
                                isEditing = false
                            },
                            enabled = editedName.isNotBlank() && (editedPrice.toDoubleOrNull() ?: -1.0) >= 0.0 && !isUploading
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = "Guardar", tint = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
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
                        onDelete(product.id)
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
private fun ProductImagePlaceholder(product: InventoryItem, isEditing: Boolean) {
    val isDark = isSystemInDarkTheme()

    if (isEditing) {
        Icon(
            Icons.Filled.CameraAlt,
            contentDescription = "Agregar imagen",
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        val initials = remember(product.productName) {
            product.productName.trim().take(3).uppercase(Locale.getDefault())
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = if (isDark) Color(0xFF263238) else Color(0xFFE3F2FD),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                color = if (isDark) Color(0xFF90CAF9) else Color(0xFF1565C0),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                ),
                textAlign = TextAlign.Center
            )
        }
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
