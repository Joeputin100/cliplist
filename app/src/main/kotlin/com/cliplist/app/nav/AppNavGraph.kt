package com.cliplist.app.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cliplist.app.ui.home.HomeScreen
import com.cliplist.app.ui.preview.PreviewScreen
import com.cliplist.app.ui.progress.ProgressScreen
import com.cliplist.app.ui.results.ResultsScreen
import com.cliplist.app.ui.settings.SettingsScreen

@Composable
fun AppNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController  = navController,
        startDestination = Screen.Home.route,
        modifier       = modifier
    ) {
        composable(Screen.Home.route)     { HomeScreen(navController) }
        composable(Screen.Preview.route)  { PreviewScreen(navController) }
        composable(Screen.Progress.route) { ProgressScreen(navController) }
        composable(Screen.Results.route)  { ResultsScreen(navController) }
        composable(Screen.Settings.route) { SettingsScreen(navController) }
    }
}
