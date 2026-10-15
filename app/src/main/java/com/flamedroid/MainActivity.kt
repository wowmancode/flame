package com.flamedroid

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.flamedroid.ui.FlameApp
import com.flamedroid.ui.FlameTheme

class MainActivity : ComponentActivity() {
    private val vm: FlameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            FlameTheme { FlameApp(vm) }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.setPaused(false)
    }

    override fun onStop() {
        vm.setPaused(true) // no background rendering heat while the app is hidden
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) intent.data?.let { vm.importFlame(it) }
    }
}
