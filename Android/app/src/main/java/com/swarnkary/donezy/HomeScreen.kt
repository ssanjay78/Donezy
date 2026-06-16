package com.swarnkary.donezy

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HobbyViewModel) {
    val hobbies by viewModel.hobbies.collectAsState()
    val logDaysByHobby by viewModel.logDaysByHobby.collectAsState()
    val logSearchResults by viewModel.logSearchResults.collectAsState()
    val logSearchQuery by viewModel.logSearchQuery.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showCreateSheet  by remember { mutableStateOf(false) }
    var prefillTemplate  by remember { mutableStateOf<HobbyTemplate?>(null) }
    val createSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedFilter by remember { mutableStateOf("All") }
    var searchQuery    by remember { mutableStateOf("") }
    var sortBy         by remember { mutableStateOf(SortBy.DueSoon) }
    var showSortMenu   by remember { mutableStateOf(false) }
    var showOverflow   by remember { mutableStateOf(false) }
    var heroFilter     by remember { mutableStateOf(HeroFilter.All) }

    // Backup / restore via SAF
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? -> if (uri != null) viewModel.backupTo(context, uri) }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> if (uri != null) viewModel.restoreFrom(context, uri) }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(hobbies) {
        // Tick at whatever cadence the *closest* upcoming reminder demands so the
        // "In 42s" countdown stays honest without burning the CPU when nothing is near.
        while (true) {
            val anchor = System.currentTimeMillis()
            val nearest = hobbies
                .mapNotNull { it.nextReminderAt }
                .minOfOrNull { kotlin.math.abs(it - anchor) }
                ?: Long.MAX_VALUE
            val sleep = durationRefreshIntervalMs(nearest)
            kotlinx.coroutines.delay(sleep)
            now = System.currentTimeMillis()
        }
    }

    val midnightTonight = remember(now) {
        java.time.LocalDate.now(java.time.ZoneId.systemDefault())
            .plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    val dueSoon   = remember(hobbies, midnightTonight, now) {
        hobbies.count { it.nextReminderAt != null && it.nextReminderAt <= midnightTonight && it.nextReminderAt >= now - DAY_MS }
    }
    val scheduled = remember(hobbies) { hobbies.count { it.nextReminderAt != null } }

    val streakByHobby = remember(logDaysByHobby) {
        logDaysByHobby.mapValues { (_, days) -> HobbyViewModel.computeStreakFromDays(days) }
    }
    val longestStreak = remember(streakByHobby) { streakByHobby.values.maxOrNull() ?: 0 }
    val activeStreaks = remember(streakByHobby) { streakByHobby.values.count { it >= 1 } }

    val filterLabels = remember(hobbies) {
        listOf("All") + hobbies.map { it.category }.distinct().filter { it.isNotBlank() }.sorted()
    }

    val filteredHobbies = remember(hobbies, selectedFilter, searchQuery, sortBy, heroFilter, streakByHobby) {
        val q = searchQuery.trim()
        hobbies
            .filter { h ->
                val matchCat = selectedFilter == "All" || h.category == selectedFilter
                val matchQ = q.isBlank() || h.name.contains(q, true) || h.category.contains(q, true) || h.notes.contains(q, true)
                val matchHero = when (heroFilter) {
                    HeroFilter.All       -> true
                    HeroFilter.DueSoon   -> h.nextReminderAt != null && h.nextReminderAt <= midnightTonight && h.nextReminderAt >= now - DAY_MS
                    HeroFilter.Scheduled -> h.nextReminderAt != null
                    HeroFilter.Streaks   -> (streakByHobby[h.id] ?: 0) >= 1
                }
                matchCat && matchQ && matchHero
            }
            .let { list ->
                when (sortBy) {
                    SortBy.DueSoon -> list.sortedWith(compareByDescending<Hobby> { it.isPinned }
                        .thenBy { it.nextReminderAt == null }
                        .thenBy { it.nextReminderAt ?: Long.MAX_VALUE })
                    SortBy.RecentActivity -> list.sortedWith(compareByDescending<Hobby> { it.isPinned }
                        .thenByDescending { it.createdAt })
                    SortBy.Alphabetical -> list.sortedWith(compareByDescending<Hobby> { it.isPinned }
                        .thenBy { it.name.lowercase() })
                }
            }
    }

    // Trigger log search whenever the user-visible query changes
    LaunchedEffect(searchQuery) {
        viewModel.setLogSearchQuery(searchQuery)
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collectLatest { event ->
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = event.message,
                    actionLabel = event.action,
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) event.onAction?.invoke()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Donezy", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Plan it · Do it · Done it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            @Suppress("DEPRECATION")
                            Icon(Icons.Default.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            SortBy.entries.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s.label) },
                                    onClick = { sortBy = s; showSortMenu = false },
                                    trailingIcon = {
                                        if (s == sortBy) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Default.Settings, null) },
                                onClick = { showOverflow = false; viewModel.openSettings() }
                            )
                            DropdownMenuItem(
                                text = { Text("Archive") },
                                leadingIcon = { Icon(Icons.Default.Archive, null) },
                                onClick = { showOverflow = false; viewModel.openArchive() }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Backup to file") },
                                leadingIcon = { Icon(Icons.Default.SaveAlt, null) },
                                onClick = {
                                    showOverflow = false
                                    backupLauncher.launch("hobbylog-backup-${System.currentTimeMillis()}.json")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Restore from file") },
                                leadingIcon = { Icon(Icons.Default.Restore, null) },
                                onClick = {
                                    showOverflow = false
                                    restoreLauncher.launch(arrayOf("application/json", "*/*"))
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("About") },
                                leadingIcon = { Icon(Icons.Default.Info, null) },
                                onClick = { showOverflow = false; viewModel.openAbout() }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { prefillTemplate = null; showCreateSheet = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New tracker") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    ) { padding ->

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                DashboardHero(
                    total = hobbies.size,
                    dueSoon = dueSoon,
                    scheduled = scheduled,
                    longestStreak = longestStreak,
                    activeStreaks = activeStreaks,
                    selected = heroFilter,
                    onSelect = { tapped ->
                        // Tap again to clear the filter.
                        heroFilter = if (heroFilter == tapped) HeroFilter.All else tapped
                    }
                )
            }

            if (hobbies.isEmpty()) {
                item {
                    TemplateDeck(onApply = { template ->
                        prefillTemplate = template
                        showCreateSheet = true
                    })
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("Trackers", subtitle = "${filteredHobbies.size} shown")
                    CategoryFilterRow(
                        labels = filterLabels,
                        selected = selectedFilter,
                        onSelected = { selectedFilter = it }
                    )
                    AnimatedVisibility(visible = hobbies.isNotEmpty()) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Search trackers and logs") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear")
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large
                        )
                    }
                }
            }

            // Log search results section (only when there's a query and any matches)
            if (searchQuery.isNotBlank() && logSearchResults.isNotEmpty()) {
                item {
                    SectionHeader("Log entries matching \"$logSearchQuery\"", subtitle = "${logSearchResults.size} found")
                }
                items(logSearchResults, key = { "log-${it.log.id}" }) { hit ->
                    LogSearchHitCard(
                        hit = hit,
                        onClick = { viewModel.openDetail(hit.log.hobbyId) }
                    )
                }
                item { Spacer(Modifier.height(4.dp)) }
            }

            if (filteredHobbies.isEmpty()) {
                item {
                    EmptyTrackerState(hasTrackers = hobbies.isNotEmpty(), onClear = {
                        selectedFilter = "All"; searchQuery = ""
                    })
                }
            }

            items(filteredHobbies, key = { it.id }) { hobby ->
                SwipeToConfirmRow(
                    modifier = Modifier.animateItem(),
                    onArchive = { scope.launch { viewModel.archiveHobby(hobby.id, hobby.name) } },
                    onDelete = { scope.launch { viewModel.deleteHobby(hobby.id, hobby.name) } }
                ) {
                    TrackerCard(
                        hobby = hobby,
                        streak = streakByHobby[hobby.id] ?: 0,
                        now = now,
                        onClick = { viewModel.openDetail(hobby.id) },
                        onPin = { viewModel.togglePin(hobby) }
                    )
                }
            }
        }
    }

    if (showCreateSheet) {
        CreateTrackerSheet(
            sheetState = createSheetState,
            onDismiss = { showCreateSheet = false; prefillTemplate = null },
            onSave = { result ->
                viewModel.addHobby(
                    name = result.name,
                    category = result.category,
                    notes = result.notes,
                    reminderAt = result.reminder.reminderAt,
                    recurrence = result.reminder.recurrence,
                    weeklyGoal = result.weeklyGoal
                )
                showCreateSheet = false
                prefillTemplate = null
            },
            prefill = prefillTemplate
        )
    }

}

