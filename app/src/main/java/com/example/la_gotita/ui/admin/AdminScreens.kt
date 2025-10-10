package com.example.la_gotita.ui.admin

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.la_gotita.ui.navigation.*
import com.example.la_gotita.ui.login.AuthViewModel
import com.example.la_gotita.ui.common.PlaceholderScreen
import com.example.la_gotita.ui.components.ModuleCard
import com.example.la_gotita.ui.components.ModuleItemData
import com.example.la_gotita.data.model.User
import com.example.la_gotita.data.model.UserRole
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

//actualizar rutas y nombres

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScaffold(
    appNavController: NavHostController,
    authViewModel: AuthViewModel
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val adminContentNavController = rememberNavController()
    val context = LocalContext.current

    val navBackStackEntry by adminContentNavController.currentBackStackEntryAsState()
    val currentAdminRoute = navBackStackEntry?.destination?.route

    val currentScreenTitle = remember(currentAdminRoute) {
        adminBottomNavItemsList.find { it.route == currentAdminRoute }?.title
            ?: adminDrawerNavItemsList.find { it.route == currentAdminRoute }?.title
            ?: "Admin"
    }

    // Obtener el primer nombre del usuario autenticado (para saludo en el TopAppBar)
    val firebaseUser = FirebaseAuth.getInstance().currentUser
    val fullNameForGreeting = firebaseUser?.displayName ?: firebaseUser?.email ?: "Usuario"
    val firstName = fullNameForGreeting.split(" ").firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "Usuario"

    val canPopInner = adminContentNavController.previousBackStackEntry != null
    BackHandler(enabled = drawerState.isOpen || canPopInner) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            canPopInner -> adminContentNavController.popBackStack()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AdminDrawerContent(
                onItemSelected = { route ->
                    scope.launch {
                        try {
                            drawerState.close()
                        } catch (_: Exception) {}

                        if (route == AppScreenRoutes.ADMIN_USER_MANAGEMENT || route == AppScreenRoutes.ADMIN_SETTINGS) {
                            try {
                                appNavController.navigate(route) {
                                    popUpTo(AppScreenRoutes.ADMIN_DASHBOARD_ROOT) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            } catch (e: Exception) {
                                Log.e("NavError", "Failed to navigate to $route", e)
                            }
                        } else {
                            try {
                                adminContentNavController.navigateTopLevel(route, adminContentNavController.graph.startDestinationId)
                            } catch (e: Exception) {
                                Log.e("NavError", "Failed internal navigate to $route", e)
                            }
                        }
                    }
                },
                onLogout = {
                    scope.launch { drawerState.close() }
                    authViewModel.logoutUser(context)
                    appNavController.navigate(AppScreenRoutes.LOGIN) {
                        popUpTo(AppScreenRoutes.ADMIN_DASHBOARD_ROOT) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                currentRoute = currentAdminRoute
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        if (currentAdminRoute == AppScreenRoutes.ADMIN_HOME) {
                            Text("Bienvenido $firstName")
                        } else {
                            Text(currentScreenTitle)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Abrir menú lateral")
                        }
                    },
                    actions = {
                        IconButton(onClick = { /* TODO: Acciones adicionales */ }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                )
            },
            bottomBar = {
                NavigationBar {
                    adminBottomNavItemsList.forEach { item ->
                        NavigationBarItem(
                            selected = item.route == currentAdminRoute,
                            onClick = {
                                adminContentNavController.navigateTopLevel(item.route, adminContentNavController.graph.startDestinationId)
                            },
                            icon = { Icon(item.selectedIcon, contentDescription = item.title) },
                            label = { Text(item.title) }
                        )
                    }
                }
            }
        ) { paddingValues ->
            AdminDashboardContentHost(
                modifier = Modifier.padding(paddingValues),
                navController = adminContentNavController,
                appNavController = appNavController
            )
        }
    }
}

@Composable
fun AdminDrawerContent(
    onItemSelected: (String) -> Unit,
    onLogout: () -> Unit,
    currentRoute: String?
) {
    ModalDrawerSheet {
        Spacer(Modifier.height(16.dp))
        Text(
            "Menú Administrador",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        adminDrawerNavItemsList.forEach { item ->
            NavigationDrawerItem(
                icon = { Icon(item.selectedIcon, contentDescription = item.title) },
                label = { Text(item.title) },
                selected = item.route == currentRoute,
                onClick = { onItemSelected(item.route) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
        Spacer(Modifier.weight(1f))
        NavigationDrawerItem(
            icon = { Icon(Icons.Filled.AccountCircle, contentDescription = "Cerrar Sesión") },
            label = { Text("Cerrar Sesión") },
            selected = false,
            onClick = onLogout,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun AdminDashboardContentHost(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    appNavController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = AppScreenRoutes.ADMIN_HOME,
        modifier = modifier
    ) {
        composable(AppScreenRoutes.ADMIN_HOME) {
            AdminHomeScreen(
                onModuleClick = { route ->
                    appNavController.navigate(route) {
                        popUpTo(AppScreenRoutes.ADMIN_DASHBOARD_ROOT) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
        composable(AppScreenRoutes.ADMIN_CALENDAR) { PlaceholderScreen("Calendario") }
        composable(AppScreenRoutes.ADMIN_TASKS) { PlaceholderScreen("Por Hacer") }
        composable(AppScreenRoutes.ADMIN_NOTIFICATIONS) { PlaceholderScreen("Notificaciones") }
        composable(AppScreenRoutes.ADMIN_INBOX) { PlaceholderScreen("Bandeja de Entrada") }
    }
}

@Composable
fun AdminHomeScreen(
    onModuleClick: (String) -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Notifications, contentDescription = "Anuncios", tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Anuncios importantes", style = MaterialTheme.typography.titleSmall)
                    Text("Tocar para ver el anuncio", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { /* TODO: dismiss anuncio */ }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Cerrar anuncio")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Módulos del Sistema", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { /* TODO: Ver estadísticas */ }) { Text("Ver estadísticas") }
        }

        val systemModules = listOf(
            ModuleItemData("Gestión de Inventarios", "Inventario de productos y stock", Icons.Filled.Inventory, AppScreenRoutes.INVENTORY_MANAGEMENT, true),
            ModuleItemData("Gestión de Clientes", "CRM y datos de clientes", Icons.Filled.People, "", false),
            ModuleItemData("Módulo de Ventas", "Registro y control de ventas", Icons.Filled.PointOfSale, "", false),
            ModuleItemData("Módulo de Gastos", "Control de gastos operativos", Icons.Filled.Receipt, "", false),
            ModuleItemData("Módulo de Contabilidad", "Reportes y balances", Icons.Filled.AccountBalance, "", false)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(systemModules) { module ->
                ModuleCard(
                    module = module,
                    onModuleClick = onModuleClick
                )
            }
        }

        Text("Resumen", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        Text("Panel de control y estadísticas del sistema aparecerán aquí...", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(onBack: () -> Unit, onLogout: () -> Unit) {
    SafeBackHandler(onBack = onBack)

    val context = LocalContext.current
    val vm: UserManagementViewModel = viewModel()
    val uiState by vm.uiState.collectAsState()

    var openCreateUser by remember { mutableStateOf(false) }
    var createEmail by remember { mutableStateOf("") }
    var createName by remember { mutableStateOf("") }
    var createPassword by remember { mutableStateOf("") }
    var createRole by remember { mutableStateOf(UserRole.EMPLOYEE) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestión de Usuarios") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { openCreateUser = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nuevo Usuario") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (uiState.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
            }

            if (uiState.users.isEmpty() && !uiState.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("No hay usuarios registrados", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.users, key = { it.uid }) { user ->
                        ModernUserCard(
                            user = user,
                            onToggleActive = { vm.toggleActive(user, context) },
                            onChangeRole = { newRole -> vm.changeRole(user, newRole, context) },
                            onResetPassword = { vm.sendPasswordReset(user, context) },
                            onEditUser = { newName, newEmail ->
                                if (!newEmail.isNullOrBlank() && !newName.isNullOrBlank()) {
                                    vm.updateUserEmailAndName(user, newEmail, newName, context)
                                }
                            }
                        )
                    }
                }
            }
        }

        if (openCreateUser) {
            CreateUserDialog(
                email = createEmail,
                onEmailChange = { createEmail = it },
                name = createName,
                onNameChange = { createName = it },
                password = createPassword,
                onPasswordChange = { createPassword = it },
                role = createRole,
                onRoleChange = { createRole = it },
                onDismiss = {
                    openCreateUser = false
                    createEmail = ""
                    createName = ""
                    createPassword = ""
                    createRole = UserRole.EMPLOYEE
                },
                onConfirm = {
                    vm.createUser(createEmail, createPassword, createName.ifBlank { null }, createRole, context)
                    openCreateUser = false
                    createEmail = ""
                    createName = ""
                    createPassword = ""
                    createRole = UserRole.EMPLOYEE
                }
            )
        }
    }
}

@Composable
private fun ModernUserCard(
    user: User,
    onToggleActive: () -> Unit,
    onChangeRole: (UserRole) -> Unit,
    onResetPassword: () -> Unit,
    onEditUser: (String?, String?) -> Unit = { _, _ -> }
) {
    var showRoleDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (user.active)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            !user.active -> Color.Gray.copy(alpha = 0.5f)
                            user.role == UserRole.ADMIN.name -> Color(0xFFE57373)
                            else -> Color(0xFF64B5F6)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user.name ?: "Sin nombre",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (user.active)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { if (user.active) showEditDialog = true },
                        enabled = user.active,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Editar usuario",
                            modifier = Modifier.size(16.dp),
                            tint = if (user.active)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }

                Text(
                    text = user.email ?: "Sin email",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (user.active)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = { if (user.active) showRoleDialog = true },
                        enabled = user.active,
                        label = {
                            Text(
                                if (user.role == UserRole.ADMIN.name) "Admin" else "Employee",
                                color = if (user.active)
                                    LocalContentColor.current
                                else
                                    LocalContentColor.current.copy(alpha = 0.5f)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (user.active) {
                                if (user.role == UserRole.ADMIN.name)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            },
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    )

                    TextButton(
                        onClick = onResetPassword,
                        enabled = !user.email.isNullOrBlank() && user.active
                    ) {
                        Text(
                            "Reset",
                            color = if (user.active && !user.email.isNullOrBlank())
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }
            }

            Switch(
                checked = user.active,
                onCheckedChange = { onToggleActive() },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f),
                    checkedThumbColor = Color(0xFF4CAF50),
                    uncheckedTrackColor = Color(0xFFF44336).copy(alpha = 0.5f),
                    uncheckedThumbColor = Color(0xFFF44336)
                )
            )
        }
    }

    if (showEditDialog && user.active) {
        var editName by remember { mutableStateOf(user.name ?: "") }
        var editEmail by remember { mutableStateOf(user.email ?: "") }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Editar Usuario", textAlign = TextAlign.Center) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Nombre completo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editEmail,
                        onValueChange = { editEmail = it },
                        label = { Text("Correo electrónico") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onEditUser(editName.ifBlank { null }, editEmail.ifBlank { null })
                        showEditDialog = false
                    }
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showRoleDialog && user.active) {
        AlertDialog(
            onDismissRequest = { showRoleDialog = false },
            title = { Text("Cambiar Rol", textAlign = TextAlign.Center) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Selecciona el nuevo rol para ${user.name ?: user.email}")

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                showRoleDialog = false
                                onChangeRole(UserRole.ADMIN)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (user.role == UserRole.ADMIN.name)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            if (user.role == UserRole.ADMIN.name) {
                                Icon(Icons.Filled.Check, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("ADMINISTRADOR")
                        }

                        Button(
                            onClick = {
                                showRoleDialog = false
                                onChangeRole(UserRole.EMPLOYEE)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (user.role == UserRole.EMPLOYEE.name)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            if (user.role == UserRole.EMPLOYEE.name) {
                                Icon(Icons.Filled.Check, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("EMPLEADO")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showRoleDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun CreateUserDialog(
    email: String,
    onEmailChange: (String) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    role: UserRole,
    onRoleChange: (UserRole) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crear Nuevo Usuario") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Nombre completo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    label = { Text("Correo electrónico") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("Contraseña (mín. 6 caracteres)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Column {
                    Text(
                        "Rol del usuario:",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = role == UserRole.ADMIN,
                            onClick = { onRoleChange(UserRole.ADMIN) },
                            label = { Text("ADMIN") },
                            leadingIcon = if (role == UserRole.ADMIN) {
                                { Icon(Icons.Filled.Check, contentDescription = null) }
                            } else null
                        )
                        FilterChip(
                            selected = role == UserRole.EMPLOYEE,
                            onClick = { onRoleChange(UserRole.EMPLOYEE) },
                            label = { Text("EMPLOYEE") },
                            leadingIcon = if (role == UserRole.EMPLOYEE) {
                                { Icon(Icons.Filled.Check, contentDescription = null) }
                            } else null
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = email.isNotBlank() && password.isNotBlank() && name.isNotBlank()
            ) {
                Text("Crear Usuario")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
