package com.swarnkary.donezy

import android.Manifest
import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {

    private val pendingHobbyId = kotlinx.coroutines.flow.MutableStateFlow(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureNotificationChannel(this)
        pendingHobbyId.value = intent.getLongExtra("hobby_id", 0L)

        setContent {
            val viewModel: HobbyViewModel = viewModel(factory = HobbyViewModel.factory(this))
            val themeMode by viewModel.themeMode.collectAsState()
            val pending by pendingHobbyId.collectAsState()

            HobbyLogTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HobbyLogApp(viewModel)
                }
            }

            // Honor deep-link from notification taps. Re-firing whenever the flow changes
            // means a new notification tap while the activity is alive is also handled.
            LaunchedEffect(pending) {
                if (pending > 0L) {
                    viewModel.openDetail(pending)
                    pendingHobbyId.value = 0L
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val id = intent.getLongExtra("hobby_id", 0L)
        if (id > 0L && pendingHobbyId.value != id) {
            pendingHobbyId.value = id
        }
    }
}

@Composable
fun HobbyLogApp(viewModel: HobbyViewModel) {
    val context = LocalContext.current

    // Permission request: only ask once per cold start, and only if not already granted.
    var permissionAsked by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result handled silently */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasNotificationPermission(context) &&
            !permissionAsked
        ) {
            permissionAsked = true
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val navState by viewModel.navState.collectAsState()

    AnimatedContent(
        targetState = navState,
        transitionSpec = {
            when (targetState) {
                is NavState.Home ->
                    (slideInHorizontally(tween(320)) { -it / 3 } + fadeIn(tween(320)))
                        .togetherWith(fadeOut(tween(200)))
                else ->
                    (slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(320)))
                        .togetherWith(fadeOut(tween(200)))
            }
        },
        label = "navTransition"
    ) { state ->
        when (state) {
            is NavState.Home     -> HomeScreen(viewModel)
            is NavState.Detail   -> DetailScreen(viewModel)
            NavState.Archive     -> ArchiveScreen(viewModel)
            NavState.Settings    -> SettingsScreen(viewModel)
            NavState.About       -> AboutScreen(viewModel)
        }
    }
}
