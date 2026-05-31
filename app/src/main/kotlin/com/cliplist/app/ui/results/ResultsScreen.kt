package com.cliplist.app.ui.results

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.cliplist.app.R
import com.cliplist.app.nav.Screen
import com.cliplist.app.workflow.GenerateUiState
import com.cliplist.app.workflow.ScanViewModel
import com.cliplist.scan.ResultFolderRow
import com.cliplist.scan.ResultModel

@Composable
fun ResultsScreen(navController: NavController, vm: ScanViewModel) {
    val context = LocalContext.current
    val state by vm.generateState.collectAsStateWithLifecycle()
    val folder by vm.folder.collectAsStateWithLifecycle()
    val result = (state as? GenerateUiState.Done)?.result

    if (result == null) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.no_preview), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val goHome: () -> Unit = {
        vm.resetWorkflow()
        navController.navigate(Screen.Home.route) {
            popUpTo(Screen.Home.route) { inclusive = true }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (result.allSucceeded) stringResource(R.string.results_success)
                else stringResource(R.string.results_issues),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            if (result.destination.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.results_in_destination_fmt, result.destination),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    value = result.playlistsWritten.toString(),
                    label = stringResource(R.string.results_stat_playlists),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    value = result.totalTracks.toString(),
                    label = stringResource(R.string.results_stat_tracks),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    value = result.renamesApplied.toString(),
                    label = stringResource(R.string.results_stat_renamed),
                    modifier = Modifier.weight(1f)
                )
            }

            if (result.errors.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                result.errors.take(5).forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.results_folders_written),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }

        items(result.folders) { row ->
            FolderRow(row)
            Spacer(Modifier.height(8.dp))
        }

        item {
            Spacer(Modifier.height(16.dp))
            // Eject hand-off appears only for removable media (SD card / USB).
            if (folder?.isRemovable == true) {
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.eject)) }
                Text(
                    stringResource(R.string.eject_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
                Spacer(Modifier.height(16.dp))
            }
            Button(onClick = goHome, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.done))
            }
            TextButton(onClick = goHome, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.make_another))
            }
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun FolderRow(row: ResultFolderRow) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(row.folderName, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.results_folder_tracks_fmt, row.trackCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
