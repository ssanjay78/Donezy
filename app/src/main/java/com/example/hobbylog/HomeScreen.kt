package com.example.hobbylog

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HobbyViewModel) {
    val hobbies by viewModel.hobbies.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showCreateSheet by remember { mutableStateOf(false) }
    val createSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedFilter by remember { mutableStateOf("All") }
    var searchQuery    by remember { mutableStateOf("") }
    var sortBy         by remember { mutableStateOf(SortBy.DueSoon) }
    var showSortMenu   by remember { mutableStateOf(false) }

    val now = System.currentTimeMillis()
    val dueSoon = remember(hobbies) { hobbies.count { it.nextReminderAt != null && it.nextReminderAt <= now + 24 * 60 * 60 * 1000L } }
    val scheduled = remember(hobbies) { hobbies.count { it.nextReminderAt != null } }

    val filterLabels = remember(hobbies) {
        listOf("All") + hobbies.map { it.category }.distinct().filter { it.isNotBlank() }.sorted()
    }

    val filteredHobbies = remember(hobbies, selectedFilter, searchQuery, sortBy) {
        val q = searchQuery.trim()
        hobbies
            .filter { h ->
                val matchCat = selectedFilter == "All" || h.category == selectedFilter
                val matchQ = q.isBlank() || h.name.contains(q, true) || h.category.contains(q, true) || h.notes.contains(q, true)
                matchCat && matchQ
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

    // Collect snackbar events
    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collectLatest { event ->
            snackbarHostState.showSnackbar(
                message = event.message,
                actionLabel = event.action,
                duration = SnackbarDuration.Short
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("HobbyLog", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Plan · Log · Remember",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Sort menu
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
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
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateSheet = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New tracker") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ── Gradient hero dashboard ─────────────────────────────────────
            item {
                DashboardHero(
                    total = hobbies.size,
                    dueSoon = dueSoon,
                    scheduled = scheduled,
                    categories = hobbies.map { it.category }.distinct().size,
                    pinned = hobbies.count { it.isPinned }
                )
            }

            // ── Starter templates ───────────────────────────────────────────
            if (hobbies.isEmpty()) {
                item {
                    TemplateDeck(onApply = { template ->
                        showCreateSheet = true
                        // Pre-fill is handled inside the sheet
                    })
                }
            }

            // ── Filter row + search ─────────────────────────────────────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("Trackers", "${filteredHobbies.size} shown")
                    CategoryFilterRow(
                        labels = filterLabels,
                        selected = selectedFilter,
                        onSelected = { selectedFilter = it }
                    )
                    AnimatedVisibility(visible = hobbies.isNotEmpty()) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Search") },
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

            // ── Empty state ─────────────────────────────────────────────────
            if (filteredHobbies.isEmpty()) {
                item {
                    EmptyTrackerState(hasTrackers = hobbies.isNotEmpty(), onClear = {
                        selectedFilter = "All"; searchQuery = ""
                    })
                }
            }

            // ── Tracker cards with swipe-to-delete ─────────────────────────
            items(filteredHobbies, key = { it.id }) { hobby ->
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value != SwipeToDismissBoxValue.Settled) {
                            scope.launch { viewModel.archiveHobby(hobby.id, hobby.name) }
                            true
                        } else false
                    }
                )

                SwipeToDismissBox(
                    state = dismissState,
                    modifier = Modifier.animateItem(),
                    backgroundContent = { SwipeDismissBackground(dismissState.targetValue) },
                    content = {
                        TrackerCard(
                            hobby = hobby,
                            onClick = { viewModel.openDetail(hobby.id) },
                            onPin = { viewModel.togglePin(hobby) }
                        )
                    },
                    enableDismissFromStartToEnd = false
                )
            }
        }
    }

    // ── Create sheet ──────────────────────────────────────────────────────────
    if (showCreateSheet) {
        CreateTrackerSheet(
            sheetState = createSheetState,
            onDismiss = { showCreateSheet = false },
            onSave = { name, cat, notes, hours, recurring ->
                viewModel.addHobby(name, cat, notes, hours, recurring)
                showCreateSheet = false
            }
        )
    }
}

// ─── Gradient dashboard hero ──────────────────────────────────────────────────

@Composable
fun DashboardHero(total: Int, dueSoon: Int, scheduled: Int, categories: Int, pinned: Int) {
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
                "Today at a glance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.95f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HeroMetric("Trackers",   "$total",      Color.White, Modifier.weight(1f))
                HeroMetric("Due soon",   "$dueSoon",    if (dueSoon > 0) Color(0xFFFFCC80) else Color.White, Modifier.weight(1f))
                HeroMetric("Scheduled",  "$scheduled",  Color.White, Modifier.weight(1f))
                HeroMetric("Categories", "$categories", Color.White, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeroMetric(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.15f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = valueColor)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

// ─── Tracker Card ─────────────────────────────────────────────────────────────

@Composable
fun TrackerCard(hobby: Hobby, onClick: () -> Unit, onPin: () -> Unit) {
    val category = categoryFor(hobby.category)
    val status   = reminderStatusInfo(hobby.nextReminderAt)

    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        // Coloured accent strip on the left
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
                // Top row: badge + status + pin
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
                        IconButton(onClick = onPin, modifier = Modifier.size(28.dp)) {
                            Icon(
                                if (hobby.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = "Pin",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Name + notes
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

                // Footer: reminder + recurring badge
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
                    if (hobby.reminderIntervalHours > 0L) {
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

// ─── Swipe dismiss background ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeDismissBackground(targetValue: SwipeToDismissBoxValue) {
    val color by animateColorAsState(
        targetValue = when (targetValue) {
            SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 200),
        label = "dismissBg"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.large)
            .background(color),
        contentAlignment = Alignment.CenterEnd
    ) {
        AnimatedVisibility(
            visible = targetValue == SwipeToDismissBoxValue.EndToStart,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut()
        ) {
            Row(
                modifier = Modifier.padding(end = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Archive, contentDescription = "Archive",
                    tint = MaterialTheme.colorScheme.onErrorContainer)
                Text("Archive", color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
