package com.example.la_gotita.ui.inventory

import android.content.Intent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.la_gotita.data.model.InventoryMovement
import com.example.la_gotita.data.model.MovementSource
import com.example.la_gotita.data.model.MovementType
import com.example.designsystem.theme.LocalAppColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.text.NumberFormat
import java.util.Locale
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.BorderStroke
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryHistoryScreen(
    productId: String,
    onBack: () -> Unit,
    authViewModel: com.example.la_gotita.ui.login.AuthViewModel // <-- nuevo parámetro
) {
    val viewModel: InventoryHistoryViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val listState = rememberLazyListState()
    val monthsListState = rememberLazyListState()

    LaunchedEffect(productId) {
        viewModel.load(productId)
    }

    // Efecto separado para desplazar el filtro de meses al mes activo cuando cambia
    LaunchedEffect(uiState.monthIndex0) {
        if (uiState.monthIndex0 >= 0 && !uiState.showAllMonths) {
            // Desplazar el LazyRow de meses para centrar el mes activo
            monthsListState.animateScrollToItem(
                index = uiState.monthIndex0,
                scrollOffset = -500, // Offset negativo para centrar mejor el elemento
            )
        }
    }

    val months = remember {
        listOf(
            "enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.productName.isNotBlank()) uiState.productName else "Historial",
                        maxLines = 2
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            if (uiState.showAllMonths) {
                                // Si está en vista "Todos", generar PDF
                                viewModel.generatePdfReport(
                                    context = context,
                                    onSuccess = { uri ->
                                        // Compartir el PDF generado
                                        viewModel.sharePdfReport(context, uri)
                                    },
                                    onError = { error ->
                                        // El error ya se muestra en el ViewModel via Toast
                                    }
                                )
                            } else {
                                // Si está en vista mensual, exportar CSV como antes
                                val csv = viewModel.exportMonthlyCsv() ?: return@launch
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, csv)
                                    type = "text/plain"
                                }
                                val chooser = Intent.createChooser(sendIntent, "Exportar historial mensual")
                                context.startActivity(chooser)
                            }
                        }
                    }) {
                        Icon(Icons.Filled.IosShare, contentDescription = if (uiState.showAllMonths) "Generar PDF" else "Exportar CSV")
                    }
                }
            )
        }
    ) { padding ->
        // Estados del BottomSheet y cantidad
        var showSheet by rememberSaveable { mutableStateOf(false) }
        var sheetType by rememberSaveable { mutableStateOf<MovementType?>(null) }
        var qtyText by rememberSaveable { mutableStateOf("") }
        var descriptionText by rememberSaveable { mutableStateOf("") }
        var movementToEdit by remember { mutableStateOf<InventoryMovement?>(null) }
        var movementToVoid by remember { mutableStateOf<InventoryMovement?>(null) }
        var showVoidDialog by remember { mutableStateOf(false) }
        var voidReasonText by rememberSaveable { mutableStateOf("") }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        // Contenedor principal con overlay para botones flotantes
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Contenido scrollable de la pantalla
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Encabezado interactivo con 3 chips horizontales (solo si no es vista "Todos")
                if (!uiState.showAllMonths) {
                    val monthly = uiState.monthlyMovements
                    val headerTotalExits = monthly.filter { it.type == MovementType.EXIT }.sumOf { it.quantity }
                    val headerTotalEntries = monthly.filter { it.type == MovementType.ENTRY }.sumOf { it.quantity }
                    val headerAvailable = uiState.monthlyAvailableStock // stock del mes seleccionado

                    InventoryHeaderChips(
                        available = headerAvailable,
                        totalExits = headerTotalExits,
                        totalEntries = headerTotalEntries,
                        selectedFilter = uiState.typeFilter,
                        onFilterSelected = { filter -> viewModel.setTypeFilter(filter) }
                    )
                }

                // Filtro de meses horizontal con chip "Todos"
                Column {
                    LazyRow(
                        state = monthsListState,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Meses normales con indicadores de stock
                        items(months.indices.toList()) { idx ->
                            val selected = idx == uiState.monthIndex0 && !uiState.showAllMonths
                            // Determinar stock por mes desde monthlySummaries para que el punto no dependa de selección
                            val hasStock = uiState.monthlySummaries
                                .find { it.monthIndex0 == idx }
                                ?.available?.let { it > 0 }
                                ?: run {
                                    // Fallback temporal mientras se carga: usar el stock del mes seleccionado
                                    if (!uiState.showAllMonths && idx == uiState.monthIndex0) uiState.monthlyAvailableStock > 0 else false
                                }

                            MonthChip(
                                monthName = months[idx],
                                selected = selected,
                                hasStock = hasStock,
                                onClick = {
                                    viewModel.setShowAllMonths(false)
                                    viewModel.setMonth(idx)
                                }
                            )
                        }

                        // Chip "Todos" al final
                        item {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { viewModel.setShowAllMonths(true) }
                                    .background(if (uiState.showAllMonths) Color(0xFFFFD600) else Color.Transparent)
                                    .border(
                                        width = if (uiState.showAllMonths) 2.dp else 1.dp,
                                        color = if (uiState.showAllMonths) Color(0xFFD32F2F) else Color.LightGray,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Todos",
                                    color = if (uiState.showAllMonths) Color.Black else Color.LightGray,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Título según el modo
                if (uiState.showAllMonths) {
                    Text(
                        "Resumen por mes - ${uiState.year}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                    )
                } else {
                    val filterText = when (uiState.typeFilter) {
                        MovementType.ENTRY -> "Entradas"
                        MovementType.EXIT -> "Salidas"
                        else -> "Todos"
                    }
                    val monthText = months[uiState.monthIndex0].replaceFirstChar { it.titlecase(Locale.getDefault()) }
                    Text(
                        "Historial $filterText - $monthText",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                    )
                }

                // Contenido principal
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(bottom = if (uiState.showAllMonths) 16.dp else 88.dp) // menos espacio si no hay FABs
                ) {
                    if (uiState.isLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    } else {
                        if (uiState.showAllMonths) {
                            // Vista de tabla resumen por mes
                            MonthlySummaryTable(
                                summaries = uiState.monthlySummaries,
                                months = months,
                                productPrice = uiState.productPrice
                            )
                        } else {
                            // Vista normal de movimientos
                            if (uiState.movements.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Sin movimientos en el mes seleccionado", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                MovementList(
                                    movements = uiState.movements,
                                    listState = listState,
                                    onEditMovement = { movementToEditParam ->
                                        // Abrir el diálogo de edición con los datos del movimiento
                                        movementToEdit = movementToEditParam
                                        qtyText = movementToEditParam.quantity.toString()
                                        descriptionText = movementToEditParam.description
                                        sheetType = movementToEditParam.type
                                        showSheet = true
                                    },
                                    onVoidMovement = { movementToVoidParam ->
                                        // Abrir el diálogo de anulación
                                        movementToVoid = movementToVoidParam
                                        voidReasonText = ""
                                        showVoidDialog = true
                                    }
                                )
                            }
                        }
                    }

                    // Overlays de fade solo para vista normal (no tabla)
                    if (!uiState.showAllMonths && uiState.movements.isNotEmpty()) {
                        val canScrollUp by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 } }
                        val canScrollDown by remember { derivedStateOf {
                            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            lastVisible < (listState.layoutInfo.totalItemsCount - 1)
                        } }
                        val surface = MaterialTheme.colorScheme.surface

                        if (canScrollUp) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .height(12.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(surface, surface.copy(alpha = 0f))
                                        )
                                    )
                            )
                        }
                        if (canScrollDown) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(16.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(surface.copy(alpha = 0f), surface)
                                        )
                                    )
                            )
                        }
                    }
                }
            }

            // Botones flotantes inferiores (solo si no está en vista "Todos")
            if (!uiState.showAllMonths) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // SALIDA a la izquierda
                    ExtendedFloatingActionButton(
                        onClick = {
                            movementToEdit = null // Limpiar modo edición
                            sheetType = MovementType.EXIT
                            qtyText = ""
                            descriptionText = ""
                            showSheet = true
                        },
                        icon = { Icon(Icons.Filled.Remove, contentDescription = "- Salida") },
                        text = { Text("SALIDA") },
                        containerColor = Color(0xFF8B0000),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(50)
                    )
                    // INGRESO a la derecha
                    ExtendedFloatingActionButton(
                        onClick = {
                            movementToEdit = null // Limpiar modo edición
                            sheetType = MovementType.ENTRY
                            qtyText = ""
                            descriptionText = ""
                            showSheet = true
                        },
                        icon = { Icon(Icons.Filled.Add, contentDescription = "+ Ingreso") },
                        text = { Text("INGRESO") },
                        containerColor = Color(0xFF2E7D32),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(50)
                    )
                }
            }
        }

        // Panel inferior para registrar cantidad
        if (showSheet && sheetType != null) {
            ModalBottomSheet(
                onDismissRequest = {
                    showSheet = false
                    movementToEdit = null
                },
                sheetState = sheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val isEntry = sheetType == MovementType.ENTRY
                    val isEditMode = movementToEdit != null

                    Text(
                        if (isEditMode) {
                            if (isEntry) "Editar ingreso" else "Editar salida"
                        } else {
                            if (isEntry) "Registrar ingreso" else "Registrar salida"
                        },
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                    )
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { if (it.all { ch -> ch.isDigit() }) qtyText = it },
                        label = { Text("Cantidad") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Campo de descripción
                    OutlinedTextField(
                        value = descriptionText,
                        onValueChange = { descriptionText = it },
                        label = { Text("Descripción (opcional)") },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = {
                            showSheet = false
                            movementToEdit = null
                        }) { Text("Cancelar") }
                        val qty = qtyText.toIntOrNull() ?: 0
                        Button(
                            onClick = {
                                if (qty > 0) {
                                    if (isEditMode) {
                                        // Modo edición: actualizar el movimiento existente
                                        val movement = movementToEdit!!
                                        viewModel.updateMovement(
                                            movementId = movement.id,
                                            oldQuantity = movement.quantity,
                                            newQuantity = qty,
                                            newDescription = descriptionText,
                                            movementType = sheetType!!,
                                            context = context
                                        )
                                    } else {
                                        // Modo creación: registrar nuevo movimiento
                                        if (isEntry) {
                                            viewModel.registerEntry(qty, description = descriptionText, context = context, authViewModel = authViewModel)
                                        } else {
                                            viewModel.registerExit(qty, description = descriptionText, context = context, authViewModel = authViewModel)
                                        }
                                    }
                                    showSheet = false
                                    movementToEdit = null
                                }
                            },
                            enabled = qty > 0,
                            colors = if (isEntry) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(
                                if (isEditMode) {
                                    if (isEntry) "Actualizar Ingreso" else "Actualizar Salida"
                                } else {
                                    if (isEntry) "Registrar Ingreso" else "Registrar Salida"
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        // Diálogo de anulación
        if (showVoidDialog && movementToVoid != null) {
            AlertDialog(
                onDismissRequest = {
                    showVoidDialog = false
                    movementToVoid = null
                    voidReasonText = ""
                },
                title = { Text("Anular movimiento", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("¿Está seguro de anular este movimiento?", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Esta acción revertirá el stock asociado.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Justificación (requerida):", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = voidReasonText,
                            onValueChange = { voidReasonText = it },
                            placeholder = { Text("Ingrese el motivo de la anulación") },
                            singleLine = false,
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (voidReasonText.isNotBlank()) {
                                // Confirmar anulación con los parámetros correctos
                                viewModel.voidMovement(
                                    movementId = movementToVoid!!.id,
                                    movementType = movementToVoid!!.type,
                                    quantity = movementToVoid!!.quantity,
                                    voidReason = voidReasonText,
                                    context = context,
                                    authViewModel = authViewModel
                                )
                                showVoidDialog = false
                                movementToVoid = null
                                voidReasonText = ""
                            }
                        },
                        enabled = voidReasonText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                    ) {
                        Text("Anular movimiento")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showVoidDialog = false
                        movementToVoid = null
                        voidReasonText = ""
                    }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

@Composable
private fun MovementList(
    movements: List<InventoryMovement>,
    listState: LazyListState,
    onEditMovement: (InventoryMovement) -> Unit,
    onVoidMovement: (InventoryMovement) -> Unit // Nuevo callback para anulación
) {
    val df = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(movements) { movement ->
            MovementItem(
                movement = movement,
                dateFormatter = df,
                onSwipeEdit = { onEditMovement(movement) },
                onSwipeVoid = { onVoidMovement(movement) } // Pasar el movimiento a anular
            )
        }
    }
}

@Composable
private fun MovementItem(
    movement: InventoryMovement,
    dateFormatter: SimpleDateFormat,
    onSwipeEdit: () -> Unit,
    onSwipeVoid: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    // Cambiar colores si el movimiento está anulado
    val cardBgColor = when {
        movement.isVoided && isDark -> Color(0xFF424242)
        movement.isVoided && !isDark -> Color(0xFF9E9E9E)
        isDark -> MaterialTheme.colorScheme.surface
        else -> Color(0xFFF5F5F5)
    }
    val cardBorderColor = if (isDark) Color.Transparent else Color(0xFFBDBDBD)

    // Color para el botón de editar (azul) y anular (rojo)
    val swipeEditColor = if (isDark) Color(0xFF42A5F5) else Color(0xFF1976D2)
    val swipeVoidColor = if (isDark) Color(0xFFEF5350) else Color(0xFFD32F2F)

    // Dimensiones para el swipe
    val density = LocalDensity.current
    val maxRevealPx = with(density) { 96.dp.toPx() }

    // Animación del desplazamiento (bidireccional)
    val scope = rememberCoroutineScope()
    val reveal = remember { Animatable(0f) } // positivo = derecha (editar), negativo = izquierda (anular)

    // Estado para la altura de la tarjeta
    var cardHeightPx by remember { mutableStateOf(0f) }

    Box(modifier = Modifier.fillMaxWidth()) {
        // Cápsula de EDITAR en el área revelada (swipe derecha, aparece a la izquierda)
        if (reveal.value > 0f && cardHeightPx > 0f && !movement.isVoided) {
            val capsuleWidthPx = reveal.value.coerceAtLeast(cardHeightPx)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(with(density) { reveal.value.toDp() })
                    .align(Alignment.CenterStart),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .width(with(density) { capsuleWidthPx.toDp() })
                        .height(with(density) { cardHeightPx.toDp() })
                        .background(color = swipeEditColor, shape = RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Editar",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Cápsula de ANULAR en el área revelada (swipe izquierda, aparece a la derecha)
        if (reveal.value < 0f && cardHeightPx > 0f && !movement.isVoided) {
            val capsuleWidthPx = (-reveal.value).coerceAtLeast(cardHeightPx)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(with(density) { (-reveal.value).toDp() })
                    .align(Alignment.CenterEnd),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .width(with(density) { capsuleWidthPx.toDp() })
                        .height(with(density) { cardHeightPx.toDp() })
                        .background(color = swipeVoidColor, shape = RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Remove,
                        contentDescription = "Anular",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Contenido de la tarjeta con swipe bidireccional
        Box(
            modifier = Modifier
                .onGloballyPositioned { coords: LayoutCoordinates -> cardHeightPx = coords.size.height.toFloat() }
                .offset { IntOffset(x = reveal.value.roundToInt(), y = 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    enabled = !movement.isVoided, // Deshabilitar swipe si está anulado
                    state = rememberDraggableState { delta ->
                        val newTarget = (reveal.value + delta).coerceIn(-maxRevealPx, maxRevealPx)
                        scope.launch { reveal.snapTo(newTarget) }
                    },
                    onDragStopped = {
                        val threshold = maxRevealPx * 0.6f
                        scope.launch {
                            if (reveal.value >= threshold) {
                                // Swipe derecha -> Editar
                                reveal.animateTo(
                                    targetValue = (maxRevealPx + with(density) { 8.dp.toPx() }).coerceAtMost(maxRevealPx + 18f),
                                    animationSpec = tween(120, easing = FastOutSlowInEasing)
                                )
                                reveal.animateTo(maxRevealPx, animationSpec = tween(120, easing = FastOutSlowInEasing))
                                onSwipeEdit()
                            } else if (reveal.value <= -threshold) {
                                // Swipe izquierda -> Anular
                                reveal.animateTo(
                                    targetValue = -(maxRevealPx + with(density) { 8.dp.toPx() }).coerceAtLeast(-(maxRevealPx + 18f)),
                                    animationSpec = tween(120, easing = FastOutSlowInEasing)
                                )
                                reveal.animateTo(-maxRevealPx, animationSpec = tween(120, easing = FastOutSlowInEasing))
                                onSwipeVoid()
                            }
                            reveal.animateTo(0f, animationSpec = tween(260, easing = FastOutSlowInEasing))
                        }
                    }
                )
        ) {
            Card(
                onClick = { expanded = !expanded },
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = cardBorderColor, shape = RoundedCornerShape(12.dp)),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    // Línea principal: fecha/hora y tipo de movimiento
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Fecha y hora
                        Text(
                            text = dateFormatter.format(movement.date),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (movement.isVoided) Color.Gray else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Tipo de movimiento con texto más pequeño
                        Text(
                            text = if (movement.isVoided) {
                                if (movement.type == MovementType.ENTRY) "ENTRADA ANULADA" else "SALIDA ANULADA"
                            } else {
                                if (movement.type == MovementType.ENTRY) "ENTRADA" else "SALIDA"
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            ),
                            color = if (movement.isVoided) {
                                Color.Gray
                            } else if (movement.type == MovementType.ENTRY) {
                                Color(0xFF2E7D32)
                            } else {
                                Color(0xFFD32F2F)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Segunda línea: descripción y cantidad
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Descripción del movimiento - mostrar justificación si está anulado
                        val movementDescription = when {
                            movement.isVoided && movement.description.isNotBlank() -> movement.description // Muestra "Anulado: X unidades - justificación"
                            movement.description.isNotBlank() -> movement.description
                            movement.source == MovementSource.SALE -> "Salida por venta"
                            movement.source == MovementSource.MANUAL && movement.type == MovementType.EXIT -> "Salida manual"
                            movement.source == MovementSource.MANUAL && movement.type == MovementType.ENTRY -> "Entrada manual"
                            else -> "Sin descripción"
                        }

                        Text(
                            text = movementDescription,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (movement.isVoided) Color.Gray else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )

                        // Cantidad - muestra 0 si está anulado
                        Text(
                            text = if (movement.isVoided) "0" else "${movement.quantity}",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            ),
                            color = if (movement.isVoided) {
                                Color.Gray
                            } else if (movement.type == MovementType.ENTRY) {
                                Color(0xFF2E7D32)
                            } else {
                                Color(0xFFD32F2F)
                            }
                        )
                    }

                    // Detalles expandibles
                    if (expanded) {
                        Spacer(modifier = Modifier.height(8.dp))

                        // Divisor visual más delgado
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Usuario que realizó la operación
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Usuario:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (movement.userName.isNotBlank()) movement.userName else "No especificado",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (movement.isVoided) Color.Gray else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Cliente (si fue por venta)
                            if (movement.source == MovementSource.SALE) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Cliente:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Cliente de venta",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (movement.isVoided) Color.Gray else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            // Saldo disponible de unidades
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Saldo disponible:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${movement.availableAfter} unidades",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (movement.isVoided) Color.Gray else MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Tipo de fuente
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Fuente:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = when (movement.source) {
                                        MovementSource.MANUAL -> "Manual"
                                        MovementSource.SALE -> "Venta"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (movement.isVoided) Color.Gray else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Información de anulación (si está anulado)
                            if (movement.isVoided) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    thickness = 0.5.dp,
                                    color = Color(0xFFEF5350)
                                )

                                Text(
                                    text = "MOVIMIENTO ANULADO",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFFEF5350),
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Anulado por:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = if (movement.voidedBy.isNotBlank()) movement.voidedBy else "No especificado",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Gray
                                    )
                                }

                                if (movement.voidedAt != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Fecha anulación:",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = dateFormatter.format(movement.voidedAt),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.Gray
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Justificación:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = if (movement.voidReason.isNotBlank()) movement.voidReason else "Sin justificación",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Gray,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.End
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryHeaderChips(
    available: Int,
    totalExits: Int,
    totalEntries: Int,
    selectedFilter: MovementType?,
    onFilterSelected: (MovementType?) -> Unit
) {
    val appColors = LocalAppColors.current

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HeaderChip(
            modifier = Modifier.weight(1f),
            title = "DISPONIBLE",
            count = available,
            bgColor = appColors.chipGreenBg,
            textColor = appColors.chipGreenText,
            selected = selectedFilter == null,
            borderColor = appColors.chipRedText,
            onClick = { onFilterSelected(null) },
            titleStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        )

        HeaderChip(
            modifier = Modifier.weight(1f),
            title = "SALIDAS",
            count = totalExits,
            bgColor = appColors.chipRedBg,
            textColor = appColors.chipRedText,
            selected = selectedFilter == MovementType.EXIT,
            borderColor = appColors.chipRedText,
            onClick = { onFilterSelected(MovementType.EXIT) },
            titleStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        )

        HeaderChip(
            modifier = Modifier.weight(1f),
            title = "ENTRADAS",
            count = totalEntries,
            bgColor = appColors.chipBlue2Bg,
            textColor = appColors.chipBlue2Text,
            selected = selectedFilter == MovementType.ENTRY,
            borderColor = appColors.chipRedText,
            onClick = { onFilterSelected(MovementType.ENTRY) },
            titleStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        )
    }
}

@Composable
private fun HeaderChip(
    modifier: Modifier = Modifier,
    title: String,
    count: Int,
    bgColor: Color,
    textColor: Color,
    selected: Boolean,
    borderColor: Color = Color.Transparent,
    onClick: () -> Unit,
    titleStyle: TextStyle = MaterialTheme.typography.labelSmall
) {
     // Animated bg and text color for selection
     val targetBg = if (selected) bgColor.copy(alpha = 0.95f) else bgColor
     val animBg by animateColorAsState(targetValue = targetBg, animationSpec = tween(300))
     val targetText = if (selected) textColor else textColor.copy(alpha = 0.9f)
     val animText by animateColorAsState(targetValue = targetText, animationSpec = tween(300))
     // Animated border color
     val targetBorder = if (selected) borderColor else Color.Transparent
     val animBorder by animateColorAsState(targetValue = targetBorder, animationSpec = tween(300))

    Card(
        modifier = modifier
            .clickable { onClick() }
            .border(width = if (animBorder == Color.Transparent) 0.dp else 2.dp, color = animBorder, shape = RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = animBg),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 8.dp else 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Aplicar titleStyle al texto del título
            Text(text = title, color = animText, style = titleStyle)
            Spacer(modifier = Modifier.height(6.dp))

            // Animated counter + Crossfade
            Crossfade(targetState = count, animationSpec = tween(300)) { target ->
                // Smooth numeric animation
                val animated by animateIntAsState(targetValue = target, animationSpec = tween(600))
                val nf = NumberFormat.getIntegerInstance(Locale.getDefault())
                Text(text = nf.format(animated), color = animText, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 28.sp))
            }

            // Unidad pequeña debajo
            Text(text = "unidades", color = animText.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MonthChip(
    monthName: String,
    selected: Boolean,
    hasStock: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .background(if (selected) Color(0xFFFFD600) else Color.Transparent)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Color(0xFFD32F2F) else Color.LightGray,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Punto indicador de stock
            Canvas(modifier = Modifier.size(8.dp)) {
                drawCircle(
                    color = if (hasStock) Color(0xFF2E7D32) else Color(0xFFD32F2F),
                    radius = size.minDimension / 2,
                    center = Offset(size.width / 2, size.height / 2)
                )
            }

            Text(
                monthName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                color = if (selected) Color.Black else Color.LightGray,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun MonthlySummaryTable(
    summaries: List<MonthSummary>,
    months: List<String>,
    productPrice: Double
) {
    var isMoneyView by remember { mutableStateOf(false) }

    val nf = NumberFormat.getNumberInstance(Locale.getDefault())
    val moneyFormat = NumberFormat.getCurrencyInstance(Locale("es", "GT")).apply {
        currency = java.util.Currency.getInstance("GTQ")
    }

    // Colores adaptativos para modo claro/oscuro
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val tableBackgroundColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color(0xFFF5F5F5)
    val borderColor = if (isDarkTheme) Color(0xFF404040) else Color(0xFFBDBDBD)
    val headerBackgroundColor = if (isDarkTheme) Color(0xFF2D4A87) else Color(0xFFE3F2FD)
    val evenRowColor = if (isDarkTheme) Color(0xFF2A2A2A) else Color.White
    val oddRowColor = if (isDarkTheme) Color(0xFF333333) else Color(0xFFF8F9FA)
    val textPrimaryColor = if (isDarkTheme) Color(0xFFE0E0E0) else Color(0xFF424242)
    val textSecondaryColor = if (isDarkTheme) Color(0xFFB0B0B0) else Color(0xFF757575)
    val headerTextColor = if (isDarkTheme) Color(0xFFFFFFFF) else Color(0xFF1976D2)

    // Filtrar solo los resúmenes válidos (solo 12 meses: índices 0-11)
    val validSummaries = summaries.filter { it.monthIndex0 in 0..11 }

    // Calcular totales solo con resúmenes válidos
    val totalEntries = validSummaries.sumOf { it.entries }
    val totalExits = validSummaries.sumOf { it.exits }
    val totalBalance = totalEntries - totalExits
    val totalAvailable = validSummaries.sumOf { it.available }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Botón para cambiar entre vista de unidades y dinero
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                onClick = { isMoneyView = !isMoneyView },
                modifier = Modifier,
                color = if (isMoneyView) {
                    if (isDarkTheme) MaterialTheme.colorScheme.primary else Color(0xFF1976D2)
                } else {
                    if (isDarkTheme) Color(0xFF2E7D32) else Color(0xFF4CAF50)
                },
                shape = RoundedCornerShape(50),
                shadowElevation = 6.dp,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isDarkTheme) {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    } else {
                        Color.Black.copy(alpha = 0.1f)
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isMoneyView) Icons.Filled.Remove else Icons.Filled.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (isMoneyView) "Unidades" else "Dinero",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        // Tabla con scroll vertical
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = tableBackgroundColor),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, borderColor)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Encabezado con diseño moderno (fijo)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = headerBackgroundColor,
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Text(
                            "MES",
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = headerTextColor
                        )
                        Text(
                            "TOTAL",
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = headerTextColor
                        )
                        Text(
                            "ENTRADA",
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = headerTextColor
                        )
                        Text(
                            "SALIDA",
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = headerTextColor
                        )
                        Text(
                            "DISPONIBLE",
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = headerTextColor
                        )
                    }
                }

                // Contenido de la tabla con scroll vertical - ajustado para no ocupar espacio extra
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 500.dp) // Altura máxima limitada en lugar de weight(1f)
                ) {
                    items(validSummaries.size) { index ->
                        val summary = validSummaries[index]
                        val isEvenRow = index % 2 == 0

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = if (isEvenRow) evenRowColor else oddRowColor
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 12.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Mes con indicador
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Canvas(modifier = Modifier.size(8.dp)) {
                                        drawCircle(
                                            color = if (summary.hasStock) Color(0xFF4CAF50) else Color(0xFFE53935),
                                            radius = size.minDimension / 2
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        months[summary.monthIndex0].take(3).uppercase(),
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = textPrimaryColor
                                    )
                                }

                                // Total
                                Text(
                                    if (isMoneyView) {
                                        if (summary.total == 0) "--" else moneyFormat.format(summary.total * productPrice)
                                    } else {
                                        if (summary.total == 0) "--" else nf.format(summary.total)
                                    },
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Medium,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textPrimaryColor
                                )

                                // Entradas
                                Text(
                                    if (isMoneyView) {
                                        if (summary.entries == 0) "--" else moneyFormat.format(summary.entries * productPrice)
                                    } else {
                                        if (summary.entries == 0) "--" else nf.format(summary.entries)
                                    },
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (summary.entries > 0) Color(0xFF4CAF50) else textSecondaryColor
                                )

                                // Salidas
                                Text(
                                    if (isMoneyView) {
                                        if (summary.exits == 0) "--" else moneyFormat.format(summary.exits * productPrice)
                                    } else {
                                        if (summary.exits == 0) "--" else nf.format(summary.exits)
                                    },
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (summary.exits > 0) Color(0xFFE53935) else textSecondaryColor
                                )

                                // Disponible
                                Text(
                                    if (isMoneyView) {
                                        if (summary.available == 0) "--" else moneyFormat.format(summary.available * productPrice)
                                    } else {
                                        if (summary.available == 0) "--" else nf.format(summary.available)
                                    },
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    color = if (summary.available > 0) Color(0xFF4CAF50) else Color(0xFFE53935),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        // Línea divisoria entre filas
                        if (index < validSummaries.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.fillMaxWidth(),
                                thickness = 0.5.dp,
                                color = borderColor
                            )
                        }
                    }
                }

                // Fila de totales (siempre visible al final, sin espacio extra)
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = 1.5.dp,
                    color = borderColor
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isDarkTheme) Color(0xFF3A3A3A) else Color(0xFFF0F0F0),
                    shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // TOTALES
                        Text(
                            "TOTALES",
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isDarkTheme) Color(0xFFFFD600) else Color(0xFF1976D2)
                        )

                        // Total general
                        Text(
                            if (isMoneyView) {
                                if (totalBalance == 0) "--" else moneyFormat.format(totalBalance * productPrice)
                            } else {
                                if (totalBalance == 0) "--" else nf.format(totalBalance)
                            },
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.labelMedium,
                            color = textPrimaryColor
                        )

                        // Total entradas
                        Text(
                            if (isMoneyView) {
                                if (totalEntries == 0) "--" else moneyFormat.format(totalEntries * productPrice)
                            } else {
                                if (totalEntries == 0) "--" else nf.format(totalEntries)
                            },
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (totalEntries > 0) Color(0xFF4CAF50) else textSecondaryColor
                        )

                        // Total salidas
                        Text(
                            if (isMoneyView) {
                                if (totalExits == 0) "--" else moneyFormat.format(totalExits * productPrice)
                            } else {
                                if (totalExits == 0) "--" else nf.format(totalExits)
                            },
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (totalExits > 0) Color(0xFFE53935) else textSecondaryColor
                        )

                        // Total disponible
                        Text(
                            if (isMoneyView) {
                                if (totalAvailable == 0) "--" else moneyFormat.format(totalAvailable * productPrice)
                            } else {
                                if (totalAvailable == 0) "--" else nf.format(totalAvailable)
                            },
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (totalAvailable > 0) Color(0xFF4CAF50) else Color(0xFFE53935)
                        )
                    }
                }
            }
        }
    }
}
