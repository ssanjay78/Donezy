package com.swarnkary.donezy

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
    CategoryOption("Garden",      "🌻", "Outdoor beds, lawn, compost, harvests, seasonal jobs.",
        "Plot/bed, current crops, watering days, fertilizer or compost cadence, and pest watch.",
        Color(0xFF6E8B3D)),
    CategoryOption("Fitness",     "💪", "Workouts, mobility, recovery, steps, practice streaks.",
        "Routine, target frequency, current baseline, progress marker, and recovery notes.",
        Color(0xFFB14E32)),
    CategoryOption("Mindfulness", "🧘", "Meditation, journaling, breathing, gratitude.",
        "Practice length, technique, intentions, and how you felt before/after.",
        Color(0xFF7E57C2)),
    CategoryOption("Reading",     "📚", "Books, chapters, notes, reading streaks.",
        "Current book, chapter target, themes to capture, and next reading checkpoint.",
        Color(0xFF6F4A8E)),
    CategoryOption("Learning",    "🧠", "Courses, language, coding, certifications.",
        "Topic, resource, practice plan, review checkpoints, and questions to revisit.",
        Color(0xFF4867A5)),
    CategoryOption("Music",       "🎵", "Instrument practice, drills, songs, recordings.",
        "Warmups, technique focus, piece/song, tempo target, and recording notes.",
        Color(0xFF2E6F95)),
    CategoryOption("Creative",    "🎨", "Writing, sketching, photos, side projects.",
        "Project goal, next small action, reference ideas, and review cadence.",
        Color(0xFFB05D6D)),
    CategoryOption("Cooking",     "🍳", "Recipes, meal prep, baking, fermentation, kitchen wins.",
        "Recipe or technique, ingredients, cooking time, taste notes, and what to change next.",
        Color(0xFFE07A1F)),
    CategoryOption("Coffee",      "☕", "Beans, grind, recipes, brew ratios, tasting notes.",
        "Bean, roast date, grinder setting, dose, yield, time, and taste notes.",
        Color(0xFF8A5A2B)),
    CategoryOption("Pets",        "🐾", "Feeding, walks, grooming, vet, training sessions.",
        "Pet name, feeding cadence, walk/play schedule, grooming routine, and vet milestones.",
        Color(0xFF8D6E63)),
    CategoryOption("Aquarium",    "🐠", "Water tests, feeding, cleaning, livestock health.",
        "Tank size, parameters to test, feeding cadence, cleaning routine, and livestock observations.",
        Color(0xFF207A8A)),
    CategoryOption("Travel",      "✈️", "Trips, packing, places visited, expense receipts.",
        "Destination, dates, packing list, must-see places, and cost log.",
        Color(0xFF0097A7)),
    CategoryOption("Gaming",      "🎮", "Game progress, sessions, achievements, backlog.",
        "Title, current chapter/rank, target achievement, session length, and backlog notes.",
        Color(0xFF5C6BC0)),
    CategoryOption("Tech",        "💻", "Devices, side projects, builds, deployments, learning logs.",
        "Project or device, current task, dependencies, blockers, and next milestone.",
        Color(0xFF455A64)),
    CategoryOption("Work",        "💼", "Standups, deliverables, deep-work sessions, follow-ups.",
        "Goal, current task, blockers, time spent, and what's queued for tomorrow.",
        Color(0xFF37474F)),
    CategoryOption("Finance",     "💰", "Bills, budgets, subscriptions, investments, savings goals.",
        "Account/bill name, due date or cadence, target amount, and balance trend.",
        Color(0xFF388E3C)),
    CategoryOption("Collecting",  "🎒", "Sneakers, keyboards, watches, figures, catalog care.",
        "Item details, condition, purchase date, care routine, and rotation notes.",
        Color(0xFF8B5E83)),
    CategoryOption("Maintenance", "🔧", "Home, gear, bike, car, filters, renewals.",
        "Asset, recurring task, parts/tools, last service date, and next service trigger.",
        Color(0xFF6B6D3F))
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
    HobbyTemplate("Plant care",        "Plants",      "Monstera care",
        "Track watering, leaf checks, pruning, fertilizer, and light changes.", 72L),
    HobbyTemplate("Daily meditation",  "Mindfulness", "Morning meditation",
        "Capture practice length, technique, intention, and how you felt before/after.", 24L),
    HobbyTemplate("Coffee dial-in",    "Coffee",      "Espresso recipe",
        "Record bean, dose, yield, grind, shot time, and taste notes.", 0L),
    HobbyTemplate("Cooking journal",   "Cooking",     "Weeknight dinners",
        "Recipe, ingredients, cooking time, taste notes, and what to change next.", 0L),
    HobbyTemplate("Aquarium routine",  "Aquarium",    "Tank maintenance",
        "Log water parameters, feeding, filter checks, cleaning, and livestock health.", 168L),
    HobbyTemplate("Pet care",          "Pets",        "Dog routine",
        "Walks, feeding, training drills, grooming, and vet milestones.", 12L),
    HobbyTemplate("Practice streak",   "Music",       "Guitar practice",
        "Capture warmups, drills, song sections, tempo, and recordings.", 24L),
    HobbyTemplate("Gear rotation",     "Collecting",  "Sneaker rotation",
        "Track wears, cleaning, condition, weather, and storage notes.", 336L),
    HobbyTemplate("Study plan",        "Learning",    "Android learning",
        "Plan lessons, exercises, questions, review dates, and small projects.", 24L),
    HobbyTemplate("Reading list",      "Reading",     "Currently reading",
        "Current book, chapter target, themes to capture, and weekly reading goal.", 24L),
    HobbyTemplate("Side project",      "Tech",        "Side project",
        "Goal, current task, blockers, deploy/test cadence, next milestone.", 48L),
    HobbyTemplate("Workout split",     "Fitness",     "Push/Pull/Legs",
        "Split day, key lifts, weight progression, mobility, recovery score.", 24L),
    HobbyTemplate("Bills tracker",     "Finance",     "Monthly bills",
        "Bill name, due date, autopay, amount, and renewal notes.", 720L),
    HobbyTemplate("Gaming backlog",    "Gaming",      "Backlog progress",
        "Title, current chapter/rank, target achievement, and total play hours.", 168L),
    HobbyTemplate("Trip planner",      "Travel",      "Next trip",
        "Destination, dates, packing list, must-see places, and cost log.", 0L),
    HobbyTemplate("Garden bed",        "Garden",      "Veggie bed",
        "Bed, planted crops, watering days, fertilizer, and harvest notes.", 24L),
    HobbyTemplate("Daily journal",     "Mindfulness", "Daily journal",
        "Three things, mood, gratitude, and one intention for tomorrow.", 24L),
    HobbyTemplate("Work standup",      "Work",        "Daily standup",
        "Done yesterday, doing today, blockers, and follow-ups owed.", 24L)
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
        "Mindfulness" -> listOf(
            QuickLogPreset("Meditated",  "Completed a meditation or breathing session."),
            QuickLogPreset("Journaled",  "Wrote a reflection or gratitude entry."),
            QuickLogPreset("Mood check", "Captured mood and energy level."))
        "Cooking"     -> listOf(
            QuickLogPreset("Cooked",     "Cooked a meal and noted recipe and outcome."),
            QuickLogPreset("Prepped",    "Did meal prep and stocked containers."),
            QuickLogPreset("Tasted",     "Captured tasting notes and what to tweak."))
        "Pets"        -> listOf(
            QuickLogPreset("Fed",        "Fed pet on schedule."),
            QuickLogPreset("Walked",     "Took pet for a walk or play session."),
            QuickLogPreset("Vet",        "Vet visit, grooming, or care note logged."))
        "Travel"      -> listOf(
            QuickLogPreset("Visited",    "Logged a place visited and quick highlights."),
            QuickLogPreset("Spent",      "Captured an expense or receipt."),
            QuickLogPreset("Packed",     "Updated packing list or trip checklist."))
        "Gaming"      -> listOf(
            QuickLogPreset("Played",     "Played a session and noted progress."),
            QuickLogPreset("Achievement","Unlocked an achievement or milestone."),
            QuickLogPreset("Backlog",    "Updated backlog with a new title."))
        "Tech"        -> listOf(
            QuickLogPreset("Shipped",    "Shipped a feature, fix, or commit."),
            QuickLogPreset("Debugged",   "Tracked down an issue and noted the fix."),
            QuickLogPreset("Learned",    "Captured a new tool, library, or pattern."))
        "Work"        -> listOf(
            QuickLogPreset("Deep work",  "Completed a deep-work session."),
            QuickLogPreset("Meeting",    "Logged outcomes and follow-ups from a meeting."),
            QuickLogPreset("Done",       "Closed out a task or deliverable."))
        "Finance"     -> listOf(
            QuickLogPreset("Paid",       "Paid a bill or recurring expense."),
            QuickLogPreset("Saved",      "Logged a contribution or savings transfer."),
            QuickLogPreset("Reviewed",   "Reviewed budget, statements, or subscriptions."))
        "Garden"      -> listOf(
            QuickLogPreset("Watered",    "Watered beds and noted soil condition."),
            QuickLogPreset("Harvested",  "Harvested produce and logged the haul."),
            QuickLogPreset("Weeded",     "Weeded, pruned, or composted today."))
        else -> listOf(
            QuickLogPreset("Done",       "Completed a check-in and logged the result."),
            QuickLogPreset("Checked",    "Checked status and captured observations."),
            QuickLogPreset("Updated",    "Updated notes and next action."))
    }
