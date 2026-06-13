package com.example.hobbylog

data class Hobby(
    val id: Long,
    val name: String,
    val category: String,
    val notes: String,
    val nextReminderAt: Long?,
    val createdAt: Long,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val reminderIntervalHours: Long = 0L   // 0 = one-shot, >0 = auto-recurring
)

data class HobbyLog(
    val id: Long,
    val hobbyId: Long,
    val entry: String,
    val createdAt: Long,
    val rating: Int? = null   // 1–5 stars; null = no rating
)

data class HobbyDetail(
    val hobby: Hobby,
    val logs: List<HobbyLog>
)
