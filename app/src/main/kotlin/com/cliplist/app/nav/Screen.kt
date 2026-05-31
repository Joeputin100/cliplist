package com.cliplist.app.nav

sealed class Screen(val route: String) {
    object Home     : Screen("home")
    object Preview  : Screen("preview")
    object Progress : Screen("progress")
    object Results  : Screen("results")
    object Privacy  : Screen("privacy")
}
