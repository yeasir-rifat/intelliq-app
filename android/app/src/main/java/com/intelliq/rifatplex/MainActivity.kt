package com.intelliq.rifatplex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.intelliq.rifatplex.data.HistoryStore
import com.intelliq.rifatplex.data.AppStore
import com.intelliq.rifatplex.ui.AiViewModel
import com.intelliq.rifatplex.ui.IntelliQApp

class MainActivity : ComponentActivity() {
    private val store by lazy { AppStore.get(this) }
    private val aiVm: AiViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen() // logo on the app background instead of a white screen (theme: Theme.IntelliQ.Splash)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        HistoryStore.get(this) // starts loading saved papers in the background, so the first screen does not wait for it
        setContent { IntelliQApp(store, aiVm) }
    }

    override fun onPause() {
        store.flush()
        super.onPause()
    }
}
