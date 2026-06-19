package com.swarnkary.donezy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecTest {

    @Test
    fun testRoundTrip() {
        val hobby1 = Hobby(
            id = 1L,
            name = "Reading",
            category = "Education",
            notes = "Read daily",
            nextReminderAt = 123456789L,
            createdAt = 100000000L,
            isPinned = true,
            isArchived = false,
            reminderIntervalHours = 24L,
            recurrence = Recurrence.Daily,
            weeklyGoal = 5,
            sortOrder = 2
        )
        val hobby2 = Hobby(
            id = 2L,
            name = "Gym",
            category = "Health",
            notes = "Workout",
            nextReminderAt = null,
            createdAt = 200000000L,
            isPinned = false,
            isArchived = true,
            reminderIntervalHours = 0L,
            recurrence = Recurrence.None,
            weeklyGoal = 3,
            sortOrder = 1
        )

        val log1 = HobbyLog(
            id = 10L,
            hobbyId = 1L,
            entry = "Read 10 pages",
            createdAt = 100050000L,
            rating = 5,
            photoUri = "file:///dummy/path.jpg"
        )
        val log2 = HobbyLog(
            id = 20L,
            hobbyId = 2L,
            entry = "Leg day",
            createdAt = 200050000L,
            rating = null,
            photoUri = null
        )

        val hobbies = listOf(hobby1, hobby2)
        val logs = listOf(log1, log2)

        val json = BackupCodec.encode(hobbies, logs)
        val (decodedHobbies, decodedLogs) = BackupCodec.decode(json)

        assertEquals(hobbies.size, decodedHobbies.size)
        assertEquals(logs.size, decodedLogs.size)

        // Verify Hobby 1
        val h1 = decodedHobbies.first { it.id == 1L }
        assertEquals("Reading", h1.name)
        assertEquals("Education", h1.category)
        assertEquals("Read daily", h1.notes)
        assertEquals(123456789L, h1.nextReminderAt)
        assertEquals(100000000L, h1.createdAt)
        assertEquals(true, h1.isPinned)
        assertEquals(false, h1.isArchived)
        assertEquals(24L, h1.reminderIntervalHours)
        assertEquals(Recurrence.Daily, h1.recurrence)
        assertEquals(5, h1.weeklyGoal)
        assertEquals(2, h1.sortOrder)

        // Verify Hobby 2
        val h2 = decodedHobbies.first { it.id == 2L }
        assertEquals("Gym", h2.name)
        assertEquals("Health", h2.category)
        assertEquals("Workout", h2.notes)
        assertEquals(null, h2.nextReminderAt)
        assertEquals(200000000L, h2.createdAt)
        assertEquals(false, h2.isPinned)
        assertEquals(true, h2.isArchived)
        assertEquals(0L, h2.reminderIntervalHours)
        assertEquals(Recurrence.None, h2.recurrence)
        assertEquals(3, h2.weeklyGoal)
        assertEquals(1, h2.sortOrder)

        // Verify Log 1
        val l1 = decodedLogs.first { it.id == 10L }
        assertEquals(1L, l1.hobbyId)
        assertEquals("Read 10 pages", l1.entry)
        assertEquals(100050000L, l1.createdAt)
        assertEquals(5, l1.rating)
        assertEquals("file:///dummy/path.jpg", l1.photoUri)

        // Verify Log 2
        val l2 = decodedLogs.first { it.id == 20L }
        assertEquals(2L, l2.hobbyId)
        assertEquals("Leg day", l2.entry)
        assertEquals(200050000L, l2.createdAt)
        assertEquals(null, l2.rating)
        assertEquals(null, l2.photoUri)
    }

    @Test
    fun testFutureVersionValidation() {
        val futureJson = """
            {
              "version": 99,
              "hobbies": [],
              "logs": []
            }
        """.trimIndent()

        assertThrows(IllegalStateException::class.java) {
            BackupCodec.decode(futureJson)
        }
    }

    @Test
    fun testDefaultFallbackForOlderBackups() {
        val oldJson = """
            {
              "version": 1,
              "hobbies": [
                {
                  "id": 1,
                  "name": "Reading"
                }
              ],
              "logs": [
                {
                  "id": 10,
                  "hobbyId": 1
                }
              ]
            }
        """.trimIndent()

        val (decodedHobbies, decodedLogs) = BackupCodec.decode(oldJson)
        assertEquals(1, decodedHobbies.size)
        val h = decodedHobbies.first()
        assertEquals(1L, h.id)
        assertEquals("Reading", h.name)
        assertEquals("General", h.category)
        assertEquals("", h.notes)
        assertEquals(0, h.weeklyGoal)
        assertEquals(0, h.sortOrder)

        assertEquals(1, decodedLogs.size)
        val l = decodedLogs.first()
        assertEquals(10L, l.id)
        assertEquals(1L, l.hobbyId)
        assertEquals("", l.entry)
        assertEquals(0L, l.createdAt)
        assertEquals(null, l.rating)
        assertEquals(null, l.photoUri)
    }
}
