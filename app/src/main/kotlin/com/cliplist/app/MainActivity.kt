package com.cliplist.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.cliplist.app.nav.AppNavGraph
import com.cliplist.app.theme.ClipListTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // enableEdgeToEdge() must be called before super.onCreate() on API < 29;
        // calling it here (after super) is safe on API 29+ and is the Activity Compose convention.
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClipListTheme {
                // AppNavGraph fills the window; each screen owns its own Scaffold + inset padding.
                // Compose Navigation 2.9+ handles predictive back automatically via NavBackStack.
                AppNavGraph(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
