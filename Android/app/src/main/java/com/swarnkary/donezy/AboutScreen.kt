package com.swarnkary.donezy

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(viewModel: HobbyViewModel) {
    val context = LocalContext.current
    val pkgInfo = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
    }
    val versionName = pkgInfo?.versionName ?: "—"
    val versionCode = pkgInfo?.let {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) it.longVersionCode
        else @Suppress("DEPRECATION") it.versionCode.toLong()
    } ?: 0L

    BackHandler { viewModel.goHome() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                        )
                    )
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("📓", fontSize = 36.sp)
                    Text("Donezy", style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary)
                    Text(
                        "Plan it · Do it · Done it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                }
            }

            AboutCard(
                title = "What it is",
                body = "A local-first tracker for plant care, mechanical keyboards, sneaker " +
                    "collecting, aquarium maintenance, brewing routines, study habits, " +
                    "medication, and similar life routines. Everything stays on your device."
            )

            AboutCard(
                title = "How it works",
                body = "Set a reminder for any tracker. When it fires you'll get a heads-up " +
                    "notification — tap it to jump straight to that tracker and log progress. " +
                    "Streaks, achievements, and weekly goals reward consistency."
            )

            AboutCard(
                title = "Privacy",
                body = "No backend. No analytics. No cloud sync. Your trackers, logs, and " +
                    "photos live in this app's private storage. Use Backup to export a JSON " +
                    "file you control."
            )

            AboutCard(
                title = "Privacy policy",
                body = "Read the full privacy policy at swarnkary.com/donezy/privacy. " +
                    "Short version: nothing leaves your device unless you explicitly export a backup."
            )

            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Version $versionName · build $versionCode",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "© Swarnkary · swarnkary.com",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AboutCard(title: String, body: String) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
