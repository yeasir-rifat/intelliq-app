package com.intelliq.rifatplex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.intelliq.rifatplex.data.AppStore
import com.intelliq.rifatplex.ui.AiViewModel
import com.intelliq.rifatplex.ui.IntelliQApp

class MainActivity : ComponentActivity() {
    private val store by lazy { AppStore.get(this) }
    private val aiVm: AiViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { IntelliQApp(store, aiVm) }
    }

    override fun onPause() {
        store.flush()
        super.onPause()
    }
}
