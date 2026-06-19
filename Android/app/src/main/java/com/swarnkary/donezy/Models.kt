package com.swarnkary.donezy

/**
 * Recurrence rule encoded as `type:data`.
 *
 *  - `none`                       — no auto-recurrence (one-shot or no reminder)
 *  - `hours:<n>`                  — every N hours (legacy compatible: `reminder_interval_hours`)
 *  - `daily`                      — every day at the same wall-clock time
 *  - `weekly:<dayMask>`           — selected weekdays (bit 0 = Mon … bit 6 = Sun)
 *  - `monthly:<dayOfMonth>`       — same day of month (1–31, clamped)
 *
 * Persisted columns: `recurrence_type` + `recurrence_data`.
 * `reminder_interval_hours` is kept for v2 compatibility and mirrors `hours:<n>`.
 */
sealed class Recurrence {
    object None : Recurrence()
    data class Hourly(val hours: Long) : Recurrence()
    object Daily : Recurrence()
    data class Weekly(val dayMask: Int) : Recurrence()   // bit 0 = Mon, bit 6 = Sun
    data class Monthly(val dayOfMonth: Int) : Recurrence()

    fun encode(): Pair<String, String> = when (this) {
        None              -> "none" to ""
        is Hourly         -> "hours" to hours.toString()
        Daily             -> "daily" to ""
        is Weekly         -> "weekly" to dayMask.toString()
        is Monthly        -> "monthly" to dayOfMonth.toString()
    }

    val isRecurring: Boolean get() = this !is None

    companion object {
        fun decode(type: String?, data: String?): Recurrence = when (type) {
            "hours"   -> Hourly(data?.toLongOrNull()?.coerceAtLeast(1L) ?: 24L)
            "daily"   -> Daily
            "weekly"  -> Weekly((data?.toIntOrNull() ?: 0).coerceIn(0, 0x7F))
            "monthly" -> Monthly((data?.toIntOrNull() ?: 1).coerceIn(1, 31))
            else      -> None
        }
    }
}

data class Hobby(
    val id: Long,
    val name: String,
    val category: String,
    val notes: String,
    val nextReminderAt: Long?,
    val createdAt: Long,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val reminderIntervalHours: Long = 0L,   // legacy; mirrors recurrence when Hourly
    val recurrence: Recurrence = Recurrence.None,
    val weeklyGoal: Int = 0,                 // 0 = no goal
    val sortOrder: Int = 0                   // custom manual sort order
)

data class HobbyLog(
    val id: Long,
    val hobbyId: Long,
    val entry: String,
    val createdAt: Long,
    val rating: Int? = null,
    val photoUri: String? = null
)

data class HobbyDetail(
    val hobby: Hobby,
    val logs: List<HobbyLog>
)
