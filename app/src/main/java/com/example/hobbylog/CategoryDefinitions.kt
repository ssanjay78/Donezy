package com.example.hobbylog

import androidx.compose.ui.graphics.Color

// ─── Category definitions ─────────────────────────────────────────────────────

data class CategoryOption(
    val label: String,
    val emoji: String,
    val description: String,
    val starterNotes: String,
    val accent: Color
)

val hobbyCategories = listOf(
    CategoryOption("General",     "📋", "Flexible trackers for anything recurring.",
        "What matters, how often it needs attention, and what a good update looks like.",
        Color(0xFF4E5D6C)),
    CategoryOption("Plants",      "🌱", "Watering, pruning, fertilizer, sunlight, pests.",
        "Plant species, watering preference, light conditions, fertilizer schedule, and warning signs.",
        Color(0xFF2D7A46)),
    CategoryOption("Fitness",     "💪", "Workouts, mobility, recovery, steps, practice streaks.",
        "Routine, target frequency, current baseline, progress marker, and recovery notes.",
        Color(0xFFB14E32)),
    CategoryOption("Reading",     "📚", "Books, chapters, notes, reading streaks.",
        "Current book, chapter target, themes to capture, and next reading checkpoint.",
        Color(0xFF6F4A8E)),
    CategoryOption("Music",       "🎵", "Instrument practice, drills, songs, recordings.",
        "Warmups, technique focus, piece/song, tempo target, and recording notes.",
        Color(0xFF2E6F95)),
    CategoryOption("Coffee",      "☕", "Beans, grind, recipes, brew ratios, tasting notes.",
        "Bean, roast date, grinder setting, dose, yield, time, and taste notes.",
        Color(0xFF8A5A2B)),
    CategoryOption("Aquarium",    "🐠", "Water tests, feeding, cleaning, livestock health.",
        "Tank size, parameters to test, feeding cadence, cleaning routine, and livestock observations.",
        Color(0xFF207A8A)),
    CategoryOption("Collecting",  "🎒", "Sneakers, keyboards, watches, figures, catalog care.",
        "Item details, condition, purchase date, care routine, and rotation notes.",
        Color(0xFF8B5E83)),
    CategoryOption("Maintenance", "🔧", "Home, gear, bike, car, filters, renewals.",
        "Asset, recurring task, parts/tools, last service date, and next service trigger.",
        Color(0xFF6B6D3F)),
    CategoryOption("Creative",    "🎨", "Writing, sketching, photos, side projects.",
        "Project goal, next small action, reference ideas, and review cadence.",
        Color(0xFFB05D6D)),
    CategoryOption("Learning",    "🧠", "Courses, language, coding, certifications.",
        "Topic, resource, practice plan, review checkpoints, and questions to revisit.",
        Color(0xFF4867A5))
)

fun categoryFor(label: String): CategoryOption {
    val trimmed = label.trim()
    return hobbyCategories.firstOrNull { it.label.equals(trimmed, ignoreCase = true) }
        ?: hobbyCategories.first().copy(
            label = trimmed.ifBlank { "General" },
            emoji = "📋",
            description = "Custom tracker category.",
            starterNotes = "What to track, when to check in, and what a useful note should include."
        )
}

// ─── Reminder options ────────────────────────────────────────────────────────

data class ReminderOption(val label: String, val hours: Long, val description: String)

val reminderOptions = listOf(
    ReminderOption("None", 0L,   "No reminder"),
    ReminderOption("1h",   1L,   "Quick follow-up"),
    ReminderOption("3h",   3L,   "Later today"),
    ReminderOption("6h",   6L,   "Half day"),
    ReminderOption("12h",  12L,  "Tonight"),
    ReminderOption("1d",   24L,  "Tomorrow"),
    ReminderOption("2d",   48L,  "Two days"),
    ReminderOption("3d",   72L,  "Three days"),
    ReminderOption("1w",   168L, "Weekly"),
    ReminderOption("2w",   336L, "Bi-weekly"),
    ReminderOption("30d",  720L, "Monthly")
)

fun reminderLabel(hours: Long): String =
    reminderOptions.firstOrNull { it.hours == hours }?.description ?: "${hours}h"

// ─── Starter templates ───────────────────────────────────────────────────────

data class HobbyTemplate(
    val title: String,
    val category: String,
    val trackerName: String,
    val notes: String,
    val reminderHours: Long
)

