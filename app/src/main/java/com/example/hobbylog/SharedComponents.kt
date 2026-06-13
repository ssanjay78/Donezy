package com.example.hobbylog

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── Category Badge ───────────────────────────────────────────────────────────

@Composable
fun CategoryBadge(category: CategoryOption, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = category.accent.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(category.emoji, fontSize = 12.sp)
            Text(
                category.label,
                style = MaterialTheme.typography.labelMedium,
                color = category.accent,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ─── Category Avatar ──────────────────────────────────────────────────────────

@Composable
fun CategoryAvatar(category: CategoryOption, size: Int = 52) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        category.accent.copy(alpha = 0.28f),
                        category.accent.copy(alpha = 0.10f)
                    )
                )
            )
            .border(1.dp, category.accent.copy(alpha = 0.25f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(category.emoji, fontSize = (size * 0.42).sp)
    }
}

// ─── Reminder Status Pill ─────────────────────────────────────────────────────

private const val HOUR_MS = 60L * 60L * 1000L
private const val DAY_MS  = 24L * HOUR_MS

data class ReminderStatusInfo(val label: String, val detail: String, val color: Color)

fun reminderStatusInfo(nextReminderAt: Long?): ReminderStatusInfo {
    if (nextReminderAt == null)
        return ReminderStatusInfo("No reminder", "Not scheduled", Color(0xFF888888))
    val now  = System.currentTimeMillis()
    val diff = nextReminderAt - now
    return when {
        diff <= 0L     -> ReminderStatusInfo("Overdue",    "Overdue by ${durationText(-diff)}", Color(0xFFB3261E))
        diff <= HOUR_MS -> ReminderStatusInfo("Soon",      "In ${durationText(diff)}",           Color(0xFF9A5A00))
        diff <= DAY_MS  -> ReminderStatusInfo("Today",     "In ${durationText(diff)}",           Color(0xFF9A5A00))
        else            -> ReminderStatusInfo("Scheduled", formatDateShort(nextReminderAt),       Color(0xFF1A6B48))
    }
}

private fun durationText(ms: Long): String {
    val h = (ms / HOUR_MS).coerceAtLeast(1L)
    return when {
        h < 24L        -> "${h}h"
        h < 24L * 14L  -> "${h / 24}d"
        else           -> "${h / (24L * 7L)}w"
    }
}

@Composable
fun StatusPill(info: ReminderStatusInfo, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(info.color.copy(alpha = 0.13f), label = "pillBg")
    Surface(modifier = modifier, shape = RoundedCornerShape(999.dp), color = bg) {
        Text(
            info.label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = info.color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ─── Metric Tile ──────────────────────────────────────────────────────────────

@Composable
fun MetricTile(label: String, value: String, modifier: Modifier = Modifier, accent: Color? = null) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        tonalElevation = 0.dp
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = accent ?: MaterialTheme.colorScheme.onSurface
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── Section Header ───────────────────────────────────────────────────────────

@Composable
fun SectionHeader(
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── Streak Badge ─────────────────────────────────────────────────────────────

@Composable
fun StreakBadge(streak: Int, modifier: Modifier = Modifier) {
    if (streak < 2) return
    val (bg, fg) = when {
        streak >= 30 -> Color(0xFFFFD700) to Color(0xFF7A5000)
        streak >= 14 -> Color(0xFFFF8C00) to Color(0xFF5A2800)
        streak >= 7  -> Color(0xFFFF6347) to Color(0xFF4A0E00)
        else         -> Color(0xFF2D7A46).copy(alpha = 0.18f) to Color(0xFF2D7A46)
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = bg.copy(alpha = if (streak >= 7) 0.18f else 0.18f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🔥", fontSize = 12.sp)
            Text(
                "$streak day streak",
                style = MaterialTheme.typography.labelSmall,
                color = if (streak >= 7) fg else Color(0xFF2D7A46),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ─── Star Rating ─────────────────────────────────────────────────────────────

@Composable
fun StarRatingRow(
    rating: Int?,
    onRate: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (1..5).forEach { star ->
            val filled = rating != null && star <= rating
            val scale by animateFloatAsState(
                targetValue = if (filled) 1.15f else 1f,
                animationSpec = spring(dampingRatio = 0.4f),
                label = "starScale$star"
            )
            Icon(
                imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = "$star stars",
                tint = if (filled) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier
                    .size(22.dp)
                    .scale(scale)
                    .clickable {
                        onRate(if (rating == star) null else star)  // tap same star to clear
                    }
            )
        }
    }
}

// ─── Reminder Selector Row ────────────────────────────────────────────────────

@Composable
fun ReminderSelector(
    options: List<ReminderOption>,
    selectedHours: Long,
    onSelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { option ->
            val selected = option.hours == selectedHours
            FilterChip(
                selected = selected,
                onClick = { onSelected(option.hours) },
                label = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(option.label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                        Text(option.description, style = MaterialTheme.typography.labelSmall)
                    }
                }
            )
        }
    }
}

// ─── Category Filter Chips ────────────────────────────────────────────────────

@Composable
fun CategoryFilterRow(
    labels: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(labels) { label ->
            FilterChip(
                selected = selected == label,
                onClick = { onSelected(label) },
                label = { Text(label) }
            )
        }
    }
}

// ─── Empty States ─────────────────────────────────────────────────────────────

@Composable
fun EmptyTrackerState(hasTrackers: Boolean, onClear: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("📭", fontSize = 48.sp)
        Text(
            if (hasTrackers) "No trackers match this view" else "No trackers yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            if (hasTrackers) "Clear your search or switch categories."
            else "Tap ＋ to create your first tracker.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (hasTrackers) {
            TextButton(onClick = onClear) { Text("Clear filters") }
        }
    }
}

@Composable
fun EmptyLogState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("📝", fontSize = 36.sp)
        Text("No logs yet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Use a quick log chip or write a detailed entry.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─── Stars display (read-only) ────────────────────────────────────────────────

@Composable
fun StarDisplay(rating: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (1..5).forEach { star ->
            Icon(
                imageVector = if (star <= rating) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = null,
                tint = if (star <= rating) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ─── Date formatting helpers ──────────────────────────────────────────────────

fun formatDate(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM d, yyyy h:mm a", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

fun formatDateShort(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}
