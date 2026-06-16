package com.swarnkary.donezy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Calendar

/**
 * Result of the create/edit sheet's reminder section.
 *  - reminderAt = null + recurrence = None  → no reminder
 *  - reminderAt non-null + recurrence = None → one-shot at that time
 *  - both set                               → first fire + repeat
 */
data class ReminderConfig(val reminderAt: Long?, val recurrence: Recurrence)

data class TrackerSaveResult(
    val name: String,
    val category: String,
    val notes: String,
    val reminder: ReminderConfig,
    val weeklyGoal: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTrackerSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onSave: (TrackerSaveResult) -> Unit,
    initial: Hobby? = null,           // non-null = edit mode
    prefill: HobbyTemplate? = null    // populates fields when no `initial`
) {
    val isEdit = initial != null

    var name     by remember { mutableStateOf(initial?.name ?: prefill?.trackerName ?: "") }
    var category by remember { mutableStateOf(categoryFor(initial?.category ?: prefill?.category ?: "General")) }
    var notes    by remember { mutableStateOf(initial?.notes ?: prefill?.notes ?: "") }
    var weeklyGoal by remember { mutableIntStateOf(initial?.weeklyGoal ?: 0) }

    // Reminder state — preloaded from existing tracker or template default
    var reminderAt by remember {
        mutableStateOf(
            initial?.nextReminderAt
                ?: prefill?.reminderHours?.takeIf { it > 0L }
                    ?.let { System.currentTimeMillis() + it * HOUR_MS }
        )
    }
    var recurrence by remember {
        mutableStateOf(
            initial?.recurrence
                ?: prefill?.reminderHours?.takeIf { it > 0L }?.let { Recurrence.Hourly(it) }
                ?: Recurrence.None
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (isEdit) "Edit tracker" else "New tracker",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Tracker name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )

            CategoryDropdown(selected = category, onSelected = { category = it })

            AnimatedVisibility(visible = category.description.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
                Text(
                    category.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Plan, baseline, or care notes") },
                placeholder = { Text(category.starterNotes, style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                shape = MaterialTheme.shapes.medium
            )

            ReminderSection(
                reminderAt = reminderAt,
                recurrence = recurrence,
                onChange = { ts, rule -> reminderAt = ts; recurrence = rule }
            )

            WeeklyGoalSection(
                value = weeklyGoal,
                onChange = { weeklyGoal = it }
            )

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = {
                    onSave(
                        TrackerSaveResult(
                            name = name,
                            category = category.label,
                            notes = notes,
                            reminder = ReminderConfig(reminderAt, recurrence),
                            weeklyGoal = weeklyGoal
                        )
                    )
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    if (isEdit) "Save changes" else "Add tracker",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ─── Weekly goal ──────────────────────────────────────────────────────────────

@Composable
private fun WeeklyGoalSection(value: Int, onChange: (Int) -> Unit) {
    Text("Weekly goal", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Text(
        "Earn the 🎯 badge each week you hit this many logs.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val options = listOf(0, 1, 2, 3, 5, 7, 10)
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { n ->
            FilterChip(
                selected = value == n,
                onClick = { onChange(n) },
                label = { Text(if (n == 0) "Off" else "$n / wk") }
            )
        }
    }
}

// ─── Reminder Section ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderSection(
    reminderAt: Long?,
    recurrence: Recurrence,
    onChange: (Long?, Recurrence) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()

    Text("Reminder", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

    // Quick relative chips — "in 1h, 3h, 1d, 1w, …"
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(reminderOptions) { option ->
            val selectedHours = if (reminderAt != null && recurrence is Recurrence.Hourly)
                recurrence.hours else null
            val matchesNone = option.hours == 0L && reminderAt == null
            val matches = matchesNone || option.hours == selectedHours
            FilterChip(
                selected = matches,
                onClick = {
                    if (option.hours == 0L) {
                        onChange(null, Recurrence.None)
                    } else {
                        onChange(System.currentTimeMillis() + option.hours * HOUR_MS, Recurrence.None)
                    }
                },
                label = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(option.label, fontWeight = if (matches) FontWeight.SemiBold else FontWeight.Normal)
                        Text(option.description, style = MaterialTheme.typography.labelSmall)
                    }
                }
            )
        }
    }

    // Absolute datetime row
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { showDatePicker = true },
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.large
        ) {
            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(reminderAt?.let { formatDateShort(it) } ?: "Pick date")
        }
        OutlinedButton(
            onClick = { if (reminderAt != null) showTimePicker = true else showDatePicker = true },
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.large
        ) {
            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(reminderAt?.let {
                val t = ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), zone).toLocalTime()
                "%02d:%02d".format(t.hour, t.minute)
            } ?: "Pick time")
        }
    }

    // Recurrence choice
    AnimatedVisibility(visible = reminderAt != null, enter = fadeIn(), exit = fadeOut()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Repeat", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            RecurrencePicker(
                current = recurrence,
                anchor = reminderAt ?: System.currentTimeMillis(),
                onChange = { onChange(reminderAt, it) }
            )
        }
    }

    if (showDatePicker) {
        val initialMillis = reminderAt ?: (System.currentTimeMillis() + DAY_MS)
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val ms = state.selectedDateMillis ?: initialMillis
                    val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.of("UTC"))
                        .toLocalDate()
                    val time = reminderAt
                        ?.let { ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), zone).toLocalTime() }
                        ?: LocalTime.of(9, 0)
                    val combined = ZonedDateTime.of(zdt, time, zone).toInstant().toEpochMilli()
                    onChange(combined, recurrence)
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showTimePicker && reminderAt != null) {
        val current = ZonedDateTime.ofInstant(Instant.ofEpochMilli(reminderAt), zone).toLocalTime()
        val state = rememberTimePickerState(initialHour = current.hour, initialMinute = current.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val date = ZonedDateTime.ofInstant(Instant.ofEpochMilli(reminderAt), zone).toLocalDate()
                    val combined = ZonedDateTime.of(date, LocalTime.of(state.hour, state.minute), zone)
                        .toInstant().toEpochMilli()
                    onChange(combined, recurrence)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
            title = { Text("Pick time") },
            text = { TimePicker(state = state) }
        )
    }
}

