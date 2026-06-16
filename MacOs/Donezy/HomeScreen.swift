import SwiftUI
import UniformTypeIdentifiers

enum HeroFilter { case all, dueSoon, scheduled, streaks }

struct HomeScreen: View {
    @ObservedObject var viewModel: HobbyViewModel
    @Environment(\.theme) private var theme

    @State private var showCreateSheet = false
    @State private var prefillTemplate: HobbyTemplate?
    @State private var selectedFilter = "All"
    @State private var searchQuery = ""
    @State private var sortBy: SortBy = .dueSoon
    @State private var heroFilter: HeroFilter = .all
    @State private var now: Int64 = nowMillis()

    @State private var showBackupExporter = false
    @State private var showRestoreImporter = false

    private let tick = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        let hobbies = viewModel.hobbies
        let midnightTonight = computeMidnightTonight()
        let dueSoon = hobbies.filter { dueBeforeMidnight($0, midnightTonight) }.count
        let scheduled = hobbies.filter { $0.nextReminderAt != nil }.count
        let streakByHobby = viewModel.logDaysByHobby.mapValues { StreakMath.computeStreakFromDays($0) }
        let longestStreak = streakByHobby.values.max() ?? 0
        let activeStreaks = streakByHobby.values.filter { $0 >= 1 }.count
        let filterLabels = ["All"] + Array(Set(hobbies.map { $0.category })).filter { !$0.isEmpty }.sorted()
        let filtered = filteredHobbies(hobbies, midnightTonight: midnightTonight, streakByHobby: streakByHobby)

