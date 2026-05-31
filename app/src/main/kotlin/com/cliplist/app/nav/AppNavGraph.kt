package com.cliplist.app.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cliplist.app.ui.home.HomeScreen
import com.cliplist.app.ui.preview.PreviewScreen
import com.cliplist.app.ui.progress.ProgressScreen
import com.cliplist.app.ui.results.ResultsScreen
import com.cliplist.app.ui.settings.SettingsScreen
import com.cliplist.app.workflow.ScanViewModel

@Composable
fun AppNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    // Activity-scoped: one instance shared by Home and Preview.
    val scanViewModel: ScanViewModel = viewModel()
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route)     { HomeScreen(navController, scanViewModel) }
        composable(Screen.Preview.route)  { PreviewScreen(navController, scanViewModel) }
        composable(Screen.Progress.route) { ProgressScreen(navController) }
        composable(Screen.Results.route)  { ResultsScreen(navController) }
        composable(Screen.Settings.route) { SettingsScreen(navController) }
    }
}
