package com.example.la_gotita.theme

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.designsystem.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ThemeViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = ThemePreferencesRepository(application.applicationContext)

    val themeMode: StateFlow<ThemeMode> = repo.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    val dynamicColorEnabled: StateFlow<Boolean> = repo.dynamicColorFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _updating = MutableStateFlow(false)
    val updating = _updating.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            _updating.value = true
            repo.setThemeMode(mode)
            _updating.value = false
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            _updating.value = true
            repo.setDynamicColor(enabled)
            _updating.value = false
        }
    }
}