        VStack(spacing: 0) {
            topBar
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 14) {
                    DashboardHero(total: hobbies.count, dueSoon: dueSoon, scheduled: scheduled,
                                  longestStreak: longestStreak, activeStreaks: activeStreaks,
                                  selected: heroFilter,
                                  onSelect: { tapped in heroFilter = heroFilter == tapped ? .all : tapped })

                    if hobbies.isEmpty {
                        TemplateDeck { template in prefillTemplate = template; showCreateSheet = true }
                    }

                    VStack(alignment: .leading, spacing: 10) {
                        SectionHeader(title: "Trackers", subtitle: "\(filtered.count) shown")
                        CategoryFilterRow(labels: filterLabels, selected: selectedFilter) { selectedFilter = $0 }
                        if !hobbies.isEmpty {
                            searchField
                        }
                    }

                    // Log search results
                    if !searchQuery.trimmingCharacters(in: .whitespaces).isEmpty && !viewModel.logSearchResults.isEmpty {
                        SectionHeader(title: "Log entries matching \"\(viewModel.logSearchQuery)\"",
                                      subtitle: "\(viewModel.logSearchResults.count) found")
                        ForEach(viewModel.logSearchResults) { hit in
                            LogSearchHitCard(hit: hit) { viewModel.openDetail(hit.log.hobbyId) }
                        }
                    }

                    if filtered.isEmpty {
                        EmptyTrackerState(hasTrackers: !hobbies.isEmpty) {
                            selectedFilter = "All"; searchQuery = ""
                        }
                    }

                    ForEach(filtered) { hobby in
                        SwipeToConfirmRow(
                            onArchive: { viewModel.archiveHobby(id: hobby.id, name: hobby.name) },
                            onDelete: { viewModel.deleteHobby(id: hobby.id, name: hobby.name) }
                        ) {
                            TrackerCard(hobby: hobby, streak: streakByHobby[hobby.id] ?? 0, now: now,
                                        onTap: { viewModel.openDetail(hobby.id) },
                                        onPin: { viewModel.togglePin(hobby) })
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 4)
                .padding(.bottom, 120)
            }
        }
        .overlay(alignment: .bottomTrailing) {
            Button {
                prefillTemplate = nil; showCreateSheet = true
            } label: {
                Label("New tracker", systemImage: "plus")
                    .font(.system(size: 15, weight: .semibold))
                    .padding(.horizontal, 18).padding(.vertical, 14)
                    .foregroundColor(theme.onPrimary)
                    .background(theme.primary, in: Capsule())
                    .shadow(color: .black.opacity(0.2), radius: 6, y: 3)
            }
            .padding(20)
        }
        .onReceive(tick) { _ in now = nowMillis() }
        .onChange(of: searchQuery) { _, q in viewModel.setLogSearchQuery(q) }
        .sheet(isPresented: $showCreateSheet) {
            CreateTrackerSheet(prefill: prefillTemplate,
                onSave: { result in
                    viewModel.addHobby(name: result.name, category: result.category, notes: result.notes,
                                       reminderAt: result.reminder.reminderAt, recurrence: result.reminder.recurrence,
                                       weeklyGoal: result.weeklyGoal)
                    showCreateSheet = false; prefillTemplate = nil
                },
                onDismiss: { showCreateSheet = false; prefillTemplate = nil })
        }
        .fileExporter(isPresented: $showBackupExporter,
                      document: JSONFileDocument(data: viewModel.backupData() ?? Data()),
                      contentType: .json,
                      defaultFilename: viewModel.defaultBackupFilename()) { result in
            viewModel.handleBackupResult(result.map { $0 })
        }
        .fileImporter(isPresented: $showRestoreImporter, allowedContentTypes: [.json, .data]) { result in
            if case .success(let url) = result { viewModel.restoreFrom(url: url) }
        }
    }

    // ── Top bar ───────────────────────────────────────────────────────────────

    private var topBar: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Donezy").font(.system(size: 22, weight: .bold))
                Text("Plan it · Do it · Done it.")
                    .font(.system(size: 12)).foregroundColor(theme.onSurfaceVariant)
            }
            Spacer()
            Menu {
                Picker("Sort", selection: $sortBy) {
                    ForEach(SortBy.allCases) { Text($0.label).tag($0) }
                }
            } label: {
                Image(systemName: "arrow.up.arrow.down").font(.system(size: 18)).foregroundColor(theme.onSurface)
            }
            .padding(.horizontal, 6)
            Menu {
                Button { viewModel.openSettings() } label: { Label("Settings", systemImage: "gearshape") }
                Button { viewModel.openArchive() } label: { Label("Archive", systemImage: "archivebox") }
                Divider()
                Button { showBackupExporter = true } label: { Label("Backup to file", systemImage: "square.and.arrow.down") }
                Button { showRestoreImporter = true } label: { Label("Restore from file", systemImage: "arrow.clockwise") }
                Divider()
                Button { viewModel.openAbout() } label: { Label("About", systemImage: "info.circle") }
            } label: {
                Image(systemName: "ellipsis").font(.system(size: 18)).foregroundColor(theme.onSurface)
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 10)
    }

    private var searchField: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass").foregroundColor(theme.onSurfaceVariant)
            TextField("Search trackers and logs", text: $searchQuery)
            if !searchQuery.isEmpty {
                Button { searchQuery = "" } label: { Image(systemName: "xmark.circle.fill").foregroundColor(theme.onSurfaceVariant) }
            }
        }
        .padding(12)
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(theme.onSurfaceVariant.opacity(0.4)))
    }

    // ── Filtering / sorting ─────────────────────────────────────────────────────

    private func computeMidnightTonight() -> Int64 {
        let cal = Calendar.current
        let startTomorrow = cal.date(byAdding: .day, value: 1, to: cal.startOfDay(for: Date()))!
        return Int64(startTomorrow.timeIntervalSince1970 * 1000.0)
    }

    private func dueBeforeMidnight(_ h: Hobby, _ midnight: Int64) -> Bool {
        guard let t = h.nextReminderAt else { return false }
        return t <= midnight && t >= now - DAY_MS
    }

    private func filteredHobbies(_ hobbies: [Hobby], midnightTonight: Int64,
                                 streakByHobby: [Int64: Int]) -> [Hobby] {
        let q = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        let filtered = hobbies.filter { h in
            let matchCat = selectedFilter == "All" || h.category == selectedFilter
            let matchQ = q.isEmpty || h.name.localizedCaseInsensitiveContains(q)
                || h.category.localizedCaseInsensitiveContains(q) || h.notes.localizedCaseInsensitiveContains(q)
            let matchHero: Bool = {
                switch heroFilter {
                case .all: return true
                case .dueSoon: return dueBeforeMidnight(h, midnightTonight)
                case .scheduled: return h.nextReminderAt != nil
                case .streaks: return (streakByHobby[h.id] ?? 0) >= 1
                }
            }()
            return matchCat && matchQ && matchHero
        }
        switch sortBy {
        case .dueSoon:
            return filtered.sorted { a, b in
                if a.isPinned != b.isPinned { return a.isPinned }
                let an = a.nextReminderAt == nil, bn = b.nextReminderAt == nil
                if an != bn { return !an }
                return (a.nextReminderAt ?? .max) < (b.nextReminderAt ?? .max)
            }
        case .recentActivity:
            return filtered.sorted { a, b in
                if a.isPinned != b.isPinned { return a.isPinned }
                return a.createdAt > b.createdAt
            }
        case .alphabetical:
            return filtered.sorted { a, b in
                if a.isPinned != b.isPinned { return a.isPinned }
                return a.name.lowercased() < b.name.lowercased()
            }
        }
    }
}

// ─── Log search hit card ──────────────────────────────────────────────────────

