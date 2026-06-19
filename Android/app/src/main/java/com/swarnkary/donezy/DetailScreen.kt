package com.swarnkary.donezy

import kotlin.math.roundToInt

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.compose.AsyncImagePainter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(viewModel: HobbyViewModel) {
    val detail by viewModel.detail.collectAsState()
    val filteredLogs by viewModel.filteredLogs.collectAsState()
    var editingLog by remember { mutableStateOf<HobbyLog?>(null) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var logEntry      by remember { mutableStateOf("") }
    var logRating     by remember { mutableStateOf<Int?>(null) }
    var pendingPhoto  by remember { mutableStateOf<String?>(null) }
    var showEditSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPhotoSourceSheet by remember { mutableStateOf(false) }
    val editSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                pendingPhoto = viewModel.importPhoto(uri)
            }
        }
    }

    // Camera capture: pre-allocate a target file, hand its content-Uri to the camera, then
    // commit the matching file:// URI only if the capture succeeded (otherwise clean it up).
    var pendingCameraTarget by remember { mutableStateOf<HobbyViewModel.CameraTarget?>(null) }
    val cameraCapture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val target = pendingCameraTarget
        pendingCameraTarget = null
        if (target == null) return@rememberLauncherForActivityResult
        if (success) pendingPhoto = target.fileUri
        else viewModel.discardCameraTarget(target.fileUri)
    }

    BackHandler { viewModel.goHome() }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collectLatest { event ->
            if (event.message.contains("unlocked")) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
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
            item { DetailHero(hobby, category, logs.size, streak, daysSince) }

            // Insights chart
            if (logs.isNotEmpty()) {
                item { InsightsCard(logs) }
            }

            // Achievements + weekly goal progress
            if (logs.isNotEmpty() || hobby.weeklyGoal > 0) {
                item {
                    val snapshot = remember(hobby, logs) { AchievementSnapshot.from(hobby, logs) }
                    val earned = remember(snapshot) { Achievements.earned(snapshot) }
                    if (hobby.weeklyGoal > 0 || earned.isNotEmpty()) {
                        AchievementCard(snapshot, earned, hobby.name)
                    }
                }
            }

            // Quick log chips
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader("Quick log", subtitle = "Tap to save instantly")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(quickLogs) { preset ->
                                AssistChip(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.addLog(hobby.id, preset.entry, null)
                                    },
                                    label = { Text(preset.label) }
                                )
                            }
                        }
                    }
                }
            }

            // Manual log entry
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader("Write a log entry", subtitle = "Detailed note")
                        OutlinedTextField(
                            value = logEntry,
                            onValueChange = { logEntry = it },
                            label = { Text("What happened?") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            shape = MaterialTheme.shapes.medium
                        )

                        // Photo preview + add/remove
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { showPhotoSourceSheet = true },
                                shape = MaterialTheme.shapes.large
                            ) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (pendingPhoto == null) "Add photo" else "Replace photo")
                            }
                            if (pendingPhoto != null) {
                                IconButton(onClick = { pendingPhoto = null }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove photo")
                                }
                                Spacer(Modifier.weight(1f))
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    val painter = rememberAsyncImagePainter(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(pendingPhoto)
                                            .crossfade(true)
                                            .build()
                                    )
                                    androidx.compose.foundation.Image(
                                        painter = painter,
                                        contentDescription = "Pending photo preview",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (painter.state is AsyncImagePainter.State.Error) {
                                        Box(
                                            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.BrokenImage,
                                                contentDescription = "Error loading image",
                                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

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
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.addLog(hobby.id, logEntry, logRating, pendingPhoto)
                                    logEntry = ""
                                    logRating = null
                                    pendingPhoto = null
                                },
                                enabled = logEntry.isNotBlank() || pendingPhoto != null,
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

            // Reminder summary card with edit/clear
            item {
                ReminderSummaryCard(
                    hobby = hobby,
                    onEdit = { showEditSheet = true },
                    onClear = { viewModel.clearReminder(hobby) }
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(
                            "Log history",
                            subtitle = if (filteredLogs.size == logs.size) "${logs.size} entries" else "${filteredLogs.size} of ${logs.size} shown"
                        )
                        
                        var showRangePicker by remember { mutableStateOf(false) }
                        
                        IconButton(onClick = { showRangePicker = true }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Filter by date")
                        }
                        
                        if (showRangePicker) {
                            val state = rememberDateRangePickerState()
                            DatePickerDialog(
                                onDismissRequest = { showRangePicker = false },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            val start = state.selectedStartDateMillis
                                            val end = state.selectedEndDateMillis
                                            if (start != null && end != null) {
                                                viewModel.filterLogsByDateRange(hobby.id, start, end)
                                            }
                                            showRangePicker = false
                                        },
                                        enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null
                                    ) {
                                        Text("Apply")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showRangePicker = false }) {
                                        Text("Cancel")
                                    }
                                }
                            ) {
                                DateRangePicker(
                                    state = state,
                                    modifier = Modifier.weight(1f).padding(16.dp)
                                )
                            }
                        }
                    }
                    
                    if (filteredLogs.size < logs.size) {
                        InputChip(
                            selected = true,
                            onClick = { viewModel.clearLogDateFilter(hobby.id) },
                            label = { Text("Date Filter Active") },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear date filter",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }
                }
            }

            if (filteredLogs.isEmpty()) item { EmptyLogState() }

            items(filteredLogs, key = { it.id }) { log ->
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
                    content = {
                        LogCard(
                            log = log,
                            onEdit = { editingLog = log },
                            onDelete = { viewModel.deleteLog(hobby.id, log.id) }
                        )
                    },
                    enableDismissFromStartToEnd = false
                )
            }
        }
    }

    if (showPhotoSourceSheet) {
        PhotoSourceSheet(
            onDismiss = { showPhotoSourceSheet = false },
            onCamera = {
                showPhotoSourceSheet = false
                val target = viewModel.createCameraTarget()
                if (target != null) {
                    pendingCameraTarget = target
                    cameraCapture.launch(target.contentUri)
                }
            },
            onGallery = {
                showPhotoSourceSheet = false
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        )
    }

    if (showEditSheet) {
        CreateTrackerSheet(
            sheetState = editSheetState,
            onDismiss = { showEditSheet = false },
            onSave = { result ->
                detail?.hobby?.let { h ->
                    viewModel.updateHobby(h.id, result.name, result.category, result.notes, result.weeklyGoal)
                    if (result.reminder.reminderAt == null) viewModel.clearReminder(h)
                    else viewModel.setReminderAt(h, result.reminder.reminderAt, result.reminder.recurrence)
                }
                showEditSheet = false
            },
            initial = detail?.hobby
        )
    }

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

    if (editingLog != null) {
        val log = editingLog!!
        var entryText by remember(log.id) { mutableStateOf(log.entry) }
        var rating by remember(log.id) { mutableStateOf(log.rating) }
        var photoUri by remember(log.id) { mutableStateOf(log.photoUri) }

        val dialogPhotoPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia()
        ) { uri: Uri? ->
            if (uri != null) {
                scope.launch {
                    photoUri = viewModel.importPhoto(uri)
                }
            }
        }

        AlertDialog(
            onDismissRequest = { editingLog = null },
            title = { Text("Edit Log Entry") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = entryText,
                        onValueChange = { entryText = it },
                        label = { Text("Notes") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Column {
                        Text("Rating", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        StarRatingRow(rating = rating, onRate = { rating = it })
                    }

                    if (photoUri != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Photo attached", style = MaterialTheme.typography.bodySmall)
                            IconButton(onClick = { photoUri = null }) {
                                Icon(Icons.Default.Clear, "Remove photo")
                            }
                        }
                    } else {
                        TextButton(onClick = {
                            dialogPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) {
                            Icon(Icons.Default.CameraAlt, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Add Photo")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        detail?.hobby?.let { h ->
                            viewModel.updateLog(h.id, log.id, entryText, rating, photoUri)
                        }
                        editingLog = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingLog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/** Bottom sheet letting the user pick where a log photo comes from: camera or gallery. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoSourceSheet(
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                "Add a photo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
            )
            ListItem(
                headlineContent = { Text("Take photo") },
                supportingContent = { Text("Open the camera") },
                leadingContent = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onCamera)
            )
            ListItem(
                headlineContent = { Text("Choose from gallery") },
                supportingContent = { Text("Pick an existing photo") },
                leadingContent = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onGallery)
            )
        }
    }
}

// ─── Achievements + weekly goal ───────────────────────────────────────────────

@Composable
fun AchievementCard(snapshot: AchievementSnapshot, earned: List<Achievement>, hobbyName: String) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Achievements", subtitle = "${earned.size} earned")

            if (snapshot.weeklyGoal > 0) {
                val progress = (snapshot.logsThisWeek.toFloat() / snapshot.weeklyGoal).coerceIn(0f, 1f)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("This week", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "${snapshot.logsThisWeek} / ${snapshot.weeklyGoal}",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (snapshot.logsThisWeek >= snapshot.weeklyGoal)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = if (snapshot.logsThisWeek >= snapshot.weeklyGoal)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            if (earned.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(earned) { achievement -> AchievementChip(achievement, hobbyName) }
                }
            } else if (snapshot.weeklyGoal == 0) {
                Text(
                    "Save a log to start unlocking achievements.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AchievementChip(achievement: Achievement, hobbyName: String) {
    val context = LocalContext.current
    Surface(
        onClick = {
            shareAchievementCard(context, achievement, hobbyName)
        },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(achievement.emoji)
            Text(
                achievement.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "Share",
                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

private fun shareAchievementCard(context: Context, achievement: Achievement, hobbyName: String) {
    val width = 800
    val height = 800
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Draw background gradient
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val gradient = LinearGradient(
        0f, 0f, width.toFloat(), height.toFloat(),
        intArrayOf(0xFF6366F1.toInt(), 0xFFA855F7.toInt(), 0xFFEC4899.toInt()),
        null,
        Shader.TileMode.CLAMP
    )
    paint.shader = gradient
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    paint.shader = null

    // Draw card overlay
    paint.color = 0x22FFFFFF
    val margin = 50f
    val rect = RectF(margin, margin, width - margin, height - margin)
    canvas.drawRoundRect(rect, 36f, 36f, paint)

    // Draw card border
    paint.color = 0x55FFFFFF
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 3f
    canvas.drawRoundRect(rect, 36f, 36f, paint)
    paint.style = Paint.Style.FILL

    // Draw emoji
    paint.textSize = 120f
    paint.textAlign = Paint.Align.CENTER
    paint.color = 0xFFFFFFFF.toInt()
    canvas.drawText(achievement.emoji, width / 2f, 280f, paint)

    // Draw achievement title
    paint.textSize = 44f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    canvas.drawText(achievement.title, width / 2f, 410f, paint)

    // Draw tagline
    paint.textSize = 28f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    paint.color = 0xEEFFFFFF.toInt()
    
    val tagline = achievement.tagline
    val words = tagline.split(" ")
    val line1 = StringBuilder()
    val line2 = StringBuilder()
    var line1Done = false
    for (word in words) {
        if (!line1Done && line1.length + word.length < 32) {
            if (line1.isNotEmpty()) line1.append(" ")
            line1.append(word)
        } else {
            line1Done = true
            if (line2.isNotEmpty()) line2.append(" ")
            line2.append(word)
        }
    }
    
    if (line2.isEmpty()) {
        canvas.drawText(line1.toString(), width / 2f, 490f, paint)
    } else {
        canvas.drawText(line1.toString(), width / 2f, 475f, paint)
        canvas.drawText(line2.toString(), width / 2f, 515f, paint)
    }

    // Draw tracker context
    paint.textSize = 26f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
    paint.color = 0xCCFFFFFF.toInt()
    canvas.drawText("Unlocked in $hobbyName", width / 2f, 580f, paint)

    // Draw app name / branding
    paint.textSize = 36f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.color = 0xFFFFFFFF.toInt()
    canvas.drawText("Donezy", width / 2f, 680f, paint)

    paint.textSize = 20f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    paint.color = 0x88FFFFFF.toInt()
    canvas.drawText("Plan it · Do it · Done it.", width / 2f, 715f, paint)

    // Save bitmap to file and share
    val dir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
    val file = java.io.File(dir, "achievement_${achievement.id}.png")
    try {
        java.io.FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Achievement Unlocked: ${achievement.title}!")
            putExtra(Intent.EXTRA_TEXT, "I just unlocked the '${achievement.title}' achievement in Donezy for '$hobbyName'! ${achievement.emoji}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Achievement"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// ─── Insights chart ───────────────────────────────────────────────────────────

@Composable
fun InsightsCard(logs: List<HobbyLog>) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember(LocalDate.now(zone)) { LocalDate.now(zone) }
    // 30-day window, oldest at the start so today is rightmost (matches how charts read).
    val windowDays = 30
    val days = remember(today) { (0 until windowDays).map { today.minusDays((windowDays - 1 - it).toLong()) } }

    val countsByDay: Map<LocalDate, Int> = remember(logs) {
        logs.groupBy { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }
            .mapValues { it.value.size }
    }
    val totalEntries = countsByDay.values.sum()
    val maxCount = (countsByDay.values.maxOrNull() ?: 0).coerceAtLeast(1)

    val weekdayFmt = remember { java.time.format.DateTimeFormatter.ofPattern("EEE") }
    val dayFmt = remember { java.time.format.DateTimeFormatter.ofPattern("d MMM") }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(days.size) {
        // Land at today (the last cell) on first composition.
        listState.scrollToItem((days.size - 1).coerceAtLeast(0))
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Insights", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Scroll to see earlier days", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("$totalEntries", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                    Text("entries", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            androidx.compose.foundation.lazy.LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(days.size) { idx ->
                    val date = days[idx]
                    val count = countsByDay[date] ?: 0
                    DayCandle(
                        date = date,
                        count = count,
                        maxCount = maxCount,
                        isToday = date == today,
                        weekdayFmt = weekdayFmt,
                        dayFmt = dayFmt
                    )
                }
            }

            val ratedLogs = remember(logs) { logs.filter { it.rating != null } }
            val avgRating = remember(ratedLogs) {
                if (ratedLogs.isNotEmpty()) ratedLogs.map { it.rating!! }.average() else null
            }

            val timeOfDayDistribution = remember(logs) {
                val distribution = linkedMapOf("Morning" to 0, "Afternoon" to 0, "Evening" to 0, "Night" to 0)
                logs.forEach { log ->
                    val hour = Instant.ofEpochMilli(log.createdAt).atZone(zone).hour
                    when (hour) {
                        in 5..11 -> distribution["Morning"] = distribution["Morning"]!! + 1
                        in 12..16 -> distribution["Afternoon"] = distribution["Afternoon"]!! + 1
                        in 17..20 -> distribution["Evening"] = distribution["Evening"]!! + 1
                        else -> distribution["Night"] = distribution["Night"]!! + 1
                    }
                }
                distribution
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (avgRating != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Avg. rating:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        StarDisplay(avgRating.roundToInt())
                        Text(
                            String.format("%.1f / 5.0", avgRating),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text("Time of Day Distribution", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val maxDist = timeOfDayDistribution.values.maxOrNull()?.coerceAtLeast(1) ?: 1
                    timeOfDayDistribution.forEach { (label, count) ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(count.toFloat() / maxDist)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                            Text("$count logs", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCandle(
    date: LocalDate,
    count: Int,
    maxCount: Int,
    isToday: Boolean,
    weekdayFmt: java.time.format.DateTimeFormatter,
    dayFmt: java.time.format.DateTimeFormatter
) {
    val barTrackHeight = 96.dp
    val fillFraction = if (count == 0) 0f else (count.toFloat() / maxCount).coerceIn(0f, 1f)

    Column(
        modifier = Modifier.width(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            weekdayFmt.format(date),
            style = MaterialTheme.typography.labelSmall,
            color = if (isToday) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium
        )
        Text(
            dayFmt.format(date),
            style = MaterialTheme.typography.labelSmall,
            color = if (isToday) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )

        // Bar track + filled bar, with the bar growing upward from the baseline.
        Box(
            modifier = Modifier
                .height(barTrackHeight)
                .width(20.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            if (count > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fillFraction)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                        .background(
                            if (isToday) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.tertiary
                        )
                )
            }
        }

        Text(
            if (count == 0) "—" else "$count",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (count == 0) MaterialTheme.colorScheme.onSurfaceVariant
                    else if (isToday) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
        )
    }
}

// ─── Reminder summary card ────────────────────────────────────────────────────

@Composable
fun ReminderSummaryCard(hobby: Hobby, onEdit: () -> Unit, onClear: () -> Unit) {
    val status = reminderStatusInfo(hobby.nextReminderAt)
    val rule = when (val r = hobby.recurrence) {
        Recurrence.None       -> "One-shot"
        is Recurrence.Hourly  -> "Repeats every ${reminderLabel(r.hours)}"
        Recurrence.Daily      -> "Repeats daily"
        is Recurrence.Weekly  -> "Repeats weekly · ${weeklyMaskLabel(r.dayMask)}"
        is Recurrence.Monthly -> "Repeats monthly on day ${r.dayOfMonth}"
    }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Reminder", subtitle = status.label)
            Text(
                hobby.nextReminderAt?.let { formatDate(it) } ?: "No reminder set",
                style = MaterialTheme.typography.bodyMedium
            )
            if (hobby.nextReminderAt != null) {
                Text(rule, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(Icons.Default.Notifications, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (hobby.nextReminderAt == null) "Set reminder" else "Edit")
                }
                if (hobby.nextReminderAt != null) {
                    OutlinedButton(
                        onClick = onClear,
                        shape = MaterialTheme.shapes.large
                    ) {
                        Icon(Icons.Default.NotificationsOff, null, Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

private fun weeklyMaskLabel(mask: Int): String {
    val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val out = (0..6).filter { (mask shr it) and 1 == 1 }.map { names[it] }
    return if (out.isEmpty()) "No days picked" else out.joinToString(", ")
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
    val onHero = Color.White

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
                            color = onHero.copy(alpha = 0.85f))
                        if (hobby.isPinned) Icon(Icons.Default.PushPin, contentDescription = null,
                            tint = onHero.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
                    }
                    Text(hobby.name, style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, color = onHero)
                }
            }

            if (hobby.notes.isNotBlank()) {
                Text(hobby.notes, style = MaterialTheme.typography.bodyMedium,
                    color = onHero.copy(alpha = 0.85f), maxLines = 3, overflow = TextOverflow.Ellipsis)
            }

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

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Notifications, contentDescription = null,
                    tint = onHero.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
                Text(status.detail, style = MaterialTheme.typography.bodySmall, color = onHero.copy(alpha = 0.9f))
                if (hobby.recurrence !is Recurrence.None) {
                    Text("· ↺ recurring", style = MaterialTheme.typography.bodySmall, color = onHero.copy(alpha = 0.75f))
                }
            }

            Text(
                "Created ${formatDateShort(hobby.createdAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = onHero.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun HeroDetailMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f))
        }
    }
}

// ─── Log Card ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogCard(log: HobbyLog, onEdit: () -> Unit, onDelete: () -> Unit) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = onEdit
            ),
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
                    IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete log",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }
            }
            if (log.entry.isNotBlank()) {
                Text(log.entry, style = MaterialTheme.typography.bodyMedium)
            }
            log.photoUri?.let { uri ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    val painter = rememberAsyncImagePainter(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(uri)
                            .crossfade(true)
                            .build()
                    )
                    androidx.compose.foundation.Image(
                        painter = painter,
                        contentDescription = "Log entry photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    val isError = painter.state is AsyncImagePainter.State.Error
                    if (isError) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(
                                Icons.Default.BrokenImage,
                                contentDescription = "Broken image",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Image not found",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

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
