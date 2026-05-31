package com.cliplist.app.ui.home

import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.cliplist.app.nav.Screen
import com.cliplist.app.workflow.ScanUiState
import com.cliplist.app.workflow.ScanViewModel
import com.cliplist.app.workflow.SelectedFolder

@Composable
fun HomeScreen(navController: NavController, vm: ScanViewModel) {
    val context = LocalContext.current
    val options by vm.options.collectAsStateWithLifecycle()
    val folder by vm.folder.collectAsStateWithLifecycle()
    val scanState by vm.scanState.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val name = docId.substringAfterLast('/').substringAfterLast(':').ifEmpty { "Selected folder" }
            vm.setFolder(SelectedFolder(uri, name))
        }
    }

    // When a scan finishes, move to Preview exactly once.
    androidx.compose.runtime.LaunchedEffect(scanState) {
        if (scanState is ScanUiState.Ready) navController.navigate(Screen.Preview.route)
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text("My Playlist Creator", style = MaterialTheme.typography.headlineMedium)
            Text(
                "for SanDisk Clip Sport",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))

            Card(onClick = { picker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("MUSIC FOLDER", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        folder?.displayName ?: "Tap to choose a folder",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            ToggleRow("Search subfolders", null, options.searchSubfolders, vm::setSearchSubfolders)
            ToggleRow("Alphabetize tracks", null, options.alphabetize, vm::setAlphabetize)
            ToggleRow("Clean file names", "Plain ASCII for SanDisk Clip Sport",
                options.cleanNames, vm::setCleanNames)
            ToggleRow("Rename hidden files", "For SanDisk Clip Sport compatibility",
                options.renameHidden, vm::setRenameHidden)
            Spacer(Modifier.height(24.dp))

            val scanning = scanState is ScanUiState.Scanning
            Button(
                onClick = { vm.scan() },
                enabled = folder != null && !scanning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (scanning) "Scanning…" else "Scan folder")
            }
            (scanState as? ScanUiState.Error)?.let {
                Spacer(Modifier.height(12.dp))
                Text(it.message, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ToggleRow(
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
        Column(Modifier.fillMaxWidth(0.8f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
