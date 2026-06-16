package com.swarnkary.donezy

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// ─── Navigation state ─────────────────────────────────────────────────────────

sealed class NavState {
    object Home : NavState()
    object Archive : NavState()
    object Settings : NavState()
    object About : NavState()
    data class Detail(val hobbyId: Long) : NavState()
}

enum class SortBy(val label: String) {
    DueSoon("Due soon"),
    RecentActivity("Recent"),
    Alphabetical("A–Z")
}

data class SnackbarEvent(
    val message: String,
    val action: String? = null,
    val onAction: (() -> Unit)? = null
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

class HobbyViewModel(
    private val repository: HobbyRepository,
    private val themePreference: ThemePreference,
    private val settingsPreferences: SettingsPreferences,
    private val appContext: Context
) : ViewModel() {

    val soundEnabled: StateFlow<Boolean>        = settingsPreferences.soundEnabled
    val vibrateEnabled: StateFlow<Boolean>      = settingsPreferences.vibrateEnabled
    val streakRescueEnabled: StateFlow<Boolean> = settingsPreferences.streakRescueEnabled

    fun setSoundEnabled(enabled: Boolean)        = settingsPreferences.setSound(enabled)
    fun setVibrateEnabled(enabled: Boolean)      = settingsPreferences.setVibrate(enabled)
    fun setStreakRescueEnabled(enabled: Boolean) {
        settingsPreferences.setStreakRescue(enabled)
        if (enabled) StreakRescueScheduler.scheduleNext(appContext)
        else StreakRescueScheduler.cancel(appContext)
    }

    val hobbies: StateFlow<List<Hobby>> = repository.hobbies
    val archivedHobbies: StateFlow<List<Hobby>> = repository.archivedHobbies
    val logDaysByHobby: StateFlow<Map<Long, List<Long>>> = repository.logDaysByHobby
    val themeMode: StateFlow<ThemeMode> = themePreference.mode

    private val _navState = MutableStateFlow<NavState>(NavState.Home)
    val navState: StateFlow<NavState> = _navState

    private val _detail = MutableStateFlow<HobbyDetail?>(null)
    val detail: StateFlow<HobbyDetail?> = _detail

    private val _snackbar = Channel<SnackbarEvent>(Channel.BUFFERED)
    val snackbarEvents = _snackbar.receiveAsFlow()

    private val _logSearchQuery = MutableStateFlow("")
    val logSearchQuery: StateFlow<String> = _logSearchQuery

    private val _logSearchResults = MutableStateFlow<List<LogSearchHit>>(emptyList())
    val logSearchResults: StateFlow<List<LogSearchHit>> = _logSearchResults

    init {
        viewModelScope.launch {
            repository.refresh()
            StreakRescueScheduler.scheduleNext(appContext)
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    fun openDetail(id: Long) {
        viewModelScope.launch {
            val d = repository.detail(id)
            if (d == null) {
                // Tracker may have been deleted/archived between scheduling the notification
                // and the user tapping it. Stay on Home rather than rendering an empty detail.
                _snackbar.send(SnackbarEvent("That tracker is no longer available"))
                _navState.value = NavState.Home
                return@launch
            }
            _detail.value = d
            _navState.value = NavState.Detail(id)
        }
    }

    fun goHome() {
        _navState.value = NavState.Home
        _detail.value = null
        viewModelScope.launch { repository.refresh() }
    }

    fun openArchive()  { _navState.value = NavState.Archive  }
    fun openSettings() { _navState.value = NavState.Settings }
    fun openAbout()    { _navState.value = NavState.About    }

    fun refreshDetail(id: Long) {
        viewModelScope.launch { _detail.value = repository.detail(id) }
    }

    // ── Theme ─────────────────────────────────────────────────────────────────

    fun setThemeMode(mode: ThemeMode) = themePreference.setMode(mode)

    // ── Tracker CRUD ──────────────────────────────────────────────────────────

    fun addHobby(
        name: String,
        category: String,
        notes: String,
        reminderAt: Long?,
        recurrence: Recurrence,
        weeklyGoal: Int = 0
    ) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val id = repository.addHobby(
                trimmed, category.ifBlank { "General" }, notes, reminderAt, recurrence, weeklyGoal
            )
            if (reminderAt != null) scheduleReminder(appContext, id, trimmed, reminderAt)
            _snackbar.send(SnackbarEvent("Tracker \"$trimmed\" created ✓"))
            NextDueWidgetProvider.refreshAll(appContext)
        }
    }

    fun updateHobby(id: Long, name: String, category: String, notes: String, weeklyGoal: Int = 0) {
        viewModelScope.launch {
            repository.updateHobby(id, name, category, notes, weeklyGoal)
            _snackbar.send(SnackbarEvent("Tracker updated ✓"))
            refreshDetail(id)
        }
    }

    fun deleteHobby(id: Long, name: String) {
        viewModelScope.launch {
            // Snapshot before deleting so Undo can restore the hobby and its (cascaded) logs.
            val snapshot = repository.snapshotHobby(id)
            cancelReminder(appContext, id)
            repository.deleteHobby(id)
            NextDueWidgetProvider.refreshAll(appContext)
            _snackbar.send(SnackbarEvent(
                "\"$name\" deleted",
                action = if (snapshot != null) "Undo" else null,
                onAction = snapshot?.let { { restoreDeletedHobby(it) } }
            ))
            goHome()
        }
    }

    private fun restoreDeletedHobby(snapshot: HobbyDetail) {
        viewModelScope.launch {
            repository.restoreHobbySnapshot(snapshot)
            // Re-arm a future reminder if the restored hobby had one pending.
            val ts = snapshot.hobby.nextReminderAt
            if (ts != null && ts > System.currentTimeMillis()) {
                scheduleReminder(appContext, snapshot.hobby.id, snapshot.hobby.name, ts)
            }
            NextDueWidgetProvider.refreshAll(appContext)
            _snackbar.send(SnackbarEvent("\"${snapshot.hobby.name}\" restored"))
        }
    }

    fun togglePin(hobby: Hobby) {
        viewModelScope.launch {
            repository.togglePin(hobby.id, hobby.isPinned)
            _snackbar.send(SnackbarEvent(if (hobby.isPinned) "Unpinned" else "Pinned to top 📌"))
        }
    }

    fun archiveHobby(id: Long, name: String) {
        viewModelScope.launch {
            cancelReminder(appContext, id)
            repository.archiveHobby(id)
            NextDueWidgetProvider.refreshAll(appContext)
            _snackbar.send(SnackbarEvent("\"$name\" archived", action = "Undo", onAction = { restoreHobby(id, name) }))
            if (_navState.value is NavState.Detail) goHome()
        }
    }

    fun restoreHobby(id: Long, name: String) {
        viewModelScope.launch {
            repository.restoreHobby(id)
            // Re-arm reminder if there was one in the future
            val h = repository.hobbyByIdSync(id)
            val ts = h?.nextReminderAt
            if (h != null && ts != null && ts > System.currentTimeMillis()) {
                scheduleReminder(appContext, h.id, h.name, ts)
            }
            NextDueWidgetProvider.refreshAll(appContext)
            _snackbar.send(SnackbarEvent("\"$name\" restored"))
        }
    }

    // ── Reminders ─────────────────────────────────────────────────────────────

    fun setReminderAt(hobby: Hobby, reminderAt: Long, recurrence: Recurrence) {
        viewModelScope.launch {
            repository.updateReminder(hobby.id, reminderAt, recurrence)
            scheduleReminder(appContext, hobby.id, hobby.name, reminderAt)
            _snackbar.send(SnackbarEvent("Reminder set for ${formatDate(reminderAt)} 🔔"))
            refreshDetail(hobby.id)
            NextDueWidgetProvider.refreshAll(appContext)
        }
    }

    fun clearReminder(hobby: Hobby) {
        viewModelScope.launch {
            cancelReminder(appContext, hobby.id)
            repository.updateReminder(hobby.id, null, Recurrence.None)
            _snackbar.send(SnackbarEvent("Reminder cleared"))
            refreshDetail(hobby.id)
            NextDueWidgetProvider.refreshAll(appContext)
        }
    }

    // ── Logs ─────────────────────────────────────────────────────────────────

    fun addLog(hobbyId: Long, entry: String, rating: Int?, photoUri: String? = null) {
        if (entry.isBlank() && photoUri == null) return
        viewModelScope.launch {
            // Snapshot before so we can detect newly-earned achievements after the insert.
            val beforeDetail = repository.detail(hobbyId)
            val beforeSnapshot = beforeDetail?.let {
                AchievementSnapshot.from(it.hobby, it.logs)
            }

            repository.addLog(hobbyId, entry, rating, photoUri)
            refreshDetail(hobbyId)
            NextDueWidgetProvider.refreshAll(appContext)

            val afterDetail = repository.detail(hobbyId)
            val newAchievements = if (beforeSnapshot != null && afterDetail != null) {
                Achievements.newlyEarned(
                    before = beforeSnapshot,
                    after = AchievementSnapshot.from(afterDetail.hobby, afterDetail.logs)
                )
            } else emptyList()

            if (newAchievements.isNotEmpty()) {
                for (a in newAchievements) {
                    _snackbar.send(SnackbarEvent("${a.emoji} ${a.title} unlocked — ${a.tagline}"))
                }
            } else {
                _snackbar.send(SnackbarEvent(Affirmations.pick()))
            }

            // After a successful save, drop the user back to Home so the snackbar /
            // achievement appears against the dashboard. They can re-open the tracker
            // from the home list if they want to keep adding entries.
            if (_navState.value is NavState.Detail) goHome()
        }
    }

    fun deleteLog(hobbyId: Long, logId: Long) {
        viewModelScope.launch {
            val snapshot = repository.fetchLog(logId) ?: return@launch
            repository.deleteLog(logId)
            refreshDetail(hobbyId)
            _snackbar.send(SnackbarEvent(
                "Log entry deleted",
                action = "Undo",
                onAction = {
                    viewModelScope.launch {
                        repository.insertLog(snapshot)
                        refreshDetail(hobbyId)
                    }
                }
            ))
        }
    }

    /**
     * Persist a content-Uri photo into the app's private filesDir/photos/
     * so we can keep the URI valid after the picker permission expires.
     * Returns a `file://` URI usable inside the app and shareable via FileProvider.
     */
    suspend fun importPhoto(srcUri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val dir = File(appContext.filesDir, "photos").apply { mkdirs() }
            val target = File(dir, "log_${System.currentTimeMillis()}.jpg")
            appContext.contentResolver.openInputStream(srcUri)?.use { input ->
                FileOutputStream(target).use { out -> input.copyTo(out) }
            } ?: return@withContext null
            target.toURI().toString()
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Allocate an empty file in the private photos dir for the camera to write into.
     * Returns a [CameraTarget] pairing the FileProvider content-Uri handed to the camera
     * (which can write to it) with the `file://` URI we persist on success — mirroring the
     * scheme [importPhoto] uses so both photo sources end up identical downstream.
     * Returns null if the target file can't be created.
     */
    fun createCameraTarget(): CameraTarget? = try {
        val dir = File(appContext.filesDir, "photos").apply { mkdirs() }
        val target = File(dir, "log_${System.currentTimeMillis()}.jpg")
        target.createNewFile()
        val contentUri = FileProvider.getUriForFile(
            appContext, "${appContext.packageName}.fileprovider", target
        )
        CameraTarget(contentUri = contentUri, fileUri = target.toURI().toString())
    } catch (t: Throwable) {
        null
    }

    /** Delete a camera target file that the user cancelled out of, so it doesn't leak. */
    fun discardCameraTarget(fileUri: String) {
        try {
            File(Uri.parse(fileUri).path ?: return).takeIf { it.exists() }?.delete()
        } catch (_: Throwable) { /* best-effort cleanup */ }
    }

    /** Camera-capture handles: [contentUri] is writable by the camera app; [fileUri] is what we store. */
    data class CameraTarget(val contentUri: Uri, val fileUri: String)

    // ── Log search ────────────────────────────────────────────────────────────

    fun setLogSearchQuery(query: String) {
        _logSearchQuery.value = query
        viewModelScope.launch {
            _logSearchResults.value =
                if (query.isBlank()) emptyList() else repository.searchLogs(query)
        }
    }

    // ── Export ────────────────────────────────────────────────────────────────

    fun exportLogs(context: Context) {
        val d = _detail.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
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
            val safeName = d.hobby.name.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "hobby" }
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, "${safeName}_log.csv")
            file.writeText(csv)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "${d.hobby.name} — Donezy Export")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(intent, "Export logs"))
            }
        }
    }

    // ── Backup / Restore ──────────────────────────────────────────────────────

    fun backupTo(context: Context, dest: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val payload = BackupCodec.encode(repository.allHobbiesSync(), repository.allLogsSync())
                context.contentResolver.openOutputStream(dest)?.use { it.write(payload.toByteArray()) }
                    ?: throw IllegalStateException("Could not open destination")
                _snackbar.send(SnackbarEvent("Backup saved ✓"))
            } catch (t: Throwable) {
                _snackbar.send(SnackbarEvent("Backup failed: ${t.message ?: "unknown"}"))
            }
        }
    }

    fun restoreFrom(context: Context, src: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val text = context.contentResolver.openInputStream(src)?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalStateException("Could not open backup file")
                val (hobbies, logs) = BackupCodec.decode(text)
                repository.replaceAll(hobbies, logs)
                // Reschedule alarms
                for (h in hobbies) {
                    val ts = h.nextReminderAt
                    if (!h.isArchived && ts != null && ts > System.currentTimeMillis()) {
                        scheduleReminder(appContext, h.id, h.name, ts)
                    }
                }
                NextDueWidgetProvider.refreshAll(appContext)
                _snackbar.send(SnackbarEvent("Restored ${hobbies.size} trackers"))
            } catch (t: Throwable) {
                _snackbar.send(SnackbarEvent("Restore failed: ${t.message ?: "unknown"}"))
            }
        }
    }

    // ── Streak / formatting helpers ──────────────────────────────────────────

    companion object {
        /** Streak computed from precomputed local-midnight day timestamps, descending. */
        fun computeStreakFromDays(dayMs: List<Long>): Int {
            if (dayMs.isEmpty()) return 0
            val zone = ZoneId.systemDefault()
            val daySet = dayMs.toHashSet()
            fun localDayMs(date: LocalDate): Long =
                date.atStartOfDay(zone).toInstant().toEpochMilli()

            var streak = 0
            var check = LocalDate.now(zone)
            while (daySet.contains(localDayMs(check))) {
                streak++
                check = check.minusDays(1)
            }
            if (streak == 0) {
                check = LocalDate.now(zone).minusDays(1)
                while (daySet.contains(localDayMs(check))) {
                    streak++
                    check = check.minusDays(1)
                }
            }
            return streak
        }

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
            return (now - last) / DAY_MS
        }

        fun factory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HobbyViewModel(
                    ServiceLocator.repository(context),
                    ServiceLocator.themePreference(context),
                    ServiceLocator.settingsPreferences(context),
                    context.applicationContext
                ) as T
        }
    }
}
