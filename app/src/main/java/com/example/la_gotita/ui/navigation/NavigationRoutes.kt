package com.example.la_gotita.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

object AppScreenRoutes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val ADMIN_DASHBOARD_ROOT = "admin_dashboard_root"
    const val EMPLOYEE_DASHBOARD = "employee_dashboard"
    const val ADMIN_USER_MANAGEMENT = "admin_user_management"
    const val ADMIN_HOME = "admin_home"
    const val ADMIN_CALENDAR = "admin_calendar"
    const val ADMIN_TASKS = "admin_tasks"
    const val ADMIN_NOTIFICATIONS = "admin_notifications"
    const val ADMIN_INBOX = "admin_inbox"
    const val ADMIN_SETTINGS = "admin_settings"
    const val INVENTORY_MANAGEMENT = "inventory_management"
}

data class NavigationItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: String
)

val adminBottomNavItemsList = listOf(
    NavigationItem("Tablero", Icons.Outlined.Dashboard, Icons.Outlined.Dashboard, AppScreenRoutes.ADMIN_HOME),
    NavigationItem("Calendario", Icons.Outlined.CalendarMonth, Icons.Outlined.CalendarMonth, AppScreenRoutes.ADMIN_CALENDAR),
    NavigationItem("Por hacer", Icons.Outlined.AssignmentTurnedIn, Icons.Outlined.AssignmentTurnedIn, AppScreenRoutes.ADMIN_TASKS),
    NavigationItem("Notificaciones", Icons.Filled.Notifications, Icons.Filled.Notifications, AppScreenRoutes.ADMIN_NOTIFICATIONS),
    NavigationItem("Bandeja", Icons.Outlined.Inbox, Icons.Outlined.Inbox, AppScreenRoutes.ADMIN_INBOX)
)

val adminDrawerNavItemsList = listOf(
    NavigationItem("Dashboard", Icons.Outlined.Dashboard, Icons.Outlined.Dashboard, AppScreenRoutes.ADMIN_HOME),
    NavigationItem("Gestión Usuarios", Icons.Outlined.People, Icons.Outlined.People, AppScreenRoutes.ADMIN_USER_MANAGEMENT),
    NavigationItem("Configuración", Icons.Outlined.Settings, Icons.Outlined.Settings, AppScreenRoutes.ADMIN_SETTINGS)
)
