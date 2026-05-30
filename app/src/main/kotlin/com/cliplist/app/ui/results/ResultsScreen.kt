package com.cliplist.app.ui.results

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
fun ResultsScreen(navController: NavController) {
    Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Results — Phase 3b", style = MaterialTheme.typography.headlineMedium)
        }
    }
}