// ─── Recurrence picker ────────────────────────────────────────────────────────

@Composable
private fun RecurrencePicker(
    current: Recurrence,
    anchor: Long,
    onChange: (Recurrence) -> Unit
) {
    val zone = ZoneId.systemDefault()
    val anchorDate = ZonedDateTime.ofInstant(Instant.ofEpochMilli(anchor), zone).toLocalDate()

    val choices = listOf<Pair<String, Recurrence>>(
        "None"    to Recurrence.None,
        "Hourly"  to (current as? Recurrence.Hourly ?: Recurrence.Hourly(24L)),
        "Daily"   to Recurrence.Daily,
        "Weekly"  to (current as? Recurrence.Weekly ?: Recurrence.Weekly(1 shl ((anchorDate.dayOfWeek.value - 1)))),
        "Monthly" to (current as? Recurrence.Monthly ?: Recurrence.Monthly(anchorDate.dayOfMonth))
    )

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(choices) { (label, rule) ->
            val selected = current.javaClass == rule.javaClass &&
                (current !is Recurrence.None || rule is Recurrence.None)
            FilterChip(
                selected = selected,
                onClick = { onChange(rule) },
                label = { Text(label) }
            )
        }
    }

    when (current) {
        is Recurrence.Hourly -> HourlyEditor(current.hours) { onChange(Recurrence.Hourly(it)) }
        is Recurrence.Weekly -> WeeklyEditor(current.dayMask) { onChange(Recurrence.Weekly(it)) }
        is Recurrence.Monthly -> Text(
            "Day ${current.dayOfMonth} of each month",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        else -> { /* no extra controls */ }
    }
}

@Composable
private fun HourlyEditor(hours: Long, onChange: (Long) -> Unit) {
    val presets = listOf(1L, 3L, 6L, 12L, 24L, 48L, 72L, 168L, 336L, 720L)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(presets) { p ->
            FilterChip(
                selected = hours == p,
                onClick = { onChange(p) },
                label = { Text(reminderOptions.firstOrNull { it.hours == p }?.label ?: "${p}h") }
            )
        }
    }
}

@Composable
private fun WeeklyEditor(dayMask: Int, onChange: (Int) -> Unit) {
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { i, label ->
            val selected = dayMask and (1 shl i) != 0
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable {
                        val newMask = dayMask xor (1 shl i)
                        onChange(newMask.coerceAtLeast(0))
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ─── Category Dropdown ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDropdown(selected: CategoryOption, onSelected: (CategoryOption) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = "${selected.emoji}  ${selected.label}",
            onValueChange = {},
            readOnly = true,
            label = { Text("Category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            hobbyCategories.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(option.emoji)
                            Column {
                                Text(option.label, fontWeight = FontWeight.SemiBold)
                                Text(
                                    option.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    onClick = { onSelected(option); expanded = false }
                )
            }
        }
    }
}

// ─── Template Deck ────────────────────────────────────────────────────────────

@Composable
fun TemplateDeck(onApply: (HobbyTemplate) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Starter templates", subtitle = "Tap to prefill")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 4.dp)
        ) {
            items(hobbyTemplates) { template ->
                val cat = categoryFor(template.category)
                ElevatedCard(
                    onClick = { onApply(template) },
                    modifier = Modifier.width(220.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CategoryBadge(cat)
                        Text(template.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            template.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3
                        )
                        Text(
                            "Default: ${reminderLabel(template.reminderHours)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
