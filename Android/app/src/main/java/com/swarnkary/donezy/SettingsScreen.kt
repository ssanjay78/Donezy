package com.swarnkary.donezy

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: HobbyViewModel) {
    val sound by viewModel.soundEnabled.collectAsState()
    val vibrate by viewModel.vibrateEnabled.collectAsState()
    val streakRescue by viewModel.streakRescueEnabled.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    var showThemeDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    BackHandler { viewModel.goHome() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.goHome() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SettingsSectionHeader("Notifications")

            @Suppress("DEPRECATION")
            SettingsToggleRow(
                icon = Icons.Default.VolumeUp,
                title = "Sound",
                subtitle = "Play the system notification sound when a reminder fires",
                checked = sound,
                onChange = { viewModel.setSoundEnabled(it) }
            )
            SettingsToggleRow(
                icon = Icons.Default.Vibration,
                title = "Vibrate",
                subtitle = "Vibrate the phone when a reminder fires",
                checked = vibrate,
                onChange = { viewModel.setVibrateEnabled(it) }
            )
            SettingsToggleRow(
                icon = Icons.Default.LocalFireDepartment,
                title = "Streak rescue",
                subtitle = "Get a 23:30 nudge if you're at risk of breaking a streak",
                checked = streakRescue,
                onChange = { viewModel.setStreakRescueEnabled(it) }
            )
            @Suppress("DEPRECATION")
            SettingsClickRow(
                icon = Icons.Default.OpenInNew,
                title = "System notification settings",
                subtitle = "Manage channel importance, lock-screen visibility, and Do Not Disturb",
                onClick = {
                    // minSdk = 26 so APP_NOTIFICATION_SETTINGS is always available.
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingsSectionHeader("Appearance")
            SettingsClickRow(
                icon = Icons.Default.DarkMode,
                title = "Theme",
                subtitle = themeMode.label,
                onClick = { showThemeDialog = true }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingsSectionHeader("Tip")
            Text(
                "Reminders show as a heads-up banner over other apps. Tapping the banner " +
                    "opens the matching tracker so you can log it instantly.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Theme") },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = mode == themeMode,
                                onClick = { viewModel.setThemeMode(mode); showThemeDialog = false }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(mode.label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showThemeDialog = false }) { Text("Done") } }
        )
    }
}

@Composable
private fun SettingsSectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 0.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        onClick = { onChange(!checked) }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun SettingsClickRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 0.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.Notifications, contentDescription = null, tint = Color.Transparent)
        }
    }
}
