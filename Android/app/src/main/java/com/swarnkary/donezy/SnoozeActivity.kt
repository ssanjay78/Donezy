package com.swarnkary.donezy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Translucent, no-history activity launched by the reminder notification's "Snooze" action.
 * Presents the snooze interval choices; on selection it broadcasts the commit intent back to
 * [ReminderReceiver] (which re-arms the alarm) and finishes. Themed as a dialog so the
 * launcher/home screen stays visible behind it.
 */
class SnoozeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val hobbyId   = intent.snoozeHobbyId()
        val hobbyName = intent.snoozeHobbyName()
        if (hobbyId <= 0L) { finish(); return }

        // The picker is replacing the notification's call to action, so clear it now.
        cancelReminderNotification(this, hobbyId)

        setContent {
            HobbyLogTheme {
                SnoozeDialog(
                    hobbyName = hobbyName,
                    options = snoozeOptions(),
                    onPick = { option ->
                        sendBroadcast(snoozeCommitIntent(this, hobbyId, hobbyName, option.triggerAt))
                        finish()
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }
}

@Composable
private fun SnoozeDialog(
    hobbyName: String,
    options: List<SnoozeOption>,
    onPick: (SnoozeOption) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Snooze, contentDescription = null) },
        title = { Text("Remind me again in…") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    hobbyName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                options.forEach { option ->
                    ListItem(
                        headlineContent = { Text(option.label) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(option) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
