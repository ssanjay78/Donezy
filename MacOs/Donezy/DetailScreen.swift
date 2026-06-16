import SwiftUI
import PhotosUI

struct DetailScreen: View {
    @ObservedObject var viewModel: HobbyViewModel
    @Environment(\.theme) private var theme

    @State private var logEntry = ""
    @State private var logRating: Int?
    @State private var pendingPhoto: String?
    @State private var showEditSheet = false
    @State private var showDeleteDialog = false
    @State private var photoItem: PhotosPickerItem?
    @State private var showShareSheet = false
    @State private var shareURL: URL?

    var body: some View {
        VStack(spacing: 0) {
            topBar
            if let detail = viewModel.detail {
                content(detail)
            } else {
                Spacer()
                ProgressView()
                Spacer()
            }
        }
        .sheet(isPresented: $showEditSheet) {
            if let h = viewModel.detail?.hobby {
                CreateTrackerSheet(initial: h,
                    onSave: { result in
                        viewModel.updateHobby(id: h.id, name: result.name, category: result.category,
                                              notes: result.notes, weeklyGoal: result.weeklyGoal)
                        if let at = result.reminder.reminderAt {
                            viewModel.setReminderAt(h, reminderAt: at, recurrence: result.reminder.recurrence)
                        } else {
                            viewModel.clearReminder(h)
                        }
                        showEditSheet = false
                    },
                    onDismiss: { showEditSheet = false })
            }
        }
        .sheet(isPresented: $showShareSheet) {
            if let shareURL { ShareSheet(items: [shareURL]) }
        }
        .alert("Delete tracker?", isPresented: $showDeleteDialog) {
            Button("Cancel", role: .cancel) {}
            Button("Delete", role: .destructive) {
                if let h = viewModel.detail?.hobby { viewModel.deleteHobby(id: h.id, name: h.name) }
            }
        } message: {
            if let d = viewModel.detail {
                Text("This will permanently delete \"\(d.hobby.name)\" and all \(d.logs.count) log entries. This cannot be undone.")
            }
        }
        .onChange(of: photoItem) { _, item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self) {
                    pendingPhoto = viewModel.importPhoto(data: data)
                }
            }
        }
    }

    // ── Top bar ───────────────────────────────────────────────────────────────

    private var topBar: some View {
        HStack(spacing: 4) {
            Button { viewModel.goHome() } label: { Image(systemName: "chevron.left").font(.system(size: 18)) }
            Text(viewModel.detail?.hobby.name ?? "Tracker")
                .font(.system(size: 18, weight: .semibold)).lineLimit(1)
            Spacer()
            Button {
                shareURL = viewModel.exportLogsCSV()
                if shareURL != nil { showShareSheet = true }
            } label: { Image(systemName: "square.and.arrow.up").font(.system(size: 17)) }
            Button { showEditSheet = true } label: { Image(systemName: "pencil").font(.system(size: 17)) }
            Menu {
                if let h = viewModel.detail?.hobby {
                    Button { viewModel.togglePin(h) } label: {
                        Label(h.isPinned ? "Unpin" : "Pin to top", systemImage: h.isPinned ? "pin.slash" : "pin")
                    }
                    Button { viewModel.archiveHobby(id: h.id, name: h.name) } label: {
                        Label("Archive", systemImage: "archivebox")
                    }
                    Divider()
                    Button(role: .destructive) { showDeleteDialog = true } label: {
                        Label("Delete", systemImage: "trash")
                    }
                }
            } label: { Image(systemName: "ellipsis").font(.system(size: 17)) }
        }
        .foregroundColor(theme.onSurface)
        .padding(.horizontal, 12).padding(.vertical, 10)
    }

    // ── Content ───────────────────────────────────────────────────────────────

    private func content(_ detail: HobbyDetail) -> some View {
        let hobby = detail.hobby
        let category = categoryFor(hobby.category)
        let logs = detail.logs
        let streak = StreakMath.computeStreak(logs)
        let daysSince = StreakMath.daysSinceLastLog(logs)
        let quickLogs = quickLogPresetsFor(hobby.category)
        let snapshot = AchievementSnapshot.from(hobby: hobby, logs: logs)
        let earned = Achievements.earned(snapshot)

        return ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                DetailHero(hobby: hobby, category: category, logCount: logs.count, streak: streak, daysSince: daysSince)

                if !logs.isEmpty { InsightsCard(logs: logs) }

                if (!logs.isEmpty || hobby.weeklyGoal > 0) && (hobby.weeklyGoal > 0 || !earned.isEmpty) {
                    AchievementCard(snapshot: snapshot, earned: earned)
                }

                // Quick log chips
                VStack(alignment: .leading, spacing: 12) {
                    SectionHeader(title: "Quick log", subtitle: "Tap to save instantly")
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(quickLogs) { preset in
                                AssistChip(text: preset.label) { viewModel.addLog(hobbyId: hobby.id, entry: preset.entry, rating: nil) }
                            }
                        }
                    }
                }
                .padding(16)
                .background(theme.surfaceVariant.opacity(0.5), in: RoundedRectangle(cornerRadius: 16))

                manualLogEntry(hobby)

                ReminderSummaryCard(hobby: hobby, onEdit: { showEditSheet = true }, onClear: { viewModel.clearReminder(hobby) })

                SectionHeader(title: "Log history", subtitle: "\(logs.count) entries")

                if logs.isEmpty { EmptyLogState() }

                ForEach(logs) { log in
                    LogCard(log: log) { viewModel.deleteLog(hobbyId: hobby.id, logId: log.id) }
                        .swipeActionDelete { viewModel.deleteLog(hobbyId: hobby.id, logId: log.id) }
                }
            }
            .padding(.horizontal, 16).padding(.top, 4).padding(.bottom, 40)
        }
    }

    private func manualLogEntry(_ hobby: Hobby) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(title: "Write a log entry", subtitle: "Detailed note")
            TextEditor(text: $logEntry)
                .frame(minHeight: 80).padding(6)
                .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(theme.onSurfaceVariant.opacity(0.4)))
                .overlay(alignment: .topLeading) {
                    if logEntry.isEmpty {
                        Text("What happened?").font(.system(size: 14))
                            .foregroundColor(theme.onSurfaceVariant.opacity(0.6))
                            .padding(.horizontal, 11).padding(.vertical, 14).allowsHitTesting(false)
                    }
                }

            HStack(spacing: 8) {
                PhotosPicker(selection: $photoItem, matching: .images) {
                    Label(pendingPhoto == nil ? "Add photo" : "Replace photo", systemImage: "camera")
                        .font(.system(size: 14))
                }
                .buttonStyle(.bordered)
                if pendingPhoto != nil {
                    Button { pendingPhoto = nil; photoItem = nil } label: { Image(systemName: "xmark") }
                    Spacer()
                    if let uri = pendingPhoto, let url = URL(string: uri) {
                        LogImage(url: url).frame(width: 48, height: 48).clipShape(RoundedRectangle(cornerRadius: 8))
                    }
                }
            }

            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Rate this session").font(.system(size: 12)).foregroundColor(theme.onSurfaceVariant)
                    StarRatingRow(rating: logRating) { logRating = $0 }
                }
                Spacer()
                Button {
                    viewModel.addLog(hobbyId: hobby.id, entry: logEntry, rating: logRating, photoUri: pendingPhoto)
                    logEntry = ""; logRating = nil; pendingPhoto = nil; photoItem = nil
                } label: {
                    Label("Save", systemImage: "checkmark").font(.system(size: 14))
                }
                .buttonStyle(.borderedProminent)
                .disabled(logEntry.trimmingCharacters(in: .whitespaces).isEmpty && pendingPhoto == nil)
            }
        }
        .padding(16)
        .background(theme.surface, in: RoundedRectangle(cornerRadius: 16))
        .shadow(color: .black.opacity(0.10), radius: 3, y: 1)
    }
}