// ─── Log search hit card ──────────────────────────────────────────────────────

@Composable
private fun LogSearchHitCard(hit: LogSearchHit, onClick: () -> Unit) {
    val cat = categoryFor(hit.hobbyCategory)
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(cat.emoji)
                Text(hit.hobbyName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(formatDateShort(hit.log.createdAt), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                hit.log.entry,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─── Gradient dashboard hero ──────────────────────────────────────────────────

enum class HeroFilter { All, DueSoon, Scheduled, Streaks }

@Composable
fun DashboardHero(
    total: Int,
    dueSoon: Int,
    scheduled: Int,
    longestStreak: Int,
    activeStreaks: Int,
    selected: HeroFilter,
    onSelect: (HeroFilter) -> Unit
) {
    val onHero = MaterialTheme.colorScheme.onPrimary
    val title = when (selected) {
        HeroFilter.All       -> "Today at a glance"
        HeroFilter.DueSoon   -> "Showing trackers due before midnight · tap to clear"
        HeroFilter.Scheduled -> "Showing scheduled trackers · tap to clear"
        HeroFilter.Streaks   -> "Showing trackers logged today or yesterday · tap to clear"
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = onHero
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HeroMetric(
                    label = "Trackers",
                    value = "$total",
                    valueColor = onHero,
                    selected = selected == HeroFilter.All,
                    onClick = { onSelect(HeroFilter.All) },
                    modifier = Modifier.weight(1f)
                )
                HeroMetric(
                    label = "Due soon",
                    value = "$dueSoon",
                    valueColor = if (dueSoon > 0) Color(0xFFFFCC80) else onHero,
                    selected = selected == HeroFilter.DueSoon,
                    onClick = { onSelect(HeroFilter.DueSoon) },
                    modifier = Modifier.weight(1f)
                )
                HeroMetric(
                    label = "Scheduled",
                    value = "$scheduled",
                    valueColor = onHero,
                    selected = selected == HeroFilter.Scheduled,
                    onClick = { onSelect(HeroFilter.Scheduled) },
                    modifier = Modifier.weight(1f)
                )
                HeroMetric(
                    label = if (activeStreaks > 1) "Best streak" else "Streak",
                    value = if (longestStreak > 0) "🔥 $longestStreak" else "—",
                    valueColor = if (longestStreak > 0) Color(0xFFFFD180) else onHero,
                    selected = selected == HeroFilter.Streaks,
                    onClick = { onSelect(HeroFilter.Streaks) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun HeroMetric(
    label: String,
    value: String,
    valueColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) Color.White.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.16f)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = bg,
        tonalElevation = 0.dp,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = valueColor)
            Text(label, style = MaterialTheme.typography.labelSmall, color = valueColor.copy(alpha = 0.9f))
        }
    }
}

// ─── Tracker Card ─────────────────────────────────────────────────────────────

@Composable
fun TrackerCard(hobby: Hobby, streak: Int, now: Long, onClick: () -> Unit, onPin: () -> Unit) {
    val category = categoryFor(hobby.category)
    val status   = reminderStatusInfo(hobby.nextReminderAt, now)

    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(IntrinsicSize.Min)
                    .background(
                        Brush.verticalGradient(listOf(category.accent, category.accent.copy(alpha = 0.3f)))
                    )
            )
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CategoryBadge(category)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(status)
                        if (hobby.isPinned) {
                            Icon(Icons.Default.PushPin, contentDescription = "Pinned",
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onPin, modifier = Modifier.size(48.dp)) {
                            Icon(
                                if (hobby.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = "Pin",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CategoryAvatar(category, size = 46)
                    Column(Modifier.weight(1f)) {
                        Text(hobby.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (hobby.notes.isNotBlank()) {
                            Text(
                                hobby.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Notifications, contentDescription = null,
                            modifier = Modifier.size(14.dp), tint = status.color.copy(alpha = 0.8f))
                        Text(status.detail, style = MaterialTheme.typography.labelSmall, color = status.color)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (streak >= 1) StreakBadge(streak = streak)
                        if (hobby.recurrence !is Recurrence.None) {
                            Text(
                                "↺ recurring",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Wraps a tracker card with swipe-to-confirm gestures. Unlike a plain swipe-to-dismiss,
 * the swipe doesn't act immediately — it slides the card aside to reveal an action button
 * (~30% of the width, enough for its label) and *holds* there. The destructive action only
 * runs when the user taps the revealed label, so an accidental swipe can be cancelled by
 * swiping back or tapping elsewhere.
 *
 *  • Swipe left  → reveals "Archive" on the right edge.
 *  • Swipe right → reveals "Delete" on the left edge.
 */
@Composable
fun SwipeToConfirmRow(
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    var rowWidthPx by remember { mutableStateOf(0f) }
    // Reveal 20% of the row — covers the action icon + label comfortably.
    val revealPx = rowWidthPx * 0.20f
    // Past 12% of the row the swipe "catches" and settles open; otherwise it springs closed.
    val catchPx = rowWidthPx * 0.12f

    // offset > 0 → card pushed right (Delete revealed on the left)
    // offset < 0 → card pushed left  (Archive revealed on the right)
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun settleTo(target: Float) = scope.launch {
        offsetX.animateTo(target, animationSpec = tween(durationMillis = 220))
    }

    val draggable = rememberDraggableState { delta ->
        scope.launch {
            val next = (offsetX.value + delta).coerceIn(-revealPx, revealPx)
            offsetX.snapTo(next)
        }
    }

    val revealedRight = offsetX.value < -1f   // Archive side open
    val revealedLeft  = offsetX.value > 1f    // Delete side open

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { rowWidthPx = it.width.toFloat() }
    ) {
        // Background action layer, revealed as the card slides away.
        Row(
            modifier = Modifier
                .matchParentSize()
                .clip(MaterialTheme.shapes.large),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side — Delete (revealed on right-swipe).
            SwipeAction(
                visible = revealedLeft,
                icon = Icons.Default.Delete,
                label = "Delete",
                // Sober, deep red — same family as the error palette but darker and
                // desaturated, so Delete reads as more serious than Archive's pale tone.
                container = Color(0xFF7A1F1A),
                onContent = Color(0xFFFFE9E6),
                alignEnd = false,
                widthPx = revealPx,
                density = density,
                onClick = { settleTo(0f); onDelete() }
            )
            Spacer(Modifier.weight(1f))
            // Right side — Archive (revealed on left-swipe).
            SwipeAction(
                visible = revealedRight,
                icon = Icons.Default.Archive,
                label = "Archive",
                container = MaterialTheme.colorScheme.errorContainer,
                onContent = MaterialTheme.colorScheme.onErrorContainer,
                alignEnd = true,
                widthPx = revealPx,
                density = density,
                onClick = { settleTo(0f); onArchive() }
            )
        }

        // Foreground: the card itself, slid by the drag offset.
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .draggable(
                    state = draggable,
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        when {
                            offsetX.value <= -catchPx -> settleTo(-revealPx)  // hold Archive open
                            offsetX.value >=  catchPx -> settleTo(revealPx)   // hold Delete open
                            else                      -> settleTo(0f)         // spring back closed
                        }
                    }
                )
        ) {
            content()
        }
    }
}

/** One revealed swipe action: a tappable icon+label pinned to one edge of the row. */
@Composable
private fun SwipeAction(
    visible: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    container: Color,
    onContent: Color,
    alignEnd: Boolean,
    widthPx: Float,
    density: androidx.compose.ui.unit.Density,
    onClick: () -> Unit,
) {
    val widthDp = with(density) { widthPx.toDp() }
    val bg by animateColorAsState(
        targetValue = if (visible) container else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "swipeActionBg"
    )
    Box(
        modifier = Modifier
            .width(widthDp)
            .fillMaxHeight()
            .clip(MaterialTheme.shapes.large)
            .background(bg)
            .then(if (visible) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        AnimatedVisibility(visible = visible, enter = fadeIn() + scaleIn(), exit = fadeOut()) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(icon, contentDescription = label, tint = onContent)
                Text(
                    label,
                    color = onContent,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
