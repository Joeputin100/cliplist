package com.cliplist.app.ui.results

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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

@Composable
fun ResultsScreen(navController: NavController, vm: ScanViewModel) {
    val context = LocalContext.current
    val state by vm.generateState.collectAsStateWithLifecycle()
    val result = (state as? GenerateUiState.Done)?.result

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (result == null) {
                Text(stringResource(R.string.no_preview), style = MaterialTheme.typography.bodyLarge)
                return@Column
            }
            Text(
                if (result.allSucceeded) stringResource(R.string.results_success) else stringResource(R.string.results_issues),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.results_stats_fmt, result.playlistsWritten, result.totalFailed) +
                    if (result.renamesApplied > 0) " · " + stringResource(R.string.results_renamed_fmt, result.renamesApplied) else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (result.errors.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                result.errors.take(5).forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(28.dp))
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
                modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    vm.resetWorkflow()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.done)) }
            TextButton(
                onClick = {
                    vm.resetWorkflow()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            ) { Text(stringResource(R.string.make_another)) }
        }
    }
}
