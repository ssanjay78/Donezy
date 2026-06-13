package com.example.hobbylog

import android.content.ContentValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class HobbyRepository(private val db: AppDatabase) {

    private val _hobbies = MutableStateFlow<List<Hobby>>(emptyList())
    val hobbies: StateFlow<List<Hobby>> = _hobbies

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _hobbies.value = queryHobbies()
    }

    private fun queryHobbies(): List<Hobby> =
        db.readableDatabase.rawQuery(
            """
            SELECT id, name, category, notes, next_reminder_at,
                   created_at, is_pinned, is_archived, reminder_interval_hours
            FROM hobbies
            WHERE is_archived = 0
            ORDER BY is_pinned DESC, next_reminder_at IS NULL, next_reminder_at ASC
            """.trimIndent(),
            null
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        Hobby(
                            id = cursor.getLong(0),
                            name = cursor.getString(1),
                            category = cursor.getString(2),
                            notes = cursor.getString(3),
                            nextReminderAt = if (cursor.isNull(4)) null else cursor.getLong(4),
                            createdAt = cursor.getLong(5),
                            isPinned = cursor.getInt(6) != 0,
                            isArchived = cursor.getInt(7) != 0,
                            reminderIntervalHours = cursor.getLong(8)
                        )
                    )
                }
            }
        }

    suspend fun addHobby(
        name: String,
        category: String,
        notes: String,
        nextReminderAt: Long?,
        reminderIntervalHours: Long
    ): Long = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("name", name.trim())
            put("category", category.trim())
            put("notes", notes.trim())
            if (nextReminderAt == null) putNull("next_reminder_at") else put("next_reminder_at", nextReminderAt)
            put("created_at", System.currentTimeMillis())
            put("is_pinned", 0)
            put("is_archived", 0)
            put("reminder_interval_hours", reminderIntervalHours)
        }
        val id = db.writableDatabase.insert("hobbies", null, values)
        _hobbies.value = queryHobbies()
        id
    }

    suspend fun updateHobby(
        id: Long,
        name: String,
        category: String,
        notes: String
    ) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("name", name.trim())
            put("category", category.trim())
            put("notes", notes.trim())
        }
        db.writableDatabase.update("hobbies", values, "id = ?", arrayOf(id.toString()))
        _hobbies.value = queryHobbies()
    }

    suspend fun deleteHobby(id: Long) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("hobbies", "id = ?", arrayOf(id.toString()))
        _hobbies.value = queryHobbies()
    }

    suspend fun togglePin(id: Long, current: Boolean) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put("is_pinned", if (current) 0 else 1) }
        db.writableDatabase.update("hobbies", values, "id = ?", arrayOf(id.toString()))
        _hobbies.value = queryHobbies()
    }

    suspend fun archiveHobby(id: Long) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put("is_archived", 1) }
        db.writableDatabase.update("hobbies", values, "id = ?", arrayOf(id.toString()))
        _hobbies.value = queryHobbies()
    }

    suspend fun updateReminder(
        hobbyId: Long,
        nextReminderAt: Long?,
        reminderIntervalHours: Long
    ) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            if (nextReminderAt == null) putNull("next_reminder_at") else put("next_reminder_at", nextReminderAt)
            put("reminder_interval_hours", reminderIntervalHours)
        }
        db.writableDatabase.update("hobbies", values, "id = ?", arrayOf(hobbyId.toString()))
        _hobbies.value = queryHobbies()
    }

    suspend fun addLog(hobbyId: Long, entry: String, rating: Int?): Long = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("hobby_id", hobbyId)
            put("entry", entry.trim())
            put("created_at", System.currentTimeMillis())
            if (rating == null) putNull("rating") else put("rating", rating)
        }
        db.writableDatabase.insert("logs", null, values)
    }

    suspend fun deleteLog(id: Long) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("logs", "id = ?", arrayOf(id.toString()))
    }

    suspend fun detail(hobbyId: Long): HobbyDetail? = withContext(Dispatchers.IO) {
        val hobby = db.readableDatabase.rawQuery(
            """SELECT id, name, category, notes, next_reminder_at,
                      created_at, is_pinned, is_archived, reminder_interval_hours
               FROM hobbies WHERE id = ?""",
            arrayOf(hobbyId.toString())
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@withContext null
            Hobby(
                id = cursor.getLong(0),
                name = cursor.getString(1),
                category = cursor.getString(2),
                notes = cursor.getString(3),
                nextReminderAt = if (cursor.isNull(4)) null else cursor.getLong(4),
                createdAt = cursor.getLong(5),
                isPinned = cursor.getInt(6) != 0,
                isArchived = cursor.getInt(7) != 0,
                reminderIntervalHours = cursor.getLong(8)
            )
        }

        val logs = db.readableDatabase.rawQuery(
            "SELECT id, hobby_id, entry, created_at, rating FROM logs WHERE hobby_id = ? ORDER BY created_at DESC",
            arrayOf(hobbyId.toString())
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        HobbyLog(
                            id = cursor.getLong(0),
                            hobbyId = cursor.getLong(1),
                            entry = cursor.getString(2),
                            createdAt = cursor.getLong(3),
                            rating = if (cursor.isNull(4)) null else cursor.getInt(4)
                        )
                    )
                }
            }
        }
        HobbyDetail(hobby, logs)
    }

    /** Returns the hobby's reminderIntervalHours — used by the receiver to reschedule. */
    fun reminderIntervalHoursSync(hobbyId: Long): Long =
        db.readableDatabase.rawQuery(
            "SELECT reminder_interval_hours FROM hobbies WHERE id = ?",
            arrayOf(hobbyId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }

    /** Called from BroadcastReceiver to update next_reminder_at after auto-reschedule. */
    fun updateReminderSync(hobbyId: Long, nextReminderAt: Long?) {
        val values = ContentValues().apply {
            if (nextReminderAt == null) putNull("next_reminder_at") else put("next_reminder_at", nextReminderAt)
        }
        db.writableDatabase.update("hobbies", values, "id = ?", arrayOf(hobbyId.toString()))
    }
}
