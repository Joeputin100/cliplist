package com.cliplist.app.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.cliplist.app.R
import com.cliplist.app.nav.Screen
import com.cliplist.app.workflow.ScanUiState
import com.cliplist.app.workflow.ScanViewModel
import com.cliplist.scan.PlaylistAction
import com.cliplist.scan.PlaylistRow
import com.cliplist.scan.PreviewModel
import com.cliplist.scan.RenameRow

@Composable
fun PreviewScreen(navController: NavController, vm: ScanViewModel) {
    val scanState by vm.scanState.collectAsStateWithLifecycle()
    val model = (scanState as? ScanUiState.Ready)?.model

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        if (model == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
                Text(stringResource(R.string.no_preview), style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
        ) {
            item {
                Text(stringResource(R.string.preview), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                val summary = stringResource(R.string.preview_summary_fmt, model.playlists.size, model.totalTracks)
                val minutes = (model.totalDurationMs / 60000L).toInt()
                val durationPart = if (minutes > 0)
                    " · " + stringResource(R.string.results_duration_fmt, minutes / 60, minutes % 60) else ""
                val limitsPart = if (model.withinLimits) " · " + stringResource(R.string.within_limits) else ""
                Text(
                    summary + durationPart + limitsPart,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (model.unreadable.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.results_unreadable_fmt, model.unreadable.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
            if (model.warnings.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.section_warnings)) }
                items(model.warnings) { w ->
                    WarningCard(w)
                    Spacer(Modifier.height(8.dp))
                }
            }
            item { SectionHeader(stringResource(R.string.section_playlists)) }
            items(model.playlists) { p ->
                PlaylistCard(p)
                Spacer(Modifier.height(8.dp))
            }
            if (model.renames.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.section_renames) + " (${model.renames.size})") }
                items(model.renames) { r ->
                    RenameCardRow(r)
                    Spacer(Modifier.height(8.dp))
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        vm.generate()
                        navController.navigate(Screen.Progress.route)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.generate)) }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
    )
}

@Composable
private fun PlaylistCard(p: PlaylistRow) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(p.folderName, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.tracks_with_playlist_fmt, p.trackCount, p.playlistName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                if (p.action == PlaylistAction.NEW) stringResource(R.string.badge_new) else stringResource(R.string.badge_replace),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (p.action == PlaylistAction.NEW)
                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun RenameCardRow(r: RenameRow) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text("${r.oldName}  →  ${r.newName}", style = MaterialTheme.typography.bodyMedium)
            Text(
                (if (r.isDirectory) "folder" else "file") +
                    (if (r.parentPath.isNotEmpty()) " in ${r.parentPath}" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WarningCard(text: String) {
    Card(Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error)
    }
}
