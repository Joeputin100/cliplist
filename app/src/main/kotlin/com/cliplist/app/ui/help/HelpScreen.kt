package com.cliplist.app.ui.help

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.cliplist.app.R
import com.cliplist.app.settings.SettingsViewModel
import com.cliplist.app.ui.home.WizardDialog

private val FAQ: List<Pair<Int, Int>> = listOf(
    R.string.help_q_make to R.string.help_a_make,
    R.string.help_q_notshow to R.string.help_a_notshow,
    R.string.help_q_clean to R.string.help_a_clean,
    R.string.help_q_hidden to R.string.help_a_hidden,
    R.string.help_q_where to R.string.help_a_where,
    R.string.help_q_eject to R.string.help_a_eject,
    R.string.help_q_formats to R.string.help_a_formats,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(navController: NavController, settingsVm: SettingsViewModel) {
    var showWizard by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.help_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.help_title))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            OutlinedButton(onClick = { showWizard = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.help_show_wizard))
            }
            Spacer(Modifier.height(16.dp))
            FAQ.forEach { (q, a) ->
                FaqCard(q, a)
                Spacer(Modifier.height(10.dp))
            }
        }
    }

    if (showWizard) {
        WizardDialog(
            onDismiss = { showWizard = false },
            onDontShowAgain = {
                settingsVm.setHideWizard(true)
                showWizard = false
            }
        )
    }
}

@Composable
private fun FaqCard(questionRes: Int, answerRes: Int) {
    var expanded by remember { mutableStateOf(false) }
    Card(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(questionRes),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.rotate(if (expanded) 180f else 0f)
                )
            }
            AnimatedVisibility(expanded) {
                Text(
                    stringResource(answerRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
