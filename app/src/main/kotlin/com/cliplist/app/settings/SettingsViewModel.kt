package com.cliplist.app.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cliplist.scan.AudioExtensions
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SettingsRepository(app)

    val themeMode: StateFlow<ThemeMode> =
        repo.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.System)

    val audioExtensions: StateFlow<Set<String>> =
        repo.audioExtensions.stateIn(viewModelScope, SharingStarted.Eagerly, AudioExtensions.DEFAULT)

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { repo.setThemeMode(mode) }

    fun toggleExtension(ext: String, enabled: Boolean) = viewModelScope.launch {
        val current = audioExtensions.value
        repo.setAudioExtensions(if (enabled) current + ext else current - ext)
    }
}
