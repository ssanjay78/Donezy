import SwiftUI

// ─── Category definitions ─────────────────────────────────────────────────────

struct CategoryOption: Equatable {
    let label: String
    let emoji: String
    let description: String
    let starterNotes: String
    let accent: Color
}

let hobbyCategories: [CategoryOption] = [
    CategoryOption(label: "General", emoji: "📋", description: "Flexible trackers for anything recurring.",
        starterNotes: "What matters, how often it needs attention, and what a good update looks like.",
        accent: Color(hex: 0x4E5D6C)),
    CategoryOption(label: "Plants", emoji: "🌱", description: "Watering, pruning, fertilizer, sunlight, pests.",
        starterNotes: "Plant species, watering preference, light conditions, fertilizer schedule, and warning signs.",
        accent: Color(hex: 0x2D7A46)),
    CategoryOption(label: "Garden", emoji: "🌻", description: "Outdoor beds, lawn, compost, harvests, seasonal jobs.",
        starterNotes: "Plot/bed, current crops, watering days, fertilizer or compost cadence, and pest watch.",
        accent: Color(hex: 0x6E8B3D)),
    CategoryOption(label: "Fitness", emoji: "💪", description: "Workouts, mobility, recovery, steps, practice streaks.",
        starterNotes: "Routine, target frequency, current baseline, progress marker, and recovery notes.",
        accent: Color(hex: 0xB14E32)),
    CategoryOption(label: "Medicine", emoji: "💊", description: "Medications, dosages, refills, side effects.",
        starterNotes: "Drug name, dosage, schedule (morning/evening), refill date, and side effects to watch for.",
        accent: Color(hex: 0xD81B60)),
    CategoryOption(label: "Health", emoji: "❤️", description: "Doctor visits, sleep, hydration, vitals, symptoms.",
        starterNotes: "What to track (BP, weight, sleep, hydration), check-up cadence, and any doctor's notes.",
        accent: Color(hex: 0xC2185B)),
    CategoryOption(label: "Mindfulness", emoji: "🧘", description: "Meditation, journaling, breathing, gratitude.",
        starterNotes: "Practice length, technique, intentions, and how you felt before/after.",
        accent: Color(hex: 0x7E57C2)),
    CategoryOption(label: "Reading", emoji: "📚", description: "Books, chapters, notes, reading streaks.",
        starterNotes: "Current book, chapter target, themes to capture, and next reading checkpoint.",
        accent: Color(hex: 0x6F4A8E)),
    CategoryOption(label: "Learning", emoji: "🧠", description: "Courses, language, coding, certifications.",
        starterNotes: "Topic, resource, practice plan, review checkpoints, and questions to revisit.",
        accent: Color(hex: 0x4867A5)),
    CategoryOption(label: "Music", emoji: "🎵", description: "Instrument practice, drills, songs, recordings.",
        starterNotes: "Warmups, technique focus, piece/song, tempo target, and recording notes.",
        accent: Color(hex: 0x2E6F95)),
    CategoryOption(label: "Creative", emoji: "🎨", description: "Writing, sketching, photos, side projects.",
        starterNotes: "Project goal, next small action, reference ideas, and review cadence.",
        accent: Color(hex: 0xB05D6D)),
    CategoryOption(label: "Cooking", emoji: "🍳", description: "Recipes, meal prep, baking, fermentation, kitchen wins.",
        starterNotes: "Recipe or technique, ingredients, cooking time, taste notes, and what to change next.",
        accent: Color(hex: 0xE07A1F)),
    CategoryOption(label: "Coffee", emoji: "☕", description: "Beans, grind, recipes, brew ratios, tasting notes.",
        starterNotes: "Bean, roast date, grinder setting, dose, yield, time, and taste notes.",
        accent: Color(hex: 0x8A5A2B)),
    CategoryOption(label: "Pets", emoji: "🐾", description: "Feeding, walks, grooming, vet, training sessions.",
        starterNotes: "Pet name, feeding cadence, walk/play schedule, grooming routine, and vet milestones.",
        accent: Color(hex: 0x8D6E63)),
    CategoryOption(label: "Aquarium", emoji: "🐠", description: "Water tests, feeding, cleaning, livestock health.",
        starterNotes: "Tank size, parameters to test, feeding cadence, cleaning routine, and livestock observations.",
        accent: Color(hex: 0x207A8A)),
    CategoryOption(label: "Travel", emoji: "✈️", description: "Trips, packing, places visited, expense receipts.",
        starterNotes: "Destination, dates, packing list, must-see places, and cost log.",
        accent: Color(hex: 0x0097A7)),
    CategoryOption(label: "Gaming", emoji: "🎮", description: "Game progress, sessions, achievements, backlog.",
        starterNotes: "Title, current chapter/rank, target achievement, session length, and backlog notes.",
        accent: Color(hex: 0x5C6BC0)),
    CategoryOption(label: "Tech", emoji: "💻", description: "Devices, side projects, builds, deployments, learning logs.",
        starterNotes: "Project or device, current task, dependencies, blockers, and next milestone.",
        accent: Color(hex: 0x455A64)),
    CategoryOption(label: "Work", emoji: "💼", description: "Standups, deliverables, deep-work sessions, follow-ups.",
        starterNotes: "Goal, current task, blockers, time spent, and what's queued for tomorrow.",
        accent: Color(hex: 0x37474F)),
    CategoryOption(label: "Finance", emoji: "💰", description: "Bills, budgets, subscriptions, investments, savings goals.",
        starterNotes: "Account/bill name, due date or cadence, target amount, and balance trend.",
        accent: Color(hex: 0x388E3C)),
    CategoryOption(label: "Collecting", emoji: "🎒", description: "Sneakers, keyboards, watches, figures, catalog care.",
        starterNotes: "Item details, condition, purchase date, care routine, and rotation notes.",
        accent: Color(hex: 0x8B5E83)),
    CategoryOption(label: "Maintenance", emoji: "🔧", description: "Home, gear, bike, car, filters, renewals.",
        starterNotes: "Asset, recurring task, parts/tools, last service date, and next service trigger.",
        accent: Color(hex: 0x6B6D3F)),
]

