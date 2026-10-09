package com.minimal.carlauncher.ui

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.minimal.carlauncher.ui.home.HomeScreen
import com.minimal.carlauncher.ui.theme.CarLauncherTheme
import com.minimal.carlauncher.ui.viewmodel.LauncherViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            enableImmersiveMode()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            val isDarkMode by viewModel.isDarkMode.collectAsState()
            CarLauncherTheme(isDarkMode = isDarkMode) {
                HomeScreen(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            enableImmersiveMode()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        viewModel.loadApps()
        viewModel.refreshRadio()
        // GPS speedometer is no longer shown on the home screen, so location tracking is not started.
    }

    override fun onPause() {
        super.onPause()
        viewModel.speedometer.stopTracking()
    }

    private fun enableImmersiveMode() {
        window.decorView.post {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.setDecorFitsSystemWindows(false)
                    window.insetsController?.let { controller ->
                        controller.hide(WindowInsets.Type.statusBars())
                        controller.systemBarsBehavior =
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                } else {
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility = (
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Prevent back press from closing the launcher
        if (viewModel.isAppDrawerOpen.value) {
            viewModel.closeAppDrawer()
        }
    }
}
