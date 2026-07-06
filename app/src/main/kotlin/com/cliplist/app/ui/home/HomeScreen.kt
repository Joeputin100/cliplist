package com.cliplist.app.ui.home

import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.cliplist.app.R
import com.cliplist.app.ui.components.AppLogo
import com.cliplist.app.ui.components.InfoDot
import com.cliplist.app.ui.components.WavyLinearLoader
import com.cliplist.app.ui.components.ZoetropeLoader
import com.cliplist.app.nav.Screen
import com.cliplist.app.settings.SettingsViewModel
import com.cliplist.app.ui.settings.SettingsDrawer
import com.cliplist.app.workflow.ScanUiState
import com.cliplist.app.workflow.ScanViewModel
import com.cliplist.app.workflow.SelectedFolder
import com.cliplist.scan.StorageHeuristics
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    navController: NavController,
    vm: ScanViewModel,
    settingsVm: SettingsViewModel
) {
    val context = LocalContext.current
    val options by vm.options.collectAsStateWithLifecycle()
    val folder by vm.folder.collectAsStateWithLifecycle()
    val scanState by vm.scanState.collectAsStateWithLifecycle()
    val hideWizard by settingsVm.hideWizard.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var showWizardThisSession by remember { mutableStateOf(true) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            if (uri.scheme == "content") {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                val docId = DocumentsContract.getTreeDocumentId(uri)
                val name = docId.substringAfterLast('/').substringAfterLast(':').ifEmpty { "Selected folder" }
                val volumeUuid = StorageHeuristics.removableVolumeUuid(uri.authority, docId)
                vm.setFolder(SelectedFolder(uri, name, volumeUuid))
            } else {
                // file:// (instrumented tests stub the picker): no SAF permission to persist.
                vm.setFolder(SelectedFolder(uri, uri.lastPathSegment ?: "Selected folder"))
            }
        }
    }

    // When a scan finishes, move to Preview exactly once.
    LaunchedEffect(scanState) {
        if (scanState is ScanUiState.Ready) navController.navigate(Screen.Preview.route)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                SettingsDrawer(
                    vm = settingsVm,
                    onPrivacy = {
                        scope.launch { drawerState.close() }
                        navController.navigate(Screen.Privacy.route)
                    }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // Top bar: hamburger opens the Settings drawer.
                Row(modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = stringResource(R.string.open_settings)
                        )
                    }
                }

                // Logo + name + tagline, centered.
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AppLogo(modifier = Modifier.size(64.dp).clip(CircleShape))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        stringResource(R.string.app_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(24.dp))

                Card(onClick = { picker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.music_folder),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            folder?.displayName ?: stringResource(R.string.choose_folder),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                ToggleRow(stringResource(R.string.opt_subfolders), options.searchSubfolders, vm::setSearchSubfolders,
                    R.string.opt_subfolders, R.string.info_subfolders)
                ToggleRow(stringResource(R.string.opt_alphabetize), options.alphabetize, vm::setAlphabetize,
                    R.string.opt_alphabetize, R.string.info_alphabetize)
                Spacer(Modifier.height(24.dp))

                val scanning = scanState as? ScanUiState.Scanning
                Button(
                    onClick = { vm.scan() },
                    enabled = folder != null && scanning == null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (scanning != null) stringResource(R.string.scanning) else stringResource(R.string.scan_folder))
                }
                // Same loader pair as the generate Progress screen: indeterminate zoetrope while
                // folders are enumerated, determinate wave once the metadata pass reports counts.
                scanning?.let { s ->
                    Spacer(Modifier.height(24.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ZoetropeLoader(size = 48.dp)
                        if (s.total > 0 && s.done > 0) {
                            Spacer(Modifier.height(16.dp))
                            WavyLinearLoader(
                                progress = { s.done.toFloat() / s.total.toFloat() },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.progress_count_fmt, s.done, s.total),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                (scanState as? ScanUiState.Error)?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        it.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    if (!hideWizard && showWizardThisSession) {
        WizardDialog(
            onDismiss = { showWizardThisSession = false },
            onDontShowAgain = {
                settingsVm.setHideWizard(true)
                showWizardThisSession = false
            }
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    infoTitleRes: Int? = null,
    infoBodyRes: Int? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(0.8f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
            if (infoTitleRes != null && infoBodyRes != null) InfoDot(infoTitleRes, infoBodyRes)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