private struct LogSearchHitCard: View {
    let hit: LogSearchHit
    let onTap: () -> Void
    @Environment(\.theme) private var theme
    var body: some View {
        let cat = categoryFor(hit.hobbyCategory)
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 8) {
                    Text(cat.emoji)
                    Text(hit.hobbyName).font(.system(size: 14, weight: .semibold))
                    Spacer()
                    Text(formatDateShort(hit.log.createdAt))
                        .font(.system(size: 11)).foregroundColor(theme.onSurfaceVariant)
                }
                Text(hit.log.entry)
                    .font(.system(size: 12)).foregroundColor(theme.onSurfaceVariant)
                    .lineLimit(2).multilineTextAlignment(.leading)
            }
            .padding(12).frame(maxWidth: .infinity, alignment: .leading)
        }
        .buttonStyle(.plain)
        .background(theme.surface, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(theme.onSurfaceVariant.opacity(0.25)))
    }
}

// ─── Dashboard hero ─────────────────────────────────────────────────────────────

struct DashboardHero: View {
    let total: Int
    let dueSoon: Int
    let scheduled: Int
    let longestStreak: Int
    let activeStreaks: Int
    let selected: HeroFilter
    let onSelect: (HeroFilter) -> Void
    @Environment(\.theme) private var theme

    private var title: String {
        switch selected {
        case .all: return "Today at a glance"
        case .dueSoon: return "Showing trackers due before midnight · tap to clear"
        case .scheduled: return "Showing scheduled trackers · tap to clear"
        case .streaks: return "Showing trackers logged today or yesterday · tap to clear"
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(title).font(.system(size: 16, weight: .bold)).foregroundColor(theme.onPrimary)
            HStack(spacing: 10) {
                HeroMetric(label: "Trackers", value: "\(total)", valueColor: theme.onPrimary,
                           selected: selected == .all) { onSelect(.all) }
                HeroMetric(label: "Due soon", value: "\(dueSoon)",
                           valueColor: dueSoon > 0 ? Color(hex: 0xFFCC80) : theme.onPrimary,
                           selected: selected == .dueSoon) { onSelect(.dueSoon) }
                HeroMetric(label: "Scheduled", value: "\(scheduled)", valueColor: theme.onPrimary,
                           selected: selected == .scheduled) { onSelect(.scheduled) }
                HeroMetric(label: activeStreaks > 1 ? "Best streak" : "Streak",
                           value: longestStreak > 0 ? "🔥 \(longestStreak)" : "—",
                           valueColor: longestStreak > 0 ? Color(hex: 0xFFD180) : theme.onPrimary,
                           selected: selected == .streaks) { onSelect(.streaks) }
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            LinearGradient(colors: [theme.primary, theme.tertiary], startPoint: .leading, endPoint: .trailing),
            in: RoundedRectangle(cornerRadius: 24))
    }
}

private struct HeroMetric: View {
    let label: String
    let value: String
    let valueColor: Color
    let selected: Bool
    let onTap: () -> Void
    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 2) {
                Text(value).font(.system(size: 24, weight: .bold)).foregroundColor(valueColor)
                Text(label).font(.system(size: 11)).foregroundColor(valueColor.opacity(0.9))
            }
            .padding(10).frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.white.opacity(selected ? 0.34 : 0.16), in: RoundedRectangle(cornerRadius: 14))
        }
        .buttonStyle(.plain)
    }
}

// ─── Tracker card ─────────────────────────────────────────────────────────────

struct TrackerCard: View {
    let hobby: Hobby
    let streak: Int
    let now: Int64
    let onTap: () -> Void
    let onPin: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        let category = categoryFor(hobby.category)
        let status = reminderStatusInfo(hobby.nextReminderAt, now: now)

        Button(action: onTap) {
            HStack(spacing: 0) {
                LinearGradient(colors: [category.accent, category.accent.opacity(0.3)],
                               startPoint: .top, endPoint: .bottom)
                    .frame(width: 4)
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        CategoryBadge(category: category)
                        Spacer()
                        HStack(spacing: 6) {
                            StatusPill(info: status)
                            if hobby.isPinned {
                                Image(systemName: "pin.fill").font(.system(size: 14)).foregroundColor(theme.primary)
                            }
                            Button(action: onPin) {
                                Image(systemName: hobby.isPinned ? "pin.fill" : "pin")
                                    .font(.system(size: 16))
                                    .foregroundColor(theme.onSurfaceVariant.opacity(0.6))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    HStack(spacing: 12) {
                        CategoryAvatar(category: category, size: 46)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(hobby.name).font(.system(size: 16, weight: .semibold)).foregroundColor(theme.onSurface)
                            if !hobby.notes.isEmpty {
                                Text(hobby.notes).font(.system(size: 12)).foregroundColor(theme.onSurfaceVariant)
                                    .lineLimit(2).multilineTextAlignment(.leading)
                            }
                        }
                        Spacer()
                    }
                    HStack {
                        HStack(spacing: 6) {
                            Image(systemName: "bell").font(.system(size: 12)).foregroundColor(status.color.opacity(0.8))
                            Text(status.detail).font(.system(size: 11)).foregroundColor(status.color)
                        }
                        Spacer()
                        HStack(spacing: 8) {
                            if streak >= 1 { StreakBadge(streak: streak) }
                            if hobby.recurrence.isRecurring {
                                Text("↺ recurring").font(.system(size: 11)).foregroundColor(theme.tertiary)
                            }
                        }
                    }
                }
                .padding(14)
            }
        }
        .buttonStyle(.plain)
        .background(theme.surface, in: RoundedRectangle(cornerRadius: 16))
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .shadow(color: .black.opacity(0.10), radius: 3, y: 1)
    }
}