// ─── Detail hero ──────────────────────────────────────────────────────────────

struct DetailHero: View {
    let hobby: Hobby
    let category: CategoryOption
    let logCount: Int
    let streak: Int
    let daysSince: Int64?
    @Environment(\.theme) private var theme

    var body: some View {
        let status = reminderStatusInfo(hobby.nextReminderAt)
        let onHero = Color.white

        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 14) {
                CategoryAvatar(category: category, size: 60)
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 8) {
                        Text("\(category.emoji)  \(category.label)")
                            .font(.system(size: 14)).foregroundColor(onHero.opacity(0.85))
                        if hobby.isPinned {
                            Image(systemName: "pin.fill").font(.system(size: 12)).foregroundColor(onHero.opacity(0.8))
                        }
                    }
                    Text(hobby.name).font(.system(size: 24, weight: .bold)).foregroundColor(onHero)
                }
            }
            if !hobby.notes.isEmpty {
                Text(hobby.notes).font(.system(size: 14)).foregroundColor(onHero.opacity(0.85))
                    .lineLimit(3)
            }
            HStack(spacing: 8) {
                heroMetric("Logs", "\(logCount)")
                heroMetric("Streak", streak > 0 ? "🔥 \(streak)" : "—")
                heroMetric("Last log", lastLogLabel)
            }
            HStack(spacing: 8) {
                Image(systemName: "bell").font(.system(size: 14)).foregroundColor(onHero.opacity(0.85))
                Text(status.detail).font(.system(size: 12)).foregroundColor(onHero.opacity(0.9))
                if hobby.recurrence.isRecurring {
                    Text("· ↺ recurring").font(.system(size: 12)).foregroundColor(onHero.opacity(0.75))
                }
            }
            Text("Created \(formatDateShort(hobby.createdAt))")
                .font(.system(size: 11)).foregroundColor(onHero.opacity(0.7))
        }
        .padding(20).frame(maxWidth: .infinity, alignment: .leading)
        .background(
            LinearGradient(colors: [category.accent, category.accent.opacity(0.6),
                                    theme.secondaryContainer.opacity(0.8)],
                           startPoint: .topLeading, endPoint: .bottomTrailing),
            in: RoundedRectangle(cornerRadius: 24))
    }

    private var lastLogLabel: String {
        switch daysSince {
        case .none: return "Never"
        case .some(0): return "Today"
        case .some(1): return "Yesterday"
        case .some(let d): return "\(d)d ago"
        }
    }

    private func heroMetric(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value).font(.system(size: 16, weight: .bold)).foregroundColor(.white)
            Text(label).font(.system(size: 11)).foregroundColor(.white.opacity(0.85))
        }
        .padding(10).frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white.opacity(0.18), in: RoundedRectangle(cornerRadius: 12))
    }
}

