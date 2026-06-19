package com.swarnkary.donezy

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/**
 * Public API surface, in order:
 *  - reactive lists: hobbies, archivedHobbies, logSearchResults
 *  - tracker CRUD (add/update/delete/togglePin/archive/restore/updateReminder)
 *  - log CRUD (addLog/deleteLog/restoreLog)
 *  - synchronous helpers used from BroadcastReceiver/Widget
 */
class HobbyRepository(private val db: AppDatabase) {

    private val _hobbies         = MutableStateFlow<List<Hobby>>(emptyList())
    val hobbies: StateFlow<List<Hobby>> = _hobbies

    private val _archivedHobbies = MutableStateFlow<List<Hobby>>(emptyList())
    val archivedHobbies: StateFlow<List<Hobby>> = _archivedHobbies

    /** hobbyId → list of distinct epoch-millis at midnight (local zone) where a log exists. */
    private val _logDaysByHobby = MutableStateFlow<Map<Long, List<Long>>>(emptyMap())
    val logDaysByHobby: StateFlow<Map<Long, List<Long>>> = _logDaysByHobby

    suspend fun refresh() = withContext(Dispatchers.IO) { refreshSync() }

    private fun refreshSync() {
        _hobbies.value         = queryHobbies(archived = false)
        _archivedHobbies.value = queryHobbies(archived = true)
        _logDaysByHobby.value  = queryLogDays()
    }

    /**
     * Returns one row per (hobby, local day) so the home screen can compute streak counts
     * in O(D) per tracker without a query-per-hobby. SQLite's strftime handles the
     * UTC→local conversion via the device's offset.
     */
    /**
     * Returns one row per (hobby, local day). The local-day conversion is done in
     * Java via ZoneId.systemDefault() (DST-aware) rather than SQLite's 'localtime'
     * modifier (static UTC offset) so streaks don't break on DST boundary days.
     */
    private fun queryLogDays(): Map<Long, List<Long>> {
        val zone = ZoneId.systemDefault()
        val map = HashMap<Long, MutableSet<Long>>()
        db.readableDatabase.rawQuery(
            "SELECT hobby_id, created_at FROM logs ORDER BY hobby_id ASC, created_at DESC",
            null
        ).use { c ->
            while (c.moveToNext()) {
                val hid = c.getLong(0)
                val createdAt = c.getLong(1)
                val dayMs = Instant.ofEpochMilli(createdAt)
                    .atZone(zone)
                    .toLocalDate()
                    .atStartOfDay(zone)
                    .toInstant()
                    .toEpochMilli()
                map.getOrPut(hid) { mutableLinkedSetOf() }.add(dayMs)
            }
        }
        // Convert sets to sorted-descending lists (same contract as before)
        return map.mapValues { (_, days) -> days.sortedDescending() }
    }

    private fun <T> mutableLinkedSetOf(): MutableSet<T> = LinkedHashSet()

    // ── Hobby queries ─────────────────────────────────────────────────────────

