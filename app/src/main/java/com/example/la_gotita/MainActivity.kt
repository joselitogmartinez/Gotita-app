package com.example.la_gotita

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.la_gotita.theme.ThemeViewModel
import com.example.la_gotita.ui.login.AuthViewModel
import com.example.la_gotita.ui.navigation.AppNavigation
import com.example.designsystem.theme.LaGotitaTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeViewModel: ThemeViewModel = viewModel()
            val authViewModel: AuthViewModel = viewModel()
            val themeMode by themeViewModel.themeMode.collectAsState()
            val dynamicColor by themeViewModel.dynamicColorEnabled.collectAsState()
            LaGotitaTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavigation(authViewModel = authViewModel, themeViewModel = themeViewModel)
                }
            }
        }
    }
}
