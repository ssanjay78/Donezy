package com.example.hobbylog

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.mutableLongStateOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(viewModel: HobbyViewModel) {
    val detail by viewModel.detail.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var logEntry      by remember { mutableStateOf("") }
    var logRating     by remember { mutableStateOf<Int?>(null) }
    var reminderHours by remember { mutableLongStateOf(24L) }
    var recurring     by remember { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val editSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Hardware/gesture back
    BackHandler { viewModel.goHome() }

    // Snackbar events
    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collectLatest { event ->
            snackbarHostState.showSnackbar(event.message, event.action, duration = SnackbarDuration.Short)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(detail?.hobby?.name ?: "Tracker", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.goHome() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.exportLogs(context) }) {
                        Icon(Icons.Default.Share, contentDescription = "Export")
                    }
                    IconButton(onClick = { showEditSheet = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    // Overflow menu
                    var showOverflow by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                            detail?.hobby?.let { h ->
                                DropdownMenuItem(
                                    text = { Text(if (h.isPinned) "Unpin" else "Pin to top") },
                                    leadingIcon = { Icon(if (h.isPinned) Icons.Outlined.PushPin else Icons.Filled.PushPin, null) },
                                    onClick = { viewModel.togglePin(h); showOverflow = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Archive") },
                                    leadingIcon = { Icon(Icons.Default.Archive, null) },
                                    onClick = { viewModel.archiveHobby(h.id, h.name); showOverflow = false }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { showDeleteDialog = true; showOverflow = false }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->

        val currentDetail = detail

        if (currentDetail == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val hobby     = currentDetail.hobby
        val category  = categoryFor(hobby.category)
        val logs      = currentDetail.logs
        val streak    = HobbyViewModel.computeStreak(logs)
        val daysSince = HobbyViewModel.daysSinceLastLog(logs)
        val quickLogs = quickLogPresetsFor(hobby.category)

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Gradient hero card ────────────────────────────────────────────
            item { DetailHero(hobby, category, logs.size, streak, daysSince) }

            // ── Quick log chips ───────────────────────────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader("Quick log", "Tap to save instantly")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(quickLogs) { preset ->
                                AssistChip(
                                    onClick = {
                                        viewModel.addLog(hobby.id, preset.entry, null)
                                    },
                                    label = { Text(preset.label) },
                                    leadingIcon = { Text(category.emoji, fontSize = 14.sp) }
                                )
                            }
                        }
                    }
                }
            }

            // ── Manual log entry ──────────────────────────────────────────────
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader("Write a log entry", "Detailed note")
                        OutlinedTextField(
                            value = logEntry,
                            onValueChange = { logEntry = it },
                            label = { Text("What happened?") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            shape = MaterialTheme.shapes.medium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Rate this session", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))
                                StarRatingRow(rating = logRating, onRate = { logRating = it })
                            }
                            Button(
                                onClick = {
                                    viewModel.addLog(hobby.id, logEntry, logRating)
                                    logEntry = ""
                                    logRating = null
                                },
                                enabled = logEntry.isNotBlank(),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Save")
                            }
                        }
                    }
                }
            }

            // ── Reminder section ──────────────────────────────────────────────
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader("Reminder", "Set a follow-up")
                        ReminderSelector(
                            options = reminderOptions.filter { it.hours > 0L },
                            selectedHours = reminderHours,
                            onSelected = { reminderHours = it }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Auto-recurring", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                Text("Re-schedules after each alert", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = recurring, onCheckedChange = { recurring = it })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { viewModel.setReminder(hobby, reminderHours, recurring) },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Icon(Icons.Default.Notifications, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Set reminder")
                            }
                            if (hobby.nextReminderAt != null) {
                                OutlinedButton(
                                    onClick = { viewModel.clearReminder(hobby) },
                                    shape = MaterialTheme.shapes.large
                                ) {
                                    Icon(Icons.Default.NotificationsOff, null, Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            // ── Log history header ────────────────────────────────────────────
            item {
                SectionHeader("Log history", "${logs.size} entries")
            }

            // ── Empty log state ───────────────────────────────────────────────
            if (logs.isEmpty()) {
                item { EmptyLogState() }
            }

            // ── Log cards with swipe-to-delete ────────────────────────────────
            items(logs, key = { it.id }) { log ->
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value != SwipeToDismissBoxValue.Settled) {
                            viewModel.deleteLog(hobby.id, log.id)
                            true
                        } else false
                    }
                )
                SwipeToDismissBox(
                    state = dismissState,
                    modifier = Modifier.animateItem(),
                    backgroundContent = { LogSwipeBackground(dismissState.targetValue) },
                    content = { LogCard(log, onDelete = { viewModel.deleteLog(hobby.id, log.id) }) },
                    enableDismissFromStartToEnd = false
                )
            }
        }
    }

    // ── Edit sheet ────────────────────────────────────────────────────────────
    if (showEditSheet) {
        CreateTrackerSheet(
            sheetState = editSheetState,
            onDismiss = { showEditSheet = false },
            onSave = { name, cat, notes, _, _ ->
                detail?.hobby?.let { h ->
                    viewModel.updateHobby(h.id, name, cat, notes)
                }
                showEditSheet = false
            },
            initial = detail?.hobby
        )
    }

    // ── Delete confirmation ───────────────────────────────────────────────────
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete tracker?") },
            text = { Text("This will permanently delete \"${detail?.hobby?.name}\" and all ${detail?.logs?.size ?: 0} log entries. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        detail?.hobby?.let { h -> viewModel.deleteHobby(h.id, h.name) }
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ─── Detail hero card ─────────────────────────────────────────────────────────

@Composable
fun DetailHero(
    hobby: Hobby,
    category: CategoryOption,
    logCount: Int,
    streak: Int,
    daysSince: Long?
) {
    val status = reminderStatusInfo(hobby.nextReminderAt)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        category.accent,
                        category.accent.copy(alpha = 0.6f),
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryAvatar(category, size = 60)
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(category.emoji + "  " + category.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.85f))
                        if (hobby.isPinned) Icon(Icons.Default.PushPin, contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
                    }
                    Text(hobby.name, style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            if (hobby.notes.isNotBlank()) {
                Text(hobby.notes, style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f), maxLines = 3, overflow = TextOverflow.Ellipsis)
            }

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HeroDetailMetric("Logs", "$logCount", Modifier.weight(1f))
                HeroDetailMetric(
                    "Streak",
                    if (streak > 0) "🔥 $streak" else "—",
                    Modifier.weight(1f)
                )
                HeroDetailMetric(
                    "Last log",
                    when {
                        daysSince == null -> "Never"
                        daysSince == 0L   -> "Today"
                        daysSince == 1L   -> "Yesterday"
                        else              -> "${daysSince}d ago"
                    },
                    Modifier.weight(1f)
                )
            }

            // Reminder status
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Notifications, contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                Text(status.detail, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                if (hobby.reminderIntervalHours > 0L) {
                    Text("· ↺ recurring", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                }
            }

            Text(
                "Created ${formatDateShort(hobby.createdAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun HeroDetailMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.18f)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

// ─── Log Card ─────────────────────────────────────────────────────────────────

@Composable
fun LogCard(log: HobbyLog, onDelete: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formatDate(log.createdAt),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (log.rating != null) {
                        StarDisplay(log.rating)
                        Spacer(Modifier.width(4.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete log",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                }
            }
            Text(log.entry, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ─── Log swipe background ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogSwipeBackground(targetValue: SwipeToDismissBoxValue) {
    val color by animateColorAsState(
        targetValue = when (targetValue) {
            SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
            else -> Color.Transparent
        },
        animationSpec = tween(200),
        label = "logSwipeBg"
    )
    Box(
        modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.large).background(color),
        contentAlignment = Alignment.CenterEnd
    ) {
        AnimatedVisibility(
            visible = targetValue == SwipeToDismissBoxValue.EndToStart,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut()
        ) {
            Icon(
                Icons.Default.Delete, contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(end = 20.dp)
            )
        }
    }
}
