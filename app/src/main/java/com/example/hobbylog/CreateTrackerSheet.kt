package com.example.hobbylog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// ─── Create / Edit Tracker Bottom Sheet ──────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTrackerSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onSave: (name: String, category: String, notes: String, reminderHours: Long, recurring: Boolean) -> Unit,
    initial: Hobby? = null   // non-null = edit mode
) {
    val isEdit = initial != null

    var name          by remember { mutableStateOf(initial?.name ?: "") }
    var category      by remember { mutableStateOf(categoryFor(initial?.category ?: "General")) }
    var notes         by remember { mutableStateOf(initial?.notes ?: "") }
    var reminderHours by remember { mutableLongStateOf(24L) }
    var recurring     by remember { mutableStateOf(initial?.reminderIntervalHours?.let { it > 0L } ?: false) }

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

            // Tracker name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Tracker name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )

            // Category dropdown
            CategoryDropdown(selected = category, onSelected = { category = it })

            // Description hint
            AnimatedVisibility(visible = category.description.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
                Text(
                    category.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Plan, baseline, or care notes") },
                placeholder = { Text(category.starterNotes, style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                shape = MaterialTheme.shapes.medium
            )

            // Reminder selector
            Text("Remind me in", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            ReminderSelector(
                options = reminderOptions,
                selectedHours = reminderHours,
                onSelected = { reminderHours = it }
            )

            // Recurring toggle
            AnimatedVisibility(visible = reminderHours > 0L, enter = fadeIn(), exit = fadeOut()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Auto-recurring reminder", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            "Re-schedules automatically after each alert",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = recurring, onCheckedChange = { recurring = it })
                }
            }

            Spacer(Modifier.height(4.dp))

            // Save button
            Button(
                onClick = { onSave(name, category.label, notes, reminderHours, recurring) },
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
        SectionHeader("Starter templates", "Tap to prefill")
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
