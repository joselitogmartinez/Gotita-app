package com.example.la_gotita.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.la_gotita.data.model.UserRole
import com.example.la_gotita.ui.admin.AdminDashboardScaffold
import com.example.la_gotita.ui.admin.UserManagementScreen
import com.example.la_gotita.ui.admin.SettingsScreen
import com.example.la_gotita.ui.inventory.InventoryScreen
import com.example.la_gotita.ui.employee.EmployeeDashboardScreen
import com.example.la_gotita.ui.login.AuthViewModel
import com.example.la_gotita.ui.login.LoginScreen
import com.example.la_gotita.ui.splash.SplashScreen
import com.example.la_gotita.theme.ThemeViewModel
import com.example.designsystem.theme.ThemeMode
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.la_gotita.ui.inventory.InventoryHistoryScreen

@Composable
fun AppNavigation(
    appNavController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel,
    themeViewModel: ThemeViewModel? = null
) {
    NavHost(navController = appNavController, startDestination = AppScreenRoutes.SPLASH) {
        composable(AppScreenRoutes.SPLASH) {
            SplashScreen(
                authViewModel = authViewModel,
                onNavigate = { route ->
                    appNavController.navigate(route) {
                        popUpTo(AppScreenRoutes.SPLASH) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(AppScreenRoutes.LOGIN) {
            LoginScreen(
                authViewModel = authViewModel,
                onLoginSuccess = { role ->
                    val route = when (role) {
                        UserRole.ADMIN -> AppScreenRoutes.ADMIN_DASHBOARD_ROOT
                        UserRole.EMPLOYEE -> AppScreenRoutes.EMPLOYEE_DASHBOARD
                    }
                    appNavController.navigate(route) {
                        popUpTo(AppScreenRoutes.LOGIN) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(AppScreenRoutes.ADMIN_DASHBOARD_ROOT) {
            AdminDashboardScaffold(
                appNavController = appNavController,
                authViewModel = authViewModel
            )
        }
        composable(AppScreenRoutes.EMPLOYEE_DASHBOARD) {
            val context = LocalContext.current
            EmployeeDashboardScreen(
                onLogout = {
                    authViewModel.logoutUser(context)
                    appNavController.navigate(AppScreenRoutes.LOGIN) {
                        popUpTo(AppScreenRoutes.EMPLOYEE_DASHBOARD) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(AppScreenRoutes.ADMIN_USER_MANAGEMENT) {
            val context = LocalContext.current
            UserManagementScreen(
                onBack = {
                    if (!appNavController.popBackStack()) {
                        appNavController.navigate(AppScreenRoutes.ADMIN_DASHBOARD_ROOT) {
                            popUpTo(AppScreenRoutes.ADMIN_DASHBOARD_ROOT) { inclusive = false }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                onLogout = {
                    authViewModel.logoutUser(context)
                    appNavController.navigate(AppScreenRoutes.LOGIN) {
                        popUpTo(AppScreenRoutes.ADMIN_DASHBOARD_ROOT) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(AppScreenRoutes.ADMIN_SETTINGS) {
            SettingsScreen(
                onBack = {
                    if (!appNavController.popBackStack()) {
                        appNavController.navigate(AppScreenRoutes.ADMIN_DASHBOARD_ROOT) {
                            popUpTo(AppScreenRoutes.ADMIN_DASHBOARD_ROOT) { inclusive = false }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                themeViewModel = themeViewModel
            )
        }
        composable(AppScreenRoutes.INVENTORY_MANAGEMENT) {
            InventoryScreen(
                onBack = {
                    if (!appNavController.popBackStack()) {
                        appNavController.navigate(AppScreenRoutes.ADMIN_DASHBOARD_ROOT) {
                            popUpTo(AppScreenRoutes.ADMIN_DASHBOARD_ROOT) { inclusive = false }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                onOpenHistory = { productId ->
                    appNavController.navigate("${AppScreenRoutes.INVENTORY_HISTORY}/$productId")
                }
            )
        }
        composable(
            route = "${AppScreenRoutes.INVENTORY_HISTORY}/{productId}",
            arguments = listOf(navArgument("productId") { type = NavType.StringType })
        ) { backStackEntry ->
            val productId = backStackEntry.arguments?.getString("productId") ?: return@composable
            InventoryHistoryScreen(
                productId = productId,
                onBack = { appNavController.popBackStack() }
            )
        }
    }
}