// ─── Template deck ──────────────────────────────────────────────────────────────

struct TemplateDeck: View {
    let onApply: (HobbyTemplate) -> Void
    @Environment(\.theme) private var theme
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionHeader(title: "Starter templates", subtitle: "Tap to prefill")
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(hobbyTemplates) { template in
                        let cat = categoryFor(template.category)
                        Button { onApply(template) } label: {
                            VStack(alignment: .leading, spacing: 8) {
                                CategoryBadge(category: cat)
                                Text(template.title).font(.system(size: 14, weight: .semibold)).foregroundColor(theme.onSurface)
                                Text(template.notes).font(.system(size: 12)).foregroundColor(theme.onSurfaceVariant)
                                    .lineLimit(3).multilineTextAlignment(.leading)
                                Text("Default: \(reminderLabel(template.reminderHours))")
                                    .font(.system(size: 11)).foregroundColor(theme.primary)
                            }
                            .padding(14).frame(width: 220, alignment: .leading)
                        }
                        .buttonStyle(.plain)
                        .background(theme.surface, in: RoundedRectangle(cornerRadius: 16))
                        .shadow(color: .black.opacity(0.10), radius: 3, y: 1)
                    }
                }
            }
        }
    }
}

// ─── Swipe to confirm row ─────────────────────────────────────────────────────

/// Swipe a card aside to reveal an Archive (swipe left) or Delete (swipe right)
/// action that holds open until tapped — a port of the Android `SwipeToConfirmRow`.
struct SwipeToConfirmRow<Content: View>: View {
    let onArchive: () -> Void
    let onDelete: () -> Void
    @ViewBuilder var content: () -> Content
    @Environment(\.theme) private var theme

    @State private var offset: CGFloat = 0
    @State private var rowWidth: CGFloat = 1

    private var revealWidth: CGFloat { rowWidth * 0.20 }
    private var catchWidth: CGFloat { rowWidth * 0.12 }

    var body: some View {
        ZStack {
            // Background actions
            HStack {
                if offset > 1 {
                    actionButton(label: "Delete", icon: "trash",
                                 bg: Color(hex: 0x7A1F1A), fg: Color(hex: 0xFFE9E6),
                                 align: .leading) { withAnimation { offset = 0 }; onDelete() }
                }
                Spacer()
                if offset < -1 {
                    actionButton(label: "Archive", icon: "archivebox",
                                 bg: theme.errorContainer, fg: theme.onErrorContainer,
                                 align: .trailing) { withAnimation { offset = 0 }; onArchive() }
                }
            }
            // Foreground card
            content()
                .offset(x: offset)
                .gesture(
                    DragGesture()
                        .onChanged { value in
                            offset = max(-revealWidth, min(revealWidth, value.translation.width))
                        }
                        .onEnded { _ in
                            withAnimation(.easeOut(duration: 0.22)) {
                                if offset <= -catchWidth { offset = -revealWidth }
                                else if offset >= catchWidth { offset = revealWidth }
                                else { offset = 0 }
                            }
                        }
                )
        }
        .background(GeometryReader { geo in Color.clear.onAppear { rowWidth = geo.size.width } })
    }

    private func actionButton(label: String, icon: String, bg: Color, fg: Color,
                              align: Alignment, onTap: @escaping () -> Void) -> some View {
        Button(action: onTap) {
            VStack(spacing: 2) {
                Image(systemName: icon)
                Text(label).font(.system(size: 14, weight: .semibold))
            }
            .foregroundColor(fg)
            .frame(width: revealWidth)
            .frame(maxHeight: .infinity)
            .background(bg, in: RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
    }
}

// ─── JSON file document for export ──────────────────────────────────────────────

struct JSONFileDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    var data: Data
    init(data: Data) { self.data = data }
    init(configuration: ReadConfiguration) throws {
        data = configuration.file.regularFileContents ?? Data()
    }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: data)
    }
}
