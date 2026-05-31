package com.cliplist.app.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.cliplist.app.R
import com.cliplist.app.settings.SettingsViewModel
import com.cliplist.app.settings.ThemeMode
import com.cliplist.scan.AudioExtensions

@Composable
fun SettingsScreen(navController: NavController, vm: SettingsViewModel) {
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val exts by vm.audioExtensions.collectAsStateWithLifecycle()

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(20.dp))

            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            ThemeOption(stringResource(R.string.theme_system), themeMode == ThemeMode.System) {
                vm.setThemeMode(ThemeMode.System)
            }
            ThemeOption(stringResource(R.string.theme_light), themeMode == ThemeMode.Light) {
                vm.setThemeMode(ThemeMode.Light)
            }
            ThemeOption(stringResource(R.string.theme_dark), themeMode == ThemeMode.Dark) {
                vm.setThemeMode(ThemeMode.Dark)
            }

            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            val currentLocaleTags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            LANGUAGES.forEach { (tag, autonym) ->
                val selected = if (tag.isEmpty()) currentLocaleTags.isEmpty()
                    else currentLocaleTags.equals(tag, ignoreCase = true)
                ThemeOption(autonym ?: stringResource(R.string.language_system), selected) {
                    AppCompatDelegate.setApplicationLocales(
                        if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                        else LocaleListCompat.forLanguageTags(tag)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Audio formats", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            AudioExtensions.DEFAULT.sorted().forEach { ext ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(".$ext", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = ext in exts, onCheckedChange = { vm.toggleExtension(ext, it) })
                }
            }
        }
    }
}

@Composable
private fun ThemeOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.height(0.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp))
    }
}

// (BCP-47 tag, autonym shown in its own language). "" = follow the system language.
private val LANGUAGES: List<Pair<String, String?>> = listOf(
    "" to null,
    "en" to "English", "es" to "Español", "fr" to "Français", "de" to "Deutsch",
    "pt-BR" to "Português (Brasil)", "it" to "Italiano", "ru" to "Русский",
    "ja" to "日本語", "ko" to "한국어", "zh-CN" to "简体中文",
)
