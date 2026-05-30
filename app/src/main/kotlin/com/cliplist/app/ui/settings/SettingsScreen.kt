package com.cliplist.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController

@Composable
fun SettingsScreen(navController: NavController) {
    Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Settings — Phase 3c", style = MaterialTheme.typography.headlineMedium)
        }
    }
}