    private fun queryHobbies(archived: Boolean): List<Hobby> =
        db.readableDatabase.rawQuery(
            """
            SELECT id, name, category, notes, next_reminder_at,
                   created_at, is_pinned, is_archived, reminder_interval_hours,
                   recurrence_type, recurrence_data, weekly_goal, sort_order
            FROM hobbies
            WHERE is_archived = ?
            ORDER BY is_pinned DESC, next_reminder_at IS NULL, next_reminder_at ASC
            """.trimIndent(),
            arrayOf(if (archived) "1" else "0")
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(c.toHobby())
            }
        }

    private fun android.database.Cursor.toHobby(): Hobby = Hobby(
        id                    = getLong(0),
        name                  = getString(1),
        category              = getString(2),
        notes                 = getString(3),
        nextReminderAt        = if (isNull(4)) null else getLong(4),
        createdAt             = getLong(5),
        isPinned              = getInt(6) != 0,
        isArchived            = getInt(7) != 0,
        reminderIntervalHours = getLong(8),
        recurrence            = Recurrence.decode(getString(9), getString(10)),
        weeklyGoal            = getInt(11),
        sortOrder             = getInt(12)
    )

    suspend fun addHobby(
        name: String,
        category: String,
        notes: String,
        nextReminderAt: Long?,
        recurrence: Recurrence,
        weeklyGoal: Int = 0
    ): Long = withContext(Dispatchers.IO) {
        val (type, data) = recurrence.encode()
        val intervalHours = (recurrence as? Recurrence.Hourly)?.hours ?: 0L
        val values = ContentValues().apply {
            put("name", name.trim())
            put("category", category.trim())
            put("notes", notes.trim())
            if (nextReminderAt == null) putNull("next_reminder_at") else put("next_reminder_at", nextReminderAt)
            put("created_at", System.currentTimeMillis())
            put("is_pinned", 0)
            put("is_archived", 0)
            put("reminder_interval_hours", intervalHours)
            put("recurrence_type", type)
            put("recurrence_data", data)
            put("weekly_goal", weeklyGoal.coerceAtLeast(0))
        }
        val id = db.writableDatabase.insert("hobbies", null, values)
        refreshSync()
        id
    }

    suspend fun updateHobby(
        id: Long, name: String, category: String, notes: String, weeklyGoal: Int
    ) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("name", name.trim())
            put("category", category.trim())
            put("notes", notes.trim())
            put("weekly_goal", weeklyGoal.coerceAtLeast(0))
        }
        db.writableDatabase.update("hobbies", values, "id = ?", arrayOf(id.toString()))
        refreshSync()
    }

    suspend fun deleteHobby(id: Long) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("hobbies", "id = ?", arrayOf(id.toString()))
        refreshSync()
    }

    /**
     * Snapshot a hobby together with all its logs, for undo-after-delete. Returns null if
     * the hobby doesn't exist. Logs are captured because deleting the hobby cascades to them.
     */
    suspend fun snapshotHobby(id: Long): HobbyDetail? = detail(id)

    /**
     * Re-insert a previously-deleted hobby and its logs, preserving original IDs so any
     * outstanding references (widgets, reminders) line back up. Used to undo a delete.
     */
    suspend fun restoreHobbySnapshot(snapshot: HobbyDetail) = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        w.beginTransaction()
        try {
            val h = snapshot.hobby
            val (type, data) = h.recurrence.encode()
            val hv = ContentValues().apply {
                put("id", h.id)
                put("name", h.name)
                put("category", h.category)
                put("notes", h.notes)
                if (h.nextReminderAt == null) putNull("next_reminder_at") else put("next_reminder_at", h.nextReminderAt)
                put("created_at", h.createdAt)
                put("is_pinned", if (h.isPinned) 1 else 0)
                put("is_archived", if (h.isArchived) 1 else 0)
                put("reminder_interval_hours", h.reminderIntervalHours)
                put("recurrence_type", type)
                put("recurrence_data", data)
                put("weekly_goal", h.weeklyGoal)
            }
            w.insertWithOnConflict("hobbies", null, hv, SQLiteDatabase.CONFLICT_REPLACE)
            for (l in snapshot.logs) {
                val lv = ContentValues().apply {
                    put("id", l.id)
                    put("hobby_id", l.hobbyId)
                    put("entry", l.entry)
                    put("created_at", l.createdAt)
                    if (l.rating == null) putNull("rating") else put("rating", l.rating)
                    if (l.photoUri == null) putNull("photo_uri") else put("photo_uri", l.photoUri)
                }
                w.insertWithOnConflict("logs", null, lv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
        refreshSync()
    }

    suspend fun togglePin(id: Long, current: Boolean) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put("is_pinned", if (current) 0 else 1) }
        db.writableDatabase.update("hobbies", values, "id = ?", arrayOf(id.toString()))
        refreshSync()
    }

    suspend fun archiveHobby(id: Long) = withContext(Dispatchers.IO) {
        db.writableDatabase.update(
            "hobbies", ContentValues().apply { put("is_archived", 1) },
            "id = ?", arrayOf(id.toString())
        )
        refreshSync()
    }

    suspend fun restoreHobby(id: Long) = withContext(Dispatchers.IO) {
        db.writableDatabase.update(
            "hobbies", ContentValues().apply { put("is_archived", 0) },
            "id = ?", arrayOf(id.toString())
        )
        refreshSync()
    }

    suspend fun updateReminder(
        hobbyId: Long, nextReminderAt: Long?, recurrence: Recurrence
    ) = withContext(Dispatchers.IO) {
        val (type, data) = recurrence.encode()
        val intervalHours = (recurrence as? Recurrence.Hourly)?.hours ?: 0L
        val values = ContentValues().apply {
            if (nextReminderAt == null) putNull("next_reminder_at") else put("next_reminder_at", nextReminderAt)
            put("reminder_interval_hours", intervalHours)
            put("recurrence_type", type)
            put("recurrence_data", data)
        }
        db.writableDatabase.update("hobbies", values, "id = ?", arrayOf(hobbyId.toString()))
        refreshSync()
    }

    // ── Log queries ───────────────────────────────────────────────────────────

    suspend fun addLog(hobbyId: Long, entry: String, rating: Int?, photoUri: String?): Long =
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("hobby_id", hobbyId)
                put("entry", entry.trim())
                put("created_at", System.currentTimeMillis())
                if (rating == null) putNull("rating") else put("rating", rating)
                if (photoUri == null) putNull("photo_uri") else put("photo_uri", photoUri)
            }
            val id = db.writableDatabase.insert("logs", null, values)
            refreshSync()
            id
        }

    suspend fun insertLog(log: HobbyLog): Long = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("hobby_id", log.hobbyId)
            put("entry", log.entry)
            put("created_at", log.createdAt)
            if (log.rating == null) putNull("rating") else put("rating", log.rating)
            if (log.photoUri == null) putNull("photo_uri") else put("photo_uri", log.photoUri)
        }
        db.writableDatabase.insert("logs", null, values)
    }

    suspend fun deleteLog(id: Long) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("logs", "id = ?", arrayOf(id.toString()))
        refreshSync()
    }

    suspend fun fetchLog(id: Long): HobbyLog? = withContext(Dispatchers.IO) {
        db.readableDatabase.rawQuery(
            "SELECT id, hobby_id, entry, created_at, rating, photo_uri FROM logs WHERE id = ?",
            arrayOf(id.toString())
        ).use { c ->
            if (!c.moveToFirst()) null
            else HobbyLog(
                id = c.getLong(0),
                hobbyId = c.getLong(1),
                entry = c.getString(2),
                createdAt = c.getLong(3),
                rating = if (c.isNull(4)) null else c.getInt(4),
                photoUri = if (c.isNull(5)) null else c.getString(5)
            )
        }
    }

    suspend fun detail(hobbyId: Long): HobbyDetail? = withContext(Dispatchers.IO) {
        val hobby = db.readableDatabase.rawQuery(
            """SELECT id, name, category, notes, next_reminder_at,
                      created_at, is_pinned, is_archived, reminder_interval_hours,
                      recurrence_type, recurrence_data, weekly_goal, sort_order
               FROM hobbies WHERE id = ?""",
            arrayOf(hobbyId.toString())
        ).use { c ->
            if (!c.moveToFirst()) return@withContext null
            c.toHobby()
        }
        HobbyDetail(hobby, queryLogs(hobbyId))
    }

    private fun queryLogs(hobbyId: Long): List<HobbyLog> =
        db.readableDatabase.rawQuery(
            "SELECT id, hobby_id, entry, created_at, rating, photo_uri FROM logs WHERE hobby_id = ? ORDER BY created_at DESC",
            arrayOf(hobbyId.toString())
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    HobbyLog(
                        id = c.getLong(0),
                        hobbyId = c.getLong(1),
                        entry = c.getString(2),
                        createdAt = c.getLong(3),
                        rating = if (c.isNull(4)) null else c.getInt(4),
                        photoUri = if (c.isNull(5)) null else c.getString(5)
                    )
                )
            }
        }

    /**
     * Search logs by entry text. Returns at most [limit] results joined with their hobby's name.
     */
    suspend fun searchLogs(query: String, limit: Int = 50): List<LogSearchHit> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        db.readableDatabase.rawQuery(
            """
            SELECT l.id, l.hobby_id, l.entry, l.created_at, l.rating, l.photo_uri,
                   h.name, h.category
            FROM logs l JOIN hobbies h ON h.id = l.hobby_id
            WHERE h.is_archived = 0 AND l.entry LIKE ?
            ORDER BY l.created_at DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf("%$q%", limit.toString())
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    LogSearchHit(
                        log = HobbyLog(
                            id = c.getLong(0),
                            hobbyId = c.getLong(1),
                            entry = c.getString(2),
                            createdAt = c.getLong(3),
                            rating = if (c.isNull(4)) null else c.getInt(4),
                            photoUri = if (c.isNull(5)) null else c.getString(5)
                        ),
                        hobbyName = c.getString(6),
                        hobbyCategory = c.getString(7)
                    )
                )
            }
        }
    }

    // ── Synchronous accessors for receivers / widgets ─────────────────────────

    fun hobbyByIdSync(hobbyId: Long): Hobby? =
        db.readableDatabase.rawQuery(
            """SELECT id, name, category, notes, next_reminder_at,
                      created_at, is_pinned, is_archived, reminder_interval_hours,
                      recurrence_type, recurrence_data, weekly_goal, sort_order
               FROM hobbies WHERE id = ?""",
            arrayOf(hobbyId.toString())
        ).use { c -> if (c.moveToFirst()) c.toHobby() else null }

    fun activeHobbiesWithReminderSync(): List<Hobby> =
        db.readableDatabase.rawQuery(
            """SELECT id, name, category, notes, next_reminder_at,
                      created_at, is_pinned, is_archived, reminder_interval_hours,
                      recurrence_type, recurrence_data, weekly_goal, sort_order
               FROM hobbies
               WHERE is_archived = 0 AND next_reminder_at IS NOT NULL""".trimIndent(),
            null
        ).use { c -> buildList { while (c.moveToNext()) add(c.toHobby()) } }

    fun nextDueHobbySync(): Hobby? =
        db.readableDatabase.rawQuery(
            """SELECT id, name, category, notes, next_reminder_at,
                      created_at, is_pinned, is_archived, reminder_interval_hours,
                      recurrence_type, recurrence_data, weekly_goal, sort_order
               FROM hobbies
               WHERE is_archived = 0
               ORDER BY is_pinned DESC, next_reminder_at IS NULL, next_reminder_at ASC
               LIMIT 1""".trimIndent(),
            null
        ).use { c -> if (c.moveToFirst()) c.toHobby() else null }

    /** Used by ReminderReceiver after firing a notification. */
    fun updateReminderSync(hobbyId: Long, nextReminderAt: Long?) {
        val values = ContentValues().apply {
            if (nextReminderAt == null) putNull("next_reminder_at") else put("next_reminder_at", nextReminderAt)
        }
        db.writableDatabase.update("hobbies", values, "id = ?", arrayOf(hobbyId.toString()))
        refreshSync()
    }

    fun addLogSync(hobbyId: Long, entry: String): Long {
        val values = ContentValues().apply {
            put("hobby_id", hobbyId)
            put("entry", entry.trim())
            put("created_at", System.currentTimeMillis())
        }
        val id = db.writableDatabase.insert("logs", null, values)
        refreshSync()
        return id
    }

    /** Bulk reads used by Backup. */
    fun allHobbiesSync(): List<Hobby> {
        val list = mutableListOf<Hobby>()
        db.readableDatabase.rawQuery(
            """SELECT id, name, category, notes, next_reminder_at,
                      created_at, is_pinned, is_archived, reminder_interval_hours,
                      recurrence_type, recurrence_data, weekly_goal, sort_order
               FROM hobbies""", null
        ).use { c -> while (c.moveToNext()) list += c.toHobby() }
        return list
    }

    fun allLogsSync(): List<HobbyLog> {
        val list = mutableListOf<HobbyLog>()
        db.readableDatabase.rawQuery(
            "SELECT id, hobby_id, entry, created_at, rating, photo_uri FROM logs", null
        ).use { c ->
            while (c.moveToNext()) list += HobbyLog(
                id = c.getLong(0),
                hobbyId = c.getLong(1),
                entry = c.getString(2),
                createdAt = c.getLong(3),
                rating = if (c.isNull(4)) null else c.getInt(4),
                photoUri = if (c.isNull(5)) null else c.getString(5)
            )
        }
        return list
    }

    suspend fun replaceAll(hobbies: List<Hobby>, logs: List<HobbyLog>) = withContext(Dispatchers.IO) {
        val w: SQLiteDatabase = db.writableDatabase
        w.beginTransaction()
        try {
            w.delete("logs", null, null)
            w.delete("hobbies", null, null)
            for (h in hobbies) {
                val (type, data) = h.recurrence.encode()
                val v = ContentValues().apply {
                    put("id", h.id)
                    put("name", h.name)
                    put("category", h.category)
                    put("notes", h.notes)
                    if (h.nextReminderAt == null) putNull("next_reminder_at") else put("next_reminder_at", h.nextReminderAt)
                    put("created_at", h.createdAt)
                    put("is_pinned", if (h.isPinned) 1 else 0)
                    put("is_archived", if (h.isArchived) 1 else 0)
                    put("reminder_interval_hours", h.reminderIntervalHours)
                    put("recurrence_type", type)
                    put("recurrence_data", data)
                    put("weekly_goal", h.weeklyGoal)
                }
                w.insertWithOnConflict("hobbies", null, v, SQLiteDatabase.CONFLICT_REPLACE)
            }
            for (l in logs) {
                val v = ContentValues().apply {
                    put("id", l.id)
                    put("hobby_id", l.hobbyId)
                    put("entry", l.entry)
                    put("created_at", l.createdAt)
                    if (l.rating == null) putNull("rating") else put("rating", l.rating)
                    if (l.photoUri == null) putNull("photo_uri") else put("photo_uri", l.photoUri)
                }
                w.insertWithOnConflict("logs", null, v, SQLiteDatabase.CONFLICT_REPLACE)
            }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
        refreshSync()
    }

    // ── Log editing ───────────────────────────────────────────────────────────

    suspend fun updateLog(id: Long, entry: String, rating: Int?, photoUri: String?) =
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("entry", entry.trim())
                if (rating == null) putNull("rating") else put("rating", rating)
                if (photoUri == null) putNull("photo_uri") else put("photo_uri", photoUri)
            }
            db.writableDatabase.update("logs", values, "id = ?", arrayOf(id.toString()))
            refreshSync()
        }

    // ── Sort order ────────────────────────────────────────────────────────────

    suspend fun updateSortOrder(id: Long, order: Int) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put("sort_order", order) }
        db.writableDatabase.update("hobbies", values, "id = ?", arrayOf(id.toString()))
        refreshSync()
    }

    // ── Date-range log search ─────────────────────────────────────────────────

    suspend fun searchLogsByDateRange(
        hobbyId: Long, fromMs: Long, toMs: Long
    ): List<HobbyLog> = withContext(Dispatchers.IO) {
        db.readableDatabase.rawQuery(
            """SELECT id, hobby_id, entry, created_at, rating, photo_uri
               FROM logs
               WHERE hobby_id = ? AND created_at >= ? AND created_at <= ?
               ORDER BY created_at DESC""",
            arrayOf(hobbyId.toString(), fromMs.toString(), toMs.toString())
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    HobbyLog(
                        id = c.getLong(0),
                        hobbyId = c.getLong(1),
                        entry = c.getString(2),
                        createdAt = c.getLong(3),
                        rating = if (c.isNull(4)) null else c.getInt(4),
                        photoUri = if (c.isNull(5)) null else c.getString(5)
                    )
                )
            }
        }
    }

    /**
     * Efficient streak-rescue check: returns (hobby, streak) pairs for active
     * hobbies with streaks >= [minStreak] that haven't been logged today.
     * Uses precomputed logDaysByHobby to avoid per-hobby full-log queries.
     */
    fun streakCandidatesSync(minStreak: Int = 2): List<Pair<Hobby, Int>> {
        val zone = ZoneId.systemDefault()
        val today = java.time.LocalDate.now(zone)
        val todayMs = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val logDays = queryLogDays()
        return queryHobbies(archived = false).mapNotNull { hobby ->
            val days = logDays[hobby.id] ?: return@mapNotNull null
            val streak = HobbyViewModel.computeStreakFromDays(days)
            val loggedToday = days.any { it == todayMs }
            if (streak >= minStreak && !loggedToday) hobby to streak else null
        }
    }
}

data class LogSearchHit(
    val log: HobbyLog,
    val hobbyName: String,
    val hobbyCategory: String
)