// ─── Insights chart ───────────────────────────────────────────────────────────

struct InsightsCard: View {
    let logs: [HobbyLog]
    @Environment(\.theme) private var theme

    var body: some View {
        let cal = Calendar.current
        let today = cal.startOfDay(for: Date())
        let windowDays = 30
        let days: [Date] = (0..<windowDays).map { cal.date(byAdding: .day, value: -(windowDays - 1 - $0), to: today)! }
        let countsByDay = Dictionary(grouping: logs) {
            cal.startOfDay(for: Date(timeIntervalSince1970: Double($0.createdAt) / 1000.0))
        }.mapValues { $0.count }
        let total = countsByDay.values.reduce(0, +)
        let maxCount = max(countsByDay.values.max() ?? 0, 1)

        VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack(alignment: .leading) {
                    Text("Insights").font(.system(size: 16, weight: .semibold))
                    Text("Scroll to see earlier days").font(.system(size: 11)).foregroundColor(theme.onSurfaceVariant)
                }
                Spacer()
                VStack(alignment: .trailing) {
                    Text("\(total)").font(.system(size: 22, weight: .bold)).foregroundColor(theme.primary)
                    Text("entries").font(.system(size: 11)).foregroundColor(theme.onSurfaceVariant)
                }
            }
            .padding(.horizontal, 16)

