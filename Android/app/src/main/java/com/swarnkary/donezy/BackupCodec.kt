package com.swarnkary.donezy

import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON envelope for full-database backup/restore. Photo URIs are kept as-is;
 * if the user restores onto a fresh device the file:// targets won't exist,
 * but text/rating data round-trips faithfully.
 */
object BackupCodec {

    private const val VERSION = 4

    fun encode(hobbies: List<Hobby>, logs: List<HobbyLog>): String {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("exportedAt", System.currentTimeMillis())

        val hobbiesArr = JSONArray()
        for (h in hobbies) {
            val (type, data) = h.recurrence.encode()
            hobbiesArr.put(JSONObject().apply {
                put("id", h.id)
                put("name", h.name)
                put("category", h.category)
                put("notes", h.notes)
                if (h.nextReminderAt != null) put("nextReminderAt", h.nextReminderAt)
                put("createdAt", h.createdAt)
                put("isPinned", h.isPinned)
                put("isArchived", h.isArchived)
                put("reminderIntervalHours", h.reminderIntervalHours)
                put("recurrenceType", type)
                put("recurrenceData", data)
                put("weeklyGoal", h.weeklyGoal)
            })
        }
        root.put("hobbies", hobbiesArr)

        val logsArr = JSONArray()
        for (l in logs) {
            logsArr.put(JSONObject().apply {
                put("id", l.id)
                put("hobbyId", l.hobbyId)
                put("entry", l.entry)
                put("createdAt", l.createdAt)
                if (l.rating != null) put("rating", l.rating)
                if (l.photoUri != null) put("photoUri", l.photoUri)
            })
        }
        root.put("logs", logsArr)

        return root.toString(2)
    }

    fun decode(json: String): Pair<List<Hobby>, List<HobbyLog>> {
        val root = JSONObject(json)
        val hobbiesArr = root.optJSONArray("hobbies") ?: JSONArray()
        val logsArr = root.optJSONArray("logs") ?: JSONArray()

        val hobbies = buildList {
            for (i in 0 until hobbiesArr.length()) {
                val o = hobbiesArr.getJSONObject(i)
                val type = o.optString("recurrenceType", "none").ifBlank { "none" }
                val data = o.optString("recurrenceData", "")
                val intervalHours = o.optLong("reminderIntervalHours", 0L)
                val recurrence = if (type == "none" && intervalHours > 0L) Recurrence.Hourly(intervalHours)
                                 else Recurrence.decode(type, data)
                add(
                    Hobby(
                        id = o.getLong("id"),
                        name = o.getString("name"),
                        category = o.optString("category", "General"),
                        notes = o.optString("notes", ""),
                        nextReminderAt = if (o.has("nextReminderAt") && !o.isNull("nextReminderAt"))
                            o.getLong("nextReminderAt") else null,
                        createdAt = o.optLong("createdAt", 0L),
                        isPinned = o.optBoolean("isPinned", false),
                        isArchived = o.optBoolean("isArchived", false),
                        reminderIntervalHours = intervalHours,
                        recurrence = recurrence,
                        weeklyGoal = o.optInt("weeklyGoal", 0)
                    )
                )
            }
        }
        val logs = buildList {
            for (i in 0 until logsArr.length()) {
                val o = logsArr.getJSONObject(i)
                add(
                    HobbyLog(
                        id = o.getLong("id"),
                        hobbyId = o.getLong("hobbyId"),
                        entry = o.optString("entry", ""),
                        createdAt = o.optLong("createdAt", 0L),
                        rating = if (o.has("rating") && !o.isNull("rating")) o.getInt("rating") else null,
                        photoUri = if (o.has("photoUri") && !o.isNull("photoUri")) o.getString("photoUri") else null
                    )
                )
            }
        }
        return hobbies to logs
    }
}
