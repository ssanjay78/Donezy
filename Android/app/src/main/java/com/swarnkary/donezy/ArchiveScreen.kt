package com.swarnkary.donezy

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(viewModel: HobbyViewModel) {
    val archived by viewModel.archivedHobbies.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler { viewModel.goHome() }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collectLatest { event ->
            val result = snackbarHostState.showSnackbar(
                message = event.message,
                actionLabel = event.action,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) event.onAction?.invoke()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Archived trackers") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.goHome() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        if (archived.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📦", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("No archived trackers", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text("Swipe a tracker on the home screen to archive it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(archived, key = { it.id }) { hobby ->
                val cat = categoryFor(hobby.category)
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CategoryAvatar(cat, size = 40)
                        Column(Modifier.weight(1f)) {
                            Text(hobby.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(cat.label, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FilledTonalButton(
                            onClick = { viewModel.restoreHobby(hobby.id, hobby.name) },
                            shape = MaterialTheme.shapes.large
                        ) {
                            Icon(Icons.Default.Restore, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Restore")
                        }
                    }
                }
            }
        }
    }
}
