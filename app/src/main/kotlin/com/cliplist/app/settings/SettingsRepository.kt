package com.cliplist.app.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cliplist.scan.AudioExtensions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { System, Light, Dark }

// Process-singleton DataStore for the whole app.
private val Context.settingsDataStore by preferencesDataStore("settings")

class SettingsRepository(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val extsKey = stringSetPreferencesKey("audio_extensions")

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { p ->
        runCatching { ThemeMode.valueOf(p[themeKey] ?: ThemeMode.System.name) }
            .getOrDefault(ThemeMode.System)
    }

    val audioExtensions: Flow<Set<String>> = context.settingsDataStore.data.map { p ->
        p[extsKey] ?: AudioExtensions.DEFAULT
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[themeKey] = mode.name }
    }

    suspend fun setAudioExtensions(exts: Set<String>) {
        context.settingsDataStore.edit { it[extsKey] = exts }
    }
}
