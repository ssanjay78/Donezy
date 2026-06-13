package com.example.hobbylog

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val HOUR_MS = 60L * 60L * 1000L

// ─── Navigation state ─────────────────────────────────────────────────────────

sealed class NavState {
    object Home : NavState()
    data class Detail(val hobbyId: Long) : NavState()
}

// ─── Sort options ─────────────────────────────────────────────────────────────

enum class SortBy(val label: String) {
    DueSoon("Due soon"),
    RecentActivity("Recent"),
    Alphabetical("A–Z")
}

// ─── Snackbar events ──────────────────────────────────────────────────────────

data class SnackbarEvent(val message: String, val action: String? = null, val onAction: (() -> Unit)? = null)

// ─── ViewModel ────────────────────────────────────────────────────────────────

class HobbyViewModel(
    private val repository: HobbyRepository,
    private val appContext: Context
) : ViewModel() {

    val hobbies: StateFlow<List<Hobby>> = repository.hobbies

    private val _navState = MutableStateFlow<NavState>(NavState.Home)
    val navState: StateFlow<NavState> = _navState

    private val _detail = MutableStateFlow<HobbyDetail?>(null)
    val detail: StateFlow<HobbyDetail?> = _detail

    private val _snackbar = Channel<SnackbarEvent>(Channel.BUFFERED)
    val snackbarEvents = _snackbar.receiveAsFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        viewModelScope.launch { repository.refresh() }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    fun openDetail(id: Long) {
        viewModelScope.launch {
            _detail.value = repository.detail(id)
            _navState.value = NavState.Detail(id)
        }
    }

    fun goHome() {
        _navState.value = NavState.Home
        _detail.value = null
        viewModelScope.launch { repository.refresh() }
    }

    fun refreshDetail(id: Long) {
        viewModelScope.launch { _detail.value = repository.detail(id) }
    }

    // ── Tracker CRUD ──────────────────────────────────────────────────────────

    fun addHobby(
        name: String,
        category: String,
        notes: String,
        reminderHours: Long,
        recurring: Boolean
    ) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val reminderAt = if (reminderHours > 0L) System.currentTimeMillis() + reminderHours * HOUR_MS else null
        val intervalHours = if (recurring && reminderHours > 0L) reminderHours else 0L
        viewModelScope.launch {
            val id = repository.addHobby(trimmed, category.ifBlank { "General" }, notes, reminderAt, intervalHours)
            if (reminderAt != null) scheduleReminder(appContext, id, trimmed, reminderAt)
            _snackbar.send(SnackbarEvent("Tracker \"$trimmed\" created ✓"))
            repository.refresh()
        }
    }

    fun updateHobby(id: Long, name: String, category: String, notes: String) {
        viewModelScope.launch {
            repository.updateHobby(id, name, category, notes)
            _snackbar.send(SnackbarEvent("Tracker updated ✓"))
            refreshDetail(id)
        }
    }

    fun deleteHobby(id: Long, name: String) {
        viewModelScope.launch {
            repository.deleteHobby(id)
            _snackbar.send(SnackbarEvent("\"$name\" deleted"))
            goHome()
        }
    }

    fun togglePin(hobby: Hobby) {
        viewModelScope.launch {
            repository.togglePin(hobby.id, hobby.isPinned)
            _snackbar.send(SnackbarEvent(if (hobby.isPinned) "Unpinned" else "Pinned to top 📌"))
            repository.refresh()
        }
    }

    fun archiveHobby(id: Long, name: String) {
        viewModelScope.launch {
            repository.archiveHobby(id)
            _snackbar.send(SnackbarEvent("\"$name\" archived"))
            goHome()
        }
    }

    // ── Reminders ─────────────────────────────────────────────────────────────

    fun setReminder(hobby: Hobby, hoursFromNow: Long, recurring: Boolean) {
        val reminderAt = System.currentTimeMillis() + hoursFromNow * HOUR_MS
        val intervalHours = if (recurring && hoursFromNow > 0L) hoursFromNow else 0L
        viewModelScope.launch {
            repository.updateReminder(hobby.id, reminderAt, intervalHours)
            scheduleReminder(appContext, hobby.id, hobby.name, reminderAt)
            _snackbar.send(SnackbarEvent("Reminder set for ${reminderLabel(hoursFromNow)} from now 🔔"))
            refreshDetail(hobby.id)
        }
    }

    fun clearReminder(hobby: Hobby) {
        viewModelScope.launch {
            repository.updateReminder(hobby.id, null, 0L)
            _snackbar.send(SnackbarEvent("Reminder cleared"))
            refreshDetail(hobby.id)
        }
    }

    // ── Logs ─────────────────────────────────────────────────────────────────

    fun addLog(hobbyId: Long, entry: String, rating: Int?) {
        if (entry.isBlank()) return
        viewModelScope.launch {
            repository.addLog(hobbyId, entry, rating)
            _snackbar.send(SnackbarEvent("Log saved ✓"))
            refreshDetail(hobbyId)
        }
    }

    fun deleteLog(hobbyId: Long, logId: Long) {
        viewModelScope.launch {
            repository.deleteLog(logId)
            _snackbar.send(SnackbarEvent("Log entry deleted"))
            refreshDetail(hobbyId)
        }
    }

    // ── Export ────────────────────────────────────────────────────────────────

    fun exportLogs(context: Context) {
        val d = _detail.value ?: return
        val csv = buildString {
            appendLine("Tracker: ${d.hobby.name}")
            appendLine("Category: ${d.hobby.category}")
            appendLine("Notes: ${d.hobby.notes}")
            appendLine()
            appendLine("Date,Entry,Rating")
            d.logs.forEach { log ->
                val safeEntry = log.entry.replace("\"", "\"\"")
                appendLine("${formatDate(log.createdAt)},\"$safeEntry\",${log.rating ?: ""}")
            }
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, csv)
            putExtra(Intent.EXTRA_SUBJECT, "${d.hobby.name} — HobbyLog Export")
        }
        context.startActivity(Intent.createChooser(intent, "Export logs"))
    }

    // ── Streak helpers ────────────────────────────────────────────────────────

    companion object {
        fun computeStreak(logs: List<HobbyLog>): Int {
            if (logs.isEmpty()) return 0
            val zone = ZoneId.systemDefault()
            val logDates = logs.map {
                Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate()
            }.toSortedSet(reverseOrder())

            var streak = 0
            var check: LocalDate = LocalDate.now(zone)
            while (logDates.contains(check)) {
                streak++
                check = check.minusDays(1)
            }
            // Also accept yesterday as start of streak (didn't log today yet)
            if (streak == 0) {
                check = LocalDate.now(zone).minusDays(1)
                while (logDates.contains(check)) {
                    streak++
                    check = check.minusDays(1)
                }
            }
            return streak
        }

        fun daysSinceLastLog(logs: List<HobbyLog>): Long? {
            val last = logs.maxByOrNull { it.createdAt }?.createdAt ?: return null
            val now = System.currentTimeMillis()
            return (now - last) / (24L * 60L * 60L * 1000L)
        }

        fun formatDate(timestamp: Long): String =
            SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(timestamp))

        fun formatDateShort(timestamp: Long): String =
            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))

        fun factory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HobbyViewModel(
                    HobbyRepository(AppDatabase(context.applicationContext)),
                    context.applicationContext
                ) as T
        }
    }
}