val hobbyTemplates = listOf(
    HobbyTemplate("Plant care",       "Plants",     "Monstera care",
        "Track watering, leaf checks, pruning, fertilizer, and light changes.", 72L),
    HobbyTemplate("Coffee dial-in",   "Coffee",     "Espresso recipe",
        "Record bean, dose, yield, grind, shot time, and taste notes.", 0L),
    HobbyTemplate("Aquarium routine", "Aquarium",   "Tank maintenance",
        "Log water parameters, feeding, filter checks, cleaning, and livestock health.", 168L),
    HobbyTemplate("Practice streak",  "Music",      "Guitar practice",
        "Capture warmups, drills, song sections, tempo, and recordings.", 24L),
    HobbyTemplate("Gear rotation",    "Collecting", "Sneaker rotation",
        "Track wears, cleaning, condition, weather, and storage notes.", 336L),
    HobbyTemplate("Study plan",       "Learning",   "Android learning",
        "Plan lessons, exercises, questions, review dates, and small projects.", 24L)
)

// ─── Quick-log presets ───────────────────────────────────────────────────────

data class QuickLogPreset(val label: String, val entry: String)

fun quickLogPresetsFor(category: String): List<QuickLogPreset> =
    when (categoryFor(category).label) {
        "Plants"      -> listOf(
            QuickLogPreset("Watered",    "Watered and checked soil moisture."),
            QuickLogPreset("Fertilized", "Added fertilizer and noted plant response."),
            QuickLogPreset("Leaf check", "Checked leaves for pests, yellowing, and new growth."))
        "Fitness"     -> listOf(
            QuickLogPreset("Workout",    "Completed workout and logged effort level."),
            QuickLogPreset("Mobility",   "Completed mobility or stretching session."),
            QuickLogPreset("Recovery",   "Logged rest, soreness, sleep, or recovery notes."))
        "Reading"     -> listOf(
            QuickLogPreset("Read",       "Read a session and captured key takeaways."),
            QuickLogPreset("Quote",      "Saved a memorable idea or quote."),
            QuickLogPreset("Review",     "Reviewed notes and updated next reading target."))
        "Music"       -> listOf(
            QuickLogPreset("Practice",   "Completed practice session with focus notes."),
            QuickLogPreset("Tempo",      "Worked on tempo, timing, or difficult section."),
            QuickLogPreset("Recorded",   "Recorded a take and noted improvements."))
        "Coffee"      -> listOf(
            QuickLogPreset("Brewed",     "Logged brew recipe, extraction, and taste."),
            QuickLogPreset("Adjusted",   "Adjusted grind, dose, yield, or brew time."),
            QuickLogPreset("Tasted",     "Captured tasting notes and next change."))
        "Aquarium"    -> listOf(
            QuickLogPreset("Fed",        "Fed livestock and checked behavior."),
            QuickLogPreset("Tested",     "Tested water parameters and logged results."),
            QuickLogPreset("Cleaned",    "Completed cleaning, water change, or filter check."))
        "Collecting"  -> listOf(
            QuickLogPreset("Used",       "Logged use, wear, condition, and storage notes."),
            QuickLogPreset("Cleaned",    "Cleaned item and checked condition."),
            QuickLogPreset("Cataloged",  "Updated catalog details or purchase notes."))
        "Maintenance" -> listOf(
            QuickLogPreset("Serviced",   "Completed maintenance task and noted parts/tools."),
            QuickLogPreset("Inspected",  "Inspected condition and captured follow-up items."),
            QuickLogPreset("Renewed",    "Updated renewal, warranty, or replacement details."))
        "Creative"    -> listOf(
            QuickLogPreset("Drafted",    "Moved the project forward with a focused work session."),
            QuickLogPreset("Reviewed",   "Reviewed output and captured next improvements."),
            QuickLogPreset("Published",  "Shared, exported, or archived a finished piece."))
        "Learning"    -> listOf(
            QuickLogPreset("Studied",    "Completed a learning session and captured notes."),
            QuickLogPreset("Practiced",  "Finished exercises or applied the concept."),
            QuickLogPreset("Reviewed",   "Reviewed mistakes, questions, and next lesson."))
        else -> listOf(
            QuickLogPreset("Done",       "Completed a check-in and logged the result."),
            QuickLogPreset("Checked",    "Checked status and captured observations."),
            QuickLogPreset("Updated",    "Updated notes and next action."))
    }
