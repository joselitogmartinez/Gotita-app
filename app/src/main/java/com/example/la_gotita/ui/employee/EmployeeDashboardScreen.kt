package com.example.la_gotita.ui.employee

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun EmployeeDashboardScreen(modifier: Modifier = Modifier, onLogout: () -> Unit) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Employee Dashboard")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onLogout) { Text("Cerrar Sesión") }
        }
    }
}

