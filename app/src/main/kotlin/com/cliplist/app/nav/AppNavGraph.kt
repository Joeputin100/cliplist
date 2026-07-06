package com.cliplist.app.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cliplist.app.settings.SettingsViewModel
import com.cliplist.app.ui.help.HelpScreen
import com.cliplist.app.ui.home.HomeScreen
import com.cliplist.app.ui.preview.PreviewScreen
import com.cliplist.app.ui.privacy.PrivacyScreen
import com.cliplist.app.ui.progress.ProgressScreen
import com.cliplist.app.ui.results.ResultsScreen
import com.cliplist.app.workflow.ScanViewModel

@Composable
fun AppNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    settingsViewModel: SettingsViewModel
) {
    // Activity-scoped: one instance shared by Home and Preview.
    val scanViewModel: ScanViewModel = viewModel()
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route)     { HomeScreen(navController, scanViewModel, settingsViewModel) }
        composable(Screen.Preview.route)  { PreviewScreen(navController, scanViewModel) }
        composable(Screen.Progress.route) { ProgressScreen(navController, scanViewModel) }
        composable(Screen.Results.route)  { ResultsScreen(navController, scanViewModel) }
        composable(Screen.Privacy.route)  { PrivacyScreen(navController) }
        composable(Screen.Help.route)     { HelpScreen(navController, settingsViewModel) }
    }
}