func categoryFor(_ label: String) -> CategoryOption {
    let trimmed = label.trimmingCharacters(in: .whitespacesAndNewlines)
    if let match = hobbyCategories.first(where: { $0.label.caseInsensitiveCompare(trimmed) == .orderedSame }) {
        return match
    }
    let base = hobbyCategories[0]
    return CategoryOption(
        label: trimmed.isEmpty ? "General" : trimmed,
        emoji: "📋",
        description: "Custom tracker category.",
        starterNotes: "What to track, when to check in, and what a useful note should include.",
        accent: base.accent
    )
}

// ─── Reminder options ────────────────────────────────────────────────────────

struct ReminderOption: Identifiable, Equatable {
    var id: Int64 { hours }
    let label: String
    let hours: Int64
    let description: String
}

let reminderOptions: [ReminderOption] = [
    ReminderOption(label: "None", hours: 0, description: "No reminder"),
    ReminderOption(label: "1h", hours: 1, description: "Quick follow-up"),
    ReminderOption(label: "3h", hours: 3, description: "Later today"),
    ReminderOption(label: "6h", hours: 6, description: "Half day"),
    ReminderOption(label: "12h", hours: 12, description: "Tonight"),
    ReminderOption(label: "1d", hours: 24, description: "Tomorrow"),
    ReminderOption(label: "2d", hours: 48, description: "Two days"),
    ReminderOption(label: "3d", hours: 72, description: "Three days"),
    ReminderOption(label: "1w", hours: 168, description: "Weekly"),
    ReminderOption(label: "2w", hours: 336, description: "Bi-weekly"),
    ReminderOption(label: "30d", hours: 720, description: "Monthly"),
]

func reminderLabel(_ hours: Int64) -> String {
    reminderOptions.first(where: { $0.hours == hours })?.description ?? "\(hours)h"
}

