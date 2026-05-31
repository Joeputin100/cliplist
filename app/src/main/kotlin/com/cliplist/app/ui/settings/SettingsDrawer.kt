package com.cliplist.app.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cliplist.app.R
import com.cliplist.app.settings.SettingsViewModel
import com.cliplist.app.settings.ThemeMode
import com.cliplist.scan.AudioExtensions

/** The Settings drawer content (lives inside Home's ModalDrawerSheet). */
@Composable
fun SettingsDrawer(vm: SettingsViewModel, onPrivacy: () -> Unit) {
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val exts by vm.audioExtensions.collectAsStateWithLifecycle()
    val cleanNames by vm.cleanNames.collectAsStateWithLifecycle()
    val renameHidden by vm.renameHidden.collectAsStateWithLifecycle()
    val writeCoverArt by vm.writeCoverArt.collectAsStateWithLifecycle()

    var showFormats by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        // Header: small logo + "Settings"
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.mipmap.ic_launcher),
                contentDescription = null,
                modifier = Modifier.size(36.dp).clip(CircleShape)
            )
            Spacer(Modifier.size(12.dp))
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(20.dp))

        // APPEARANCE
        SectionLabel(stringResource(R.string.settings_appearance))
        Spacer(Modifier.height(8.dp))

        val themeLabels = listOf(
            ThemeMode.System to stringResource(R.string.theme_system),
            ThemeMode.Light to stringResource(R.string.theme_light),
            ThemeMode.Dark to stringResource(R.string.theme_dark),
        )
        DropdownField(
            label = stringResource(R.string.settings_theme),
            selectedText = themeLabels.first { it.first == themeMode }.second,
            options = themeLabels.map { it.second },
            onSelect = { idx -> vm.setThemeMode(themeLabels[idx].first) }
        )
        Spacer(Modifier.height(12.dp))

        // Language dropdown: "System default" + 10 autonyms.
        val currentLocaleTags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val languageOptions: List<Pair<String, String>> = listOf(
            "" to stringResource(R.string.language_system)
        ) + LANGUAGE_AUTONYMS
        val selectedLanguageLabel = languageOptions.firstOrNull { (tag, _) ->
            if (tag.isEmpty()) currentLocaleTags.isEmpty()
            else currentLocaleTags.equals(tag, ignoreCase = true)
        }?.second ?: languageOptions.first().second
        DropdownField(
            label = stringResource(R.string.settings_language),
            selectedText = selectedLanguageLabel,
            options = languageOptions.map { it.second },
            onSelect = { idx ->
                val tag = languageOptions[idx].first
                AppCompatDelegate.setApplicationLocales(
                    if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                    else LocaleListCompat.forLanguageTags(tag)
                )
            }
        )
        Spacer(Modifier.height(24.dp))

        // PLAYLIST OPTIONS
        SectionLabel(stringResource(R.string.settings_playlist_options))
        Spacer(Modifier.height(4.dp))
        SwitchRow(
            title = stringResource(R.string.opt_clean_names),
            subtitle = stringResource(R.string.opt_clean_names_sub),
            checked = cleanNames,
            onChange = vm::setCleanNames
        )
        SwitchRow(
            title = stringResource(R.string.opt_rename_hidden),
            subtitle = stringResource(R.string.opt_rename_hidden_sub),
            checked = renameHidden,
            onChange = vm::setRenameHidden
        )
        SwitchRow(
            title = stringResource(R.string.opt_cover_art),
            subtitle = stringResource(R.string.opt_cover_art_sub),
            checked = writeCoverArt,
            onChange = vm::setWriteCoverArt
        )
        // Audio formats row (count badge) -> dialog
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showFormats = true }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.audio_formats), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(
                    R.string.audio_formats_count_fmt,
                    exts.count { it in AudioExtensions.DEFAULT },
                    AudioExtensions.DEFAULT.size
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(24.dp))

        // ABOUT
        SectionLabel(stringResource(R.string.settings_about))
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPrivacy() }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.privacy_policy), style = MaterialTheme.typography.titleMedium)
        }
    }

    if (showFormats) {
        AudioFormatsDialog(
            selected = exts,
            onToggle = { ext, checked -> vm.toggleExtension(ext, checked) },
            onDismiss = { showFormats = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    selectedText: String,
    options: List<String>,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEachIndexed { index, text ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelect(index)
                    }
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.fillMaxWidth(0.78f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun AudioFormatsDialog(
    selected: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.audio_formats)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                AudioExtensions.DEFAULT.sorted().forEach { ext ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(ext, ext !in selected) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(".$ext", style = MaterialTheme.typography.bodyLarge)
                        Checkbox(
                            checked = ext in selected,
                            onCheckedChange = { checked -> onToggle(ext, checked) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.format_dialog_done))
            }
        }
    )
}

// (BCP-47 tag, autonym shown in its own language).
private val LANGUAGE_AUTONYMS: List<Pair<String, String>> = listOf(
    "en" to "English", "es" to "Español", "fr" to "Français", "de" to "Deutsch",
    "pt-BR" to "Português (Brasil)", "it" to "Italiano", "ru" to "Русский",
    "ja" to "日本語", "ko" to "한국어", "zh-CN" to "简体中文",
)
