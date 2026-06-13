package com.example.hobbylog

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureNotificationChannel(this)
        setContent {
            HobbyLogTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HobbyLogApp()
                }
            }
        }
    }
}

@Composable
fun HobbyLogApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: HobbyViewModel = viewModel(factory = HobbyViewModel.factory(context))

    // Request POST_NOTIFICATIONS on Android 13+
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result handled silently */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val navState by viewModel.navState.collectAsState()

    AnimatedContent(
        targetState = navState,
        transitionSpec = {
            when (targetState) {
                is NavState.Detail ->
                    (slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(320)))
                        .togetherWith(fadeOut(tween(200)))
                is NavState.Home ->
                    (slideInHorizontally(tween(320)) { -it / 3 } + fadeIn(tween(320)))
                        .togetherWith(fadeOut(tween(200)))
            }
        },
        label = "navTransition"
    ) { state ->
        when (state) {
            is NavState.Home   -> HomeScreen(viewModel)
            is NavState.Detail -> DetailScreen(viewModel)
        }
    }
}