// ─── Starter templates ───────────────────────────────────────────────────────

struct HobbyTemplate: Identifiable, Equatable {
    var id: String { title }
    let title: String
    let category: String
    let trackerName: String
    let notes: String
    let reminderHours: Int64
}

let hobbyTemplates: [HobbyTemplate] = [
    HobbyTemplate(title: "Plant care", category: "Plants", trackerName: "Monstera care",
        notes: "Track watering, leaf checks, pruning, fertilizer, and light changes.", reminderHours: 72),
    HobbyTemplate(title: "Daily meditation", category: "Mindfulness", trackerName: "Morning meditation",
        notes: "Capture practice length, technique, intention, and how you felt before/after.", reminderHours: 24),
    HobbyTemplate(title: "Medication", category: "Medicine", trackerName: "Daily meds",
        notes: "Track medications taken, missed doses, refill dates, and side effects.", reminderHours: 24),
    HobbyTemplate(title: "Refill calendar", category: "Medicine", trackerName: "Prescription refills",
        notes: "Each prescription's name, dosage, supply remaining, pharmacy, and next refill date.", reminderHours: 168),
    HobbyTemplate(title: "Sleep journal", category: "Health", trackerName: "Sleep tracker",
        notes: "Bedtime, wake time, sleep quality (1–5), dreams or wake-ups, energy in the morning.", reminderHours: 24),
    HobbyTemplate(title: "Coffee dial-in", category: "Coffee", trackerName: "Espresso recipe",
        notes: "Record bean, dose, yield, grind, shot time, and taste notes.", reminderHours: 0),
    HobbyTemplate(title: "Cooking journal", category: "Cooking", trackerName: "Weeknight dinners",
        notes: "Recipe, ingredients, cooking time, taste notes, and what to change next.", reminderHours: 0),
    HobbyTemplate(title: "Aquarium routine", category: "Aquarium", trackerName: "Tank maintenance",
        notes: "Log water parameters, feeding, filter checks, cleaning, and livestock health.", reminderHours: 168),
    HobbyTemplate(title: "Pet care", category: "Pets", trackerName: "Dog routine",
        notes: "Walks, feeding, training drills, grooming, and vet milestones.", reminderHours: 12),
    HobbyTemplate(title: "Practice streak", category: "Music", trackerName: "Guitar practice",
        notes: "Capture warmups, drills, song sections, tempo, and recordings.", reminderHours: 24),
    HobbyTemplate(title: "Gear rotation", category: "Collecting", trackerName: "Sneaker rotation",
        notes: "Track wears, cleaning, condition, weather, and storage notes.", reminderHours: 336),
    HobbyTemplate(title: "Study plan", category: "Learning", trackerName: "Android learning",
        notes: "Plan lessons, exercises, questions, review dates, and small projects.", reminderHours: 24),
    HobbyTemplate(title: "Reading list", category: "Reading", trackerName: "Currently reading",
        notes: "Current book, chapter target, themes to capture, and weekly reading goal.", reminderHours: 24),
    HobbyTemplate(title: "Side project", category: "Tech", trackerName: "Side project",
        notes: "Goal, current task, blockers, deploy/test cadence, next milestone.", reminderHours: 48),
    HobbyTemplate(title: "Workout split", category: "Fitness", trackerName: "Push/Pull/Legs",
        notes: "Split day, key lifts, weight progression, mobility, recovery score.", reminderHours: 24),
    HobbyTemplate(title: "Bills tracker", category: "Finance", trackerName: "Monthly bills",
        notes: "Bill name, due date, autopay, amount, and renewal notes.", reminderHours: 720),
    HobbyTemplate(title: "Gaming backlog", category: "Gaming", trackerName: "Backlog progress",
        notes: "Title, current chapter/rank, target achievement, and total play hours.", reminderHours: 168),
    HobbyTemplate(title: "Trip planner", category: "Travel", trackerName: "Next trip",
        notes: "Destination, dates, packing list, must-see places, and cost log.", reminderHours: 0),
    HobbyTemplate(title: "Garden bed", category: "Garden", trackerName: "Veggie bed",
        notes: "Bed, planted crops, watering days, fertilizer, and harvest notes.", reminderHours: 24),
    HobbyTemplate(title: "Daily journal", category: "Mindfulness", trackerName: "Daily journal",
        notes: "Three things, mood, gratitude, and one intention for tomorrow.", reminderHours: 24),
    HobbyTemplate(title: "Work standup", category: "Work", trackerName: "Daily standup",
        notes: "Done yesterday, doing today, blockers, and follow-ups owed.", reminderHours: 24),
]

