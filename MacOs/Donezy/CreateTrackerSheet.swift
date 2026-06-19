import SwiftUI

/// Result of the create/edit sheet's reminder section.
struct ReminderConfig: Equatable {
    let reminderAt: Int64?
    let recurrence: Recurrence
}

struct TrackerSaveResult: Equatable {
    let name: String
    let category: String
    let notes: String
    let reminder: ReminderConfig
    let weeklyGoal: Int
}

/// Bottom-sheet form for creating or editing a tracker. Port of `CreateTrackerSheet`.
struct CreateTrackerSheet: View {
    var initial: Hobby?               // non-nil = edit mode
    var prefill: HobbyTemplate?       // populates fields when no `initial`
    let onSave: (TrackerSaveResult) -> Void
    let onDismiss: () -> Void

    @Environment(\.theme) private var theme

    @State private var name: String
    @State private var category: CategoryOption
    @State private var notes: String
    @State private var weeklyGoal: Int
    @State private var reminderAt: Int64?
    @State private var recurrence: Recurrence

    init(initial: Hobby? = nil, prefill: HobbyTemplate? = nil,
         onSave: @escaping (TrackerSaveResult) -> Void, onDismiss: @escaping () -> Void) {
        self.initial = initial
        self.prefill = prefill
        self.onSave = onSave
        self.onDismiss = onDismiss

        _name = State(initialValue: initial?.name ?? prefill?.trackerName ?? "")
        _category = State(initialValue: categoryFor(initial?.category ?? prefill?.category ?? "General"))
        _notes = State(initialValue: initial?.notes ?? prefill?.notes ?? "")
        _weeklyGoal = State(initialValue: initial?.weeklyGoal ?? 0)

        let initialReminder: Int64? = {
            if let r = initial?.nextReminderAt { return r }
            if let h = prefill?.reminderHours, h > 0 { return nowMillis() + h * HOUR_MS }
            return nil
        }()
        _reminderAt = State(initialValue: initialReminder)

        let initialRecurrence: Recurrence = {
            if let r = initial?.recurrence { return r }
            if let h = prefill?.reminderHours, h > 0 { return .hourly(hours: h) }
            return .none
        }()
        _recurrence = State(initialValue: initialRecurrence)
    }

    private var isEdit: Bool { initial != nil }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    TextField("Tracker name", text: $name)
                        .textFieldStyle(.roundedBorder)
                        .autocorrectionDisabled()

                    CategoryDropdown(selected: $category)

                    if !category.description.isEmpty {
                        Text(category.description)
                            .font(.system(size: 12))
                            .foregroundColor(theme.onSurfaceVariant)
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        Text("Plan, baseline, or care notes")
                            .font(.system(size: 12)).foregroundColor(theme.onSurfaceVariant)
                        TextEditor(text: $notes)
                            .frame(minHeight: 90)
                            .padding(6)
                            .autocorrectionDisabled()
                            .overlay(RoundedRectangle(cornerRadius: 8).strokeBorder(theme.onSurfaceVariant.opacity(0.4)))
                            .overlay(alignment: .topLeading) {
                                if notes.isEmpty {
                                    Text(category.starterNotes)
                                        .font(.system(size: 12))
                                        .foregroundColor(theme.onSurfaceVariant.opacity(0.6))
                                        .padding(.horizontal, 11).padding(.vertical, 14)
                                        .allowsHitTesting(false)
                                }
                            }
                    }

                    ReminderSection(reminderAt: $reminderAt, recurrence: $recurrence)

                    WeeklyGoalSection(value: $weeklyGoal)

                    Button {
                        onSave(TrackerSaveResult(
                            name: name, category: category.label, notes: notes,
                            reminder: ReminderConfig(reminderAt: reminderAt, recurrence: recurrence),
                            weeklyGoal: weeklyGoal))
                    } label: {
                        Text(isEdit ? "Save changes" : "Add tracker")
                            .font(.system(size: 14, weight: .semibold))
                            .frame(maxWidth: .infinity).frame(height: 52)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
                .padding(20)
            }
            .navigationTitle(isEdit ? "Edit tracker" : "New tracker")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { onDismiss() } label: { Image(systemName: "xmark") }
                }
            }
        }
    }
}

// ─── Weekly goal ──────────────────────────────────────────────────────────────

private struct WeeklyGoalSection: View {
    @Binding var value: Int
    @Environment(\.theme) private var theme
    private let options = [0, 1, 2, 3, 5, 7, 10]
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Weekly goal").font(.system(size: 14, weight: .semibold))
            Text("Earn the 🎯 badge each week you hit this many logs.")
                .font(.system(size: 12)).foregroundColor(theme.onSurfaceVariant)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(options, id: \.self) { n in
                        FilterChip(text: n == 0 ? "Off" : "\(n) / wk", selected: value == n) { value = n }
                    }
                }
            }
        }
    }
}

