package com.cliplist.app.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
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
    private val cleanKey = booleanPreferencesKey("clean_names")
    private val renameKey = booleanPreferencesKey("rename_hidden")
    private val coverKey = booleanPreferencesKey("write_cover_art")
    private val wizardKey = booleanPreferencesKey("hide_wizard")

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { p ->
        runCatching { ThemeMode.valueOf(p[themeKey] ?: ThemeMode.System.name) }
            .getOrDefault(ThemeMode.System)
    }

    val audioExtensions: Flow<Set<String>> = context.settingsDataStore.data.map { p ->
        p[extsKey] ?: AudioExtensions.DEFAULT
    }

    val cleanNames: Flow<Boolean> = context.settingsDataStore.data.map { it[cleanKey] ?: true }
    val renameHidden: Flow<Boolean> = context.settingsDataStore.data.map { it[renameKey] ?: true }
    val writeCoverArt: Flow<Boolean> = context.settingsDataStore.data.map { it[coverKey] ?: true }
    val hideWizard: Flow<Boolean> = context.settingsDataStore.data.map { it[wizardKey] ?: false }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[themeKey] = mode.name }
    }

    suspend fun setAudioExtensions(exts: Set<String>) {
        context.settingsDataStore.edit { it[extsKey] = exts }
    }

    suspend fun setCleanNames(v: Boolean) { context.settingsDataStore.edit { it[cleanKey] = v } }
    suspend fun setRenameHidden(v: Boolean) { context.settingsDataStore.edit { it[renameKey] = v } }
    suspend fun setWriteCoverArt(v: Boolean) { context.settingsDataStore.edit { it[coverKey] = v } }
    suspend fun setHideWizard(v: Boolean) { context.settingsDataStore.edit { it[wizardKey] = v } }
}