            ScrollViewReader { proxy in
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(Array(days.enumerated()), id: \.offset) { idx, date in
                            DayCandle(date: date, count: countsByDay[date] ?? 0, maxCount: maxCount,
                                      isToday: date == today).id(idx)
                        }
                    }
                    .padding(.horizontal, 16)
                }
                .onAppear { proxy.scrollTo(days.count - 1, anchor: .trailing) }
            }
        }
        .padding(.vertical, 16)
        .background(theme.surface, in: RoundedRectangle(cornerRadius: 16))
        .shadow(color: .black.opacity(0.10), radius: 3, y: 1)
    }
}

private struct DayCandle: View {
    let date: Date
    let count: Int
    let maxCount: Int
    let isToday: Bool
    @Environment(\.theme) private var theme

    var body: some View {
        let barTrackHeight: CGFloat = 96
        let fill = count == 0 ? 0 : min(CGFloat(count) / CGFloat(maxCount), 1)
        let weekday = dateString("EEE")
        let day = dateString("d MMM")

        VStack(spacing: 4) {
            Text(weekday).font(.system(size: 11, weight: isToday ? .bold : .medium))
                .foregroundColor(isToday ? theme.primary : theme.onSurfaceVariant)
            Text(day).font(.system(size: 11, weight: isToday ? .bold : .regular)).lineLimit(1)
                .foregroundColor(isToday ? theme.primary : theme.onSurfaceVariant)
            ZStack(alignment: .bottom) {
                RoundedRectangle(cornerRadius: 6).fill(theme.surfaceVariant.opacity(0.5))
                if count > 0 {
                    RoundedRectangle(cornerRadius: 6)
                        .fill(isToday ? theme.primary : theme.tertiary)
                        .frame(height: barTrackHeight * fill)
                }
            }
            .frame(width: 20, height: barTrackHeight)
            Text(count == 0 ? "—" : "\(count)")
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(count == 0 ? theme.onSurfaceVariant : (isToday ? theme.primary : theme.onSurface))
        }
        .frame(width: 40)
    }

    private func dateString(_ format: String) -> String {
        let f = DateFormatter(); f.locale = .current; f.dateFormat = format
        return f.string(from: date)
    }
}

// ─── Achievement card ─────────────────────────────────────────────────────────

struct AchievementCard: View {
    let snapshot: AchievementSnapshot
    let earned: [Achievement]
    @Environment(\.theme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(title: "Achievements", subtitle: "\(earned.count) earned")

            if snapshot.weeklyGoal > 0 {
                let progress = min(Double(snapshot.logsThisWeek) / Double(snapshot.weeklyGoal), 1)
                let hit = snapshot.logsThisWeek >= snapshot.weeklyGoal
                VStack(spacing: 6) {
                    HStack {
                        Text("This week").font(.system(size: 14, weight: .medium))
                        Spacer()
                        Text("\(snapshot.logsThisWeek) / \(snapshot.weeklyGoal)")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(hit ? theme.primary : theme.onSurface)
                    }
                    ProgressView(value: progress)
                        .tint(hit ? theme.primary : theme.tertiary)
                }
            }

            if !earned.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(earned) { a in AchievementChip(achievement: a) }
                    }
                }
            } else if snapshot.weeklyGoal == 0 {
                Text("Save a log to start unlocking achievements.")
                    .font(.system(size: 12)).foregroundColor(theme.onSurfaceVariant)
            }
        }
        .padding(16)
        .background(theme.surface, in: RoundedRectangle(cornerRadius: 16))
        .shadow(color: .black.opacity(0.10), radius: 3, y: 1)
    }
}