// ─── Reminder Section ─────────────────────────────────────────────────────────

private struct ReminderSection: View {
    @Binding var reminderAt: Int64?
    @Binding var recurrence: Recurrence
    @Environment(\.theme) private var theme

    @State private var showDatePicker = false
    @State private var showTimePicker = false

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Reminder").font(.system(size: 14, weight: .semibold))

            // Quick relative chips
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(reminderOptions) { option in
                        let selectedHours: Int64? = {
                            if reminderAt != nil, case .hourly(let h) = recurrence { return h }
                            return nil
                        }()
                        let matchesNone = option.hours == 0 && reminderAt == nil
                        let matches = matchesNone || option.hours == selectedHours
                        DualFilterChip(label: option.label, detail: option.description, selected: matches) {
                            if option.hours == 0 {
                                reminderAt = nil; recurrence = .none
                            } else {
                                reminderAt = nowMillis() + option.hours * HOUR_MS; recurrence = .none
                            }
                        }
                    }
                }
            }

            // Absolute date / time
            HStack(spacing: 8) {
                Button {
                    showDatePicker = true
                } label: {
                    Label(reminderAt.map { formatDateShort($0) } ?? "Pick date", systemImage: "calendar")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)

                Button {
                    if reminderAt != nil { showTimePicker = true } else { showDatePicker = true }
                } label: {
                    Label(timeLabel, systemImage: "clock")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
            }

            if reminderAt != nil {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Repeat").font(.system(size: 14, weight: .medium))
                        .foregroundColor(theme.onSurfaceVariant)
                    RecurrencePicker(current: $recurrence, anchor: reminderAt ?? nowMillis())
                }
            }
        }
        .sheet(isPresented: $showDatePicker) {
            DateTimePickerSheet(reminderAt: $reminderAt, mode: .date) { showDatePicker = false }
        }
        .sheet(isPresented: $showTimePicker) {
            DateTimePickerSheet(reminderAt: $reminderAt, mode: .time) { showTimePicker = false }
        }
    }

    private var timeLabel: String {
        guard let reminderAt else { return "Pick time" }
        let date = Date(timeIntervalSince1970: Double(reminderAt) / 1000.0)
        let comps = Calendar.current.dateComponents([.hour, .minute], from: date)
        return String(format: "%02d:%02d", comps.hour ?? 0, comps.minute ?? 0)
    }
}

/// Date or time picker presented as a sheet; writes back the merged instant.
///
/// Mirrors the Android `ReminderSection` dialogs:
///  • date mode  → a full month calendar (Material3 `DatePicker` equivalent). When no
///    reminder is set yet, the time defaults to 9:00 AM, exactly like Android.
///  • time mode  → an always-visible wheel clock, the closest iOS-native match to
///    Android's `TimePicker` dial; the existing date is preserved.
private struct DateTimePickerSheet: View {
    @Binding var reminderAt: Int64?
    enum Mode { case date, time }
    let mode: Mode
    let onClose: () -> Void

    @Environment(\.theme) private var theme
    @State private var selection: Date

    init(reminderAt: Binding<Int64?>, mode: Mode, onClose: @escaping () -> Void) {
        _reminderAt = reminderAt
        self.mode = mode
        self.onClose = onClose

        let calendar = Calendar.current
        if let existing = reminderAt.wrappedValue {
            // Editing an existing reminder — start from it.
            _selection = State(initialValue: Date(timeIntervalSince1970: Double(existing) / 1000.0))
        } else {
            // No reminder yet: default to tomorrow at 9:00 AM (matches Android).
            let tomorrow = calendar.date(byAdding: .day, value: 1, to: Date()) ?? Date()
            let nineAM = calendar.date(bySettingHour: 9, minute: 0, second: 0, of: tomorrow) ?? tomorrow
            _selection = State(initialValue: nineAM)
        }
    }

    var body: some View {
        NavigationStack {
            VStack {
                if mode == .date {
                    DatePicker("", selection: $selection, displayedComponents: .date)
                        .datePickerStyle(.graphical)
                        .labelsHidden()
                        .padding(.horizontal)
                    Spacer()
                } else {
                    Spacer()
                    DatePicker("", selection: $selection, displayedComponents: .hourAndMinute)
                        .datePickerStyle(.wheel)
                        .labelsHidden()
                    Spacer()
                }
            }
            .padding(.vertical)
            .navigationTitle(mode == .date ? "Pick date" : "Pick time")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel", action: onClose) }
                ToolbarItem(placement: .confirmationAction) {
                    Button("OK") {
                        reminderAt = Int64(selection.timeIntervalSince1970 * 1000.0)
                        onClose()
                    }
                }
            }
        }
        .presentationDetents(mode == .date ? [.large] : [.medium])
    }
}

