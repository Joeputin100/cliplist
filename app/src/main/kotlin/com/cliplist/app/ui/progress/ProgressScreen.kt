package com.cliplist.app.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.cliplist.app.R
import com.cliplist.app.nav.Screen
import com.cliplist.app.ui.components.ZoetropeLoader
import com.cliplist.app.workflow.GenPhase
import com.cliplist.app.workflow.GenerateUiState
import com.cliplist.app.workflow.ScanViewModel

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProgressScreen(navController: NavController, vm: ScanViewModel) {
    val state by vm.generateState.collectAsStateWithLifecycle()

    // When done, go to Results and remove Progress from the back stack.
    androidx.compose.runtime.LaunchedEffect(state) {
        if (state is GenerateUiState.Done) {
            navController.navigate(Screen.Results.route) {
                popUpTo(Screen.Progress.route) { inclusive = true }
            }
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val s = state) {
                is GenerateUiState.Working -> {
                    val phaseText = when (s.phase) {
                        GenPhase.Starting -> stringResource(R.string.preparing)
                        GenPhase.Cleaning -> stringResource(R.string.phase_cleaning)
                        GenPhase.Writing -> stringResource(R.string.phase_writing)
                    }
                    ZoetropeLoader(size = 72.dp)
                    Spacer(Modifier.height(24.dp))
                    Text(phaseText, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(20.dp))
                    if (s.total > 0 && s.done > 0) {
                        LinearWavyProgressIndicator(
                            progress = { s.done.toFloat() / s.total.toFloat() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.progress_count_fmt, s.done, s.total),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.writing_crlf),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                }
                is GenerateUiState.Error -> {
                    Text(stringResource(R.string.something_wrong), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp))
                    Text(s.message, color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center)
                }
                else -> {
                    Text(stringResource(R.string.preparing), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