// ─── Quick-log presets ───────────────────────────────────────────────────────

struct QuickLogPreset: Identifiable, Equatable {
    var id: String { label }
    let label: String
    let entry: String
}

func quickLogPresetsFor(_ category: String) -> [QuickLogPreset] {
    switch categoryFor(category).label {
    case "Plants":
        return [QuickLogPreset(label: "Watered", entry: "Watered and checked soil moisture."),
                QuickLogPreset(label: "Fertilized", entry: "Added fertilizer and noted plant response."),
                QuickLogPreset(label: "Leaf check", entry: "Checked leaves for pests, yellowing, and new growth.")]
    case "Fitness":
        return [QuickLogPreset(label: "Workout", entry: "Completed workout and logged effort level."),
                QuickLogPreset(label: "Mobility", entry: "Completed mobility or stretching session."),
                QuickLogPreset(label: "Recovery", entry: "Logged rest, soreness, sleep, or recovery notes.")]
    case "Reading":
        return [QuickLogPreset(label: "Read", entry: "Read a session and captured key takeaways."),
                QuickLogPreset(label: "Quote", entry: "Saved a memorable idea or quote."),
                QuickLogPreset(label: "Review", entry: "Reviewed notes and updated next reading target.")]
    case "Music":
        return [QuickLogPreset(label: "Practice", entry: "Completed practice session with focus notes."),
                QuickLogPreset(label: "Tempo", entry: "Worked on tempo, timing, or difficult section."),
                QuickLogPreset(label: "Recorded", entry: "Recorded a take and noted improvements.")]
    case "Coffee":
        return [QuickLogPreset(label: "Brewed", entry: "Logged brew recipe, extraction, and taste."),
                QuickLogPreset(label: "Adjusted", entry: "Adjusted grind, dose, yield, or brew time."),
                QuickLogPreset(label: "Tasted", entry: "Captured tasting notes and next change.")]
    case "Aquarium":
        return [QuickLogPreset(label: "Fed", entry: "Fed livestock and checked behavior."),
                QuickLogPreset(label: "Tested", entry: "Tested water parameters and logged results."),
                QuickLogPreset(label: "Cleaned", entry: "Completed cleaning, water change, or filter check.")]
    case "Collecting":
        return [QuickLogPreset(label: "Used", entry: "Logged use, wear, condition, and storage notes."),
                QuickLogPreset(label: "Cleaned", entry: "Cleaned item and checked condition."),
                QuickLogPreset(label: "Cataloged", entry: "Updated catalog details or purchase notes.")]
    case "Maintenance":
        return [QuickLogPreset(label: "Serviced", entry: "Completed maintenance task and noted parts/tools."),
                QuickLogPreset(label: "Inspected", entry: "Inspected condition and captured follow-up items."),
                QuickLogPreset(label: "Renewed", entry: "Updated renewal, warranty, or replacement details.")]
    case "Creative":
        return [QuickLogPreset(label: "Drafted", entry: "Moved the project forward with a focused work session."),
                QuickLogPreset(label: "Reviewed", entry: "Reviewed output and captured next improvements."),
                QuickLogPreset(label: "Published", entry: "Shared, exported, or archived a finished piece.")]
    case "Learning":
        return [QuickLogPreset(label: "Studied", entry: "Completed a learning session and captured notes."),
                QuickLogPreset(label: "Practiced", entry: "Finished exercises or applied the concept."),
                QuickLogPreset(label: "Reviewed", entry: "Reviewed mistakes, questions, and next lesson.")]
    case "Medicine":
        return [QuickLogPreset(label: "Took dose", entry: "Took today's dose on schedule."),
                QuickLogPreset(label: "Skipped", entry: "Skipped a dose — noted reason."),
                QuickLogPreset(label: "Side effect", entry: "Logged a side effect or interaction."),
                QuickLogPreset(label: "Refilled", entry: "Refilled prescription, captured next refill date.")]
    case "Health":
        return [QuickLogPreset(label: "Vitals", entry: "Logged vitals (BP, weight, sleep, hydration)."),
                QuickLogPreset(label: "Symptom", entry: "Noted a symptom or how I'm feeling today."),
                QuickLogPreset(label: "Doctor", entry: "Doctor visit, results, or follow-up captured.")]
    case "Mindfulness":
        return [QuickLogPreset(label: "Meditated", entry: "Completed a meditation or breathing session."),
                QuickLogPreset(label: "Journaled", entry: "Wrote a reflection or gratitude entry."),
                QuickLogPreset(label: "Mood check", entry: "Captured mood and energy level.")]
    case "Cooking":
        return [QuickLogPreset(label: "Cooked", entry: "Cooked a meal and noted recipe and outcome."),
                QuickLogPreset(label: "Prepped", entry: "Did meal prep and stocked containers."),
                QuickLogPreset(label: "Tasted", entry: "Captured tasting notes and what to tweak.")]
    case "Pets":
        return [QuickLogPreset(label: "Fed", entry: "Fed pet on schedule."),
                QuickLogPreset(label: "Walked", entry: "Took pet for a walk or play session."),
                QuickLogPreset(label: "Vet", entry: "Vet visit, medication, or grooming logged.")]
    case "Travel":
        return [QuickLogPreset(label: "Visited", entry: "Logged a place visited and quick highlights."),
                QuickLogPreset(label: "Spent", entry: "Captured an expense or receipt."),
                QuickLogPreset(label: "Packed", entry: "Updated packing list or trip checklist.")]
    case "Gaming":
        return [QuickLogPreset(label: "Played", entry: "Played a session and noted progress."),
                QuickLogPreset(label: "Achievement", entry: "Unlocked an achievement or milestone."),
                QuickLogPreset(label: "Backlog", entry: "Updated backlog with a new title.")]
    case "Tech":
        return [QuickLogPreset(label: "Shipped", entry: "Shipped a feature, fix, or commit."),
                QuickLogPreset(label: "Debugged", entry: "Tracked down an issue and noted the fix."),
                QuickLogPreset(label: "Learned", entry: "Captured a new tool, library, or pattern.")]
    case "Work":
        return [QuickLogPreset(label: "Deep work", entry: "Completed a deep-work session."),
                QuickLogPreset(label: "Meeting", entry: "Logged outcomes and follow-ups from a meeting."),
                QuickLogPreset(label: "Done", entry: "Closed out a task or deliverable.")]
    case "Finance":
        return [QuickLogPreset(label: "Paid", entry: "Paid a bill or recurring expense."),
                QuickLogPreset(label: "Saved", entry: "Logged a contribution or savings transfer."),
                QuickLogPreset(label: "Reviewed", entry: "Reviewed budget, statements, or subscriptions.")]
    case "Garden":
        return [QuickLogPreset(label: "Watered", entry: "Watered beds and noted soil condition."),
                QuickLogPreset(label: "Harvested", entry: "Harvested produce and logged the haul."),
                QuickLogPreset(label: "Weeded", entry: "Weeded, pruned, or composted today.")]
    default:
        return [QuickLogPreset(label: "Done", entry: "Completed a check-in and logged the result."),
                QuickLogPreset(label: "Checked", entry: "Checked status and captured observations."),
                QuickLogPreset(label: "Updated", entry: "Updated notes and next action.")]
    }
}