// ─── Recurrence picker ────────────────────────────────────────────────────────

private struct RecurrencePicker: View {
    @Binding var current: Recurrence
    let anchor: Int64
    @Environment(\.theme) private var theme

    var body: some View {
        let anchorDate = Date(timeIntervalSince1970: Double(anchor) / 1000.0)
        let cal = Calendar.current
        // Mon=0 … Sun=6
        let anchorWeekdayBit = ((cal.component(.weekday, from: anchorDate) + 5) % 7)
        let anchorDay = cal.component(.day, from: anchorDate)

        let choices: [(String, Recurrence)] = [
            ("None", .none),
            ("Hourly", { if case .hourly = current { return current } else { return .hourly(hours: 24) } }()),
            ("Daily", .daily),
            ("Weekly", { if case .weekly = current { return current } else { return .weekly(dayMask: 1 << anchorWeekdayBit) } }()),
            ("Monthly", { if case .monthly = current { return current } else { return .monthly(dayOfMonth: anchorDay) } }()),
        ]

        VStack(alignment: .leading, spacing: 8) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(choices, id: \.0) { label, rule in
                        FilterChip(text: label, selected: sameKind(current, rule)) { current = rule }
                    }
                }
            }

            switch current {
            case .hourly(let hours):
                HourlyEditor(hours: hours) { current = .hourly(hours: $0) }
            case .weekly(let mask):
                WeeklyEditor(dayMask: mask) { current = .weekly(dayMask: $0) }
            case .monthly(let day):
                Text("Day \(day) of each month")
                    .font(.system(size: 12)).foregroundColor(theme.onSurfaceVariant)
            default:
                EmptyView()
            }
        }
    }

    private func sameKind(_ a: Recurrence, _ b: Recurrence) -> Bool {
        switch (a, b) {
        case (.none, .none), (.hourly, .hourly), (.daily, .daily),
             (.weekly, .weekly), (.monthly, .monthly):
            return true
        default:
            return false
        }
    }
}

private struct HourlyEditor: View {
    let hours: Int64
    let onChange: (Int64) -> Void
    private let presets: [Int64] = [1, 3, 6, 12, 24, 48, 72, 168, 336, 720]
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(presets, id: \.self) { p in
                    FilterChip(text: reminderOptions.first(where: { $0.hours == p })?.label ?? "\(p)h",
                               selected: hours == p) { onChange(p) }
                }
            }
        }
    }
}

private struct WeeklyEditor: View {
    let dayMask: Int
    let onChange: (Int) -> Void
    @Environment(\.theme) private var theme
    private let labels = ["M", "T", "W", "T", "F", "S", "S"]
    var body: some View {
        HStack(spacing: 6) {
            ForEach(0..<7, id: \.self) { i in
                let selected = dayMask & (1 << i) != 0
                Text(labels[i])
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(selected ? theme.onPrimary : theme.onSurface)
                    .frame(width: 36, height: 36)
                    .background(selected ? theme.primary : theme.surfaceVariant, in: Circle())
                    .onTapGesture { onChange(max(0, dayMask ^ (1 << i))) }
            }
        }
    }
}

// ─── Category Dropdown ────────────────────────────────────────────────────────

struct CategoryDropdown: View {
    @Binding var selected: CategoryOption
    @Environment(\.theme) private var theme
    var body: some View {
        Menu {
            ForEach(hobbyCategories, id: \.label) { option in
                Button {
                    selected = option
                    UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
                } label: {
                    if selected.label == option.label {
                        Label("\(option.emoji)  \(option.label)", systemImage: "checkmark")
                    } else {
                        Text("\(option.emoji)  \(option.label)")
                    }
                }
            }
        } label: {
            HStack {
                Text("\(selected.emoji)  \(selected.label)").foregroundColor(theme.onSurface)
                Spacer()
                Image(systemName: "chevron.up.chevron.down").font(.system(size: 12)).foregroundColor(theme.onSurfaceVariant)
            }
            .padding(12)
            .background(Color.clear)
            .overlay(RoundedRectangle(cornerRadius: 8).strokeBorder(theme.onSurfaceVariant.opacity(0.4)))
            .contentShape(Rectangle())
        }
        .simultaneousGesture(TapGesture().onEnded {
            UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
        })
    }
}