private struct AchievementChip: View {
    let achievement: Achievement
    @Environment(\.theme) private var theme
    var body: some View {
        HStack(spacing: 6) {
            Text(achievement.emoji)
            Text(achievement.title).font(.system(size: 12, weight: .semibold)).foregroundColor(theme.onSecondaryContainer)
        }
        .padding(.horizontal, 10).padding(.vertical, 6)
        .background(theme.secondaryContainer, in: RoundedRectangle(cornerRadius: 10))
    }
}

// ─── Reminder summary card ────────────────────────────────────────────────────

struct ReminderSummaryCard: View {
    let hobby: Hobby
    let onEdit: () -> Void
    let onClear: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        let status = reminderStatusInfo(hobby.nextReminderAt)
        let rule: String = {
            switch hobby.recurrence {
            case .none: return "One-shot"
            case .hourly(let h): return "Repeats every \(reminderLabel(h))"
            case .daily: return "Repeats daily"
            case .weekly(let mask): return "Repeats weekly · \(weeklyMaskLabel(mask))"
            case .monthly(let day): return "Repeats monthly on day \(day)"
            }
        }()

        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(title: "Reminder", subtitle: status.label)
            Text(hobby.nextReminderAt.map { formatDate($0) } ?? "No reminder set")
                .font(.system(size: 14))
            if hobby.nextReminderAt != nil {
                Text(rule).font(.system(size: 12)).foregroundColor(theme.onSurfaceVariant)
            }
            HStack(spacing: 8) {
                Button(action: onEdit) {
                    Label(hobby.nextReminderAt == nil ? "Set reminder" : "Edit", systemImage: "bell")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                if hobby.nextReminderAt != nil {
                    Button(action: onClear) { Image(systemName: "bell.slash") }
                        .buttonStyle(.bordered)
                }
            }
        }
        .padding(16)
        .background(theme.surface, in: RoundedRectangle(cornerRadius: 16))
        .shadow(color: .black.opacity(0.10), radius: 3, y: 1)
    }
}

// ─── Log card ─────────────────────────────────────────────────────────────────

struct LogCard: View {
    let log: HobbyLog
    let onDelete: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(formatDate(log.createdAt)).font(.system(size: 14, weight: .medium)).foregroundColor(theme.primary)
                Spacer()
                HStack {
                    if let rating = log.rating { StarDisplay(rating: rating) }
                    Button(action: onDelete) {
                        Image(systemName: "trash").font(.system(size: 16))
                            .foregroundColor(theme.onSurfaceVariant.opacity(0.6))
                    }
                    .buttonStyle(.plain)
                }
            }
            if !log.entry.isEmpty {
                Text(log.entry).font(.system(size: 14)).foregroundColor(theme.onSurface)
            }
            if let uri = log.photoUri, let url = URL(string: uri) {
                LogImage(url: url)
                    .frame(maxWidth: .infinity).frame(height: 180)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            }
        }
        .padding(14).frame(maxWidth: .infinity, alignment: .leading)
        .background(theme.surface, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(theme.onSurfaceVariant.opacity(0.25)))
    }
}

/// Loads a `file://` image from the shared container.
struct LogImage: View {
    let url: URL
    @Environment(\.theme) private var theme
    var body: some View {
        if let data = try? Data(contentsOf: url), let ui = UIImage(data: data) {
            Image(uiImage: ui).resizable().scaledToFill()
        } else {
            theme.surfaceVariant
        }
    }
}

// ─── Swipe-to-delete helper for log rows ─────────────────────────────────────────

private extension View {
    /// Wrap a log row so a left-swipe reveals a Delete action (Android SwipeToDismiss).
    func swipeActionDelete(_ onDelete: @escaping () -> Void) -> some View {
        self.swipeActions(edge: .trailing, allowsFullSwipe: true) {
            Button(role: .destructive, action: onDelete) { Label("Delete", systemImage: "trash") }
        }
    }
}

// ─── Share sheet bridge ─────────────────────────────────────────────────────────

struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
