import SwiftUI

// ─── Category Badge ───────────────────────────────────────────────────────────

struct CategoryBadge: View {
    let category: CategoryOption
    var body: some View {
        HStack(spacing: 4) {
            Text(category.emoji).font(.system(size: 12))
            Text(category.label)
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(category.accent)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(category.accent.opacity(0.15), in: Capsule())
    }
}

// ─── Category Avatar ──────────────────────────────────────────────────────────

struct CategoryAvatar: View {
    let category: CategoryOption
    var size: CGFloat = 52
    var body: some View {
        ZStack {
            Circle()
                .fill(RadialGradient(
                    colors: [category.accent.opacity(0.28), category.accent.opacity(0.10)],
                    center: .center, startRadius: 0, endRadius: size / 2))
            Circle().strokeBorder(category.accent.opacity(0.25), lineWidth: 1)
            Text(category.emoji).font(.system(size: size * 0.42))
        }
        .frame(width: size, height: size)
    }
}

// ─── Status Pill ──────────────────────────────────────────────────────────────

struct StatusPill: View {
    let info: ReminderStatusInfo
    var body: some View {
        Text(info.label)
            .font(.system(size: 12, weight: .semibold))
            .foregroundColor(info.color)
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(info.color.opacity(0.13), in: Capsule())
    }
}

// ─── Section Header ───────────────────────────────────────────────────────────

struct SectionHeader: View {
    let title: String
    var subtitle: String = ""
    @Environment(\.theme) private var theme
    var body: some View {
        HStack {
            Text(title).font(.system(size: 16, weight: .semibold))
            Spacer()
            if !subtitle.isEmpty {
                Text(subtitle)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(theme.onSurfaceVariant)
            }
        }
    }
}

// ─── Streak Badge ─────────────────────────────────────────────────────────────

struct StreakBadge: View {
    let streak: Int
    var body: some View {
        if streak >= 1 {
            let (bg, fg): (Color, Color) = {
                if streak >= 30 { return (Color(hex: 0xFFD700), Color(hex: 0x7A5000)) }
                if streak >= 14 { return (Color(hex: 0xFF8C00), Color(hex: 0x5A2800)) }
                if streak >= 7  { return (Color(hex: 0xFF6347), Color(hex: 0x4A0E00)) }
                return (Color(hex: 0x2D7A46).opacity(0.18), Color(hex: 0x2D7A46))
            }()
            let label = streak == 1 ? "Day 1 — keep it going" : "\(streak) day streak"
            HStack(spacing: 3) {
                Text("🔥").font(.system(size: 12))
                Text(label)
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundColor(streak >= 7 ? fg : Color(hex: 0x2D7A46))
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(bg.opacity(0.18), in: Capsule())
        }
    }
}

// ─── Star Rating (editable) ─────────────────────────────────────────────────────

struct StarRatingRow: View {
    let rating: Int?
    let onRate: (Int?) -> Void
    @Environment(\.theme) private var theme
    var body: some View {
        HStack(spacing: 2) {
            ForEach(1...5, id: \.self) { star in
                let filled = (rating ?? 0) >= star
                Image(systemName: filled ? "star.fill" : "star")
                    .font(.system(size: 22))
                    .foregroundColor(filled ? Color(hex: 0xFFC107) : theme.onSurfaceVariant.opacity(0.4))
                    .scaleEffect(filled ? 1.15 : 1.0)
                    .animation(.spring(response: 0.3, dampingFraction: 0.4), value: rating)
                    .onTapGesture { onRate(rating == star ? nil : star) }
            }
        }
    }
}

// ─── Stars display (read-only) ────────────────────────────────────────────────

struct StarDisplay: View {
    let rating: Int
    @Environment(\.theme) private var theme
    var body: some View {
        HStack(spacing: 2) {
            ForEach(1...5, id: \.self) { star in
                Image(systemName: star <= rating ? "star.fill" : "star")
                    .font(.system(size: 16))
                    .foregroundColor(star <= rating ? Color(hex: 0xFFC107) : theme.onSurfaceVariant.opacity(0.3))
            }
        }
    }
}

// ─── Category Filter Chips ────────────────────────────────────────────────────

struct CategoryFilterRow: View {
    let labels: [String]
    let selected: String
    let onSelected: (String) -> Void
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(labels, id: \.self) { label in
                    FilterChip(text: label, selected: selected == label) { onSelected(label) }
                }
            }
        }
    }
}

// ─── Reusable chips ─────────────────────────────────────────────────────────────

/// Material3-style FilterChip: outlined when unselected, tonal-filled when selected.
struct FilterChip: View {
    let text: String
    let selected: Bool
    var onTap: () -> Void
    @Environment(\.theme) private var theme
    var body: some View {
        Button(action: onTap) {
            Text(text)
                .font(.system(size: 14, weight: selected ? .semibold : .regular))
                .foregroundColor(selected ? theme.onSecondaryContainer : theme.onSurfaceVariant)
                .padding(.horizontal, 14).padding(.vertical, 8)
                .background(selected ? theme.secondaryContainer : .clear, in: Capsule())
                .overlay(Capsule().strokeBorder(theme.onSurfaceVariant.opacity(selected ? 0 : 0.4), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

/// A two-line FilterChip (label + description), used for reminder presets.
struct DualFilterChip: View {
    let label: String
    let detail: String
    let selected: Bool
    var onTap: () -> Void
    @Environment(\.theme) private var theme
    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 1) {
                Text(label).font(.system(size: 14, weight: selected ? .semibold : .regular))
                Text(detail).font(.system(size: 11))
            }
            .foregroundColor(selected ? theme.onSecondaryContainer : theme.onSurfaceVariant)
            .padding(.horizontal, 12).padding(.vertical, 6)
            .background(selected ? theme.secondaryContainer : .clear, in: Capsule())
            .overlay(Capsule().strokeBorder(theme.onSurfaceVariant.opacity(selected ? 0 : 0.4), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

/// AssistChip used for quick-log presets.
struct AssistChip: View {
    let text: String
    var onTap: () -> Void
    @Environment(\.theme) private var theme
    var body: some View {
        Button(action: onTap) {
            Text(text)
                .font(.system(size: 14))
                .foregroundColor(theme.onSurface)
                .padding(.horizontal, 14).padding(.vertical, 8)
                .overlay(Capsule().strokeBorder(theme.onSurfaceVariant.opacity(0.4), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

// ─── Empty states ─────────────────────────────────────────────────────────────

struct EmptyTrackerState: View {
    let hasTrackers: Bool
    let onClear: () -> Void
    @Environment(\.theme) private var theme
    var body: some View {
        VStack(spacing: 12) {
            Text("📭").font(.system(size: 48))
            Text(hasTrackers ? "No trackers match this view" : "No trackers yet")
                .font(.system(size: 16, weight: .semibold))
            Text(hasTrackers ? "Clear your search or switch categories." : "Tap ＋ to create your first tracker.")
                .font(.system(size: 14))
                .foregroundColor(theme.onSurfaceVariant)
                .multilineTextAlignment(.center)
            if hasTrackers {
                Button("Clear filters", action: onClear)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 32)
    }
}

struct EmptyLogState: View {
    @Environment(\.theme) private var theme
    var body: some View {
        VStack(spacing: 8) {
            Text("📝").font(.system(size: 36))
            Text("No logs yet").font(.system(size: 14, weight: .semibold))
            Text("Use a quick log chip or write a detailed entry.")
                .font(.system(size: 12))
                .foregroundColor(theme.onSurfaceVariant)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 24)
    }
}

// ─── Card container ─────────────────────────────────────────────────────────────

/// Elevated surface card matching Material3 ElevatedCard.
struct ElevatedCardView<Content: View>: View {
    @ViewBuilder var content: () -> Content
    @Environment(\.theme) private var theme
    var body: some View {
        content()
            .background(theme.surface, in: RoundedRectangle(cornerRadius: 16))
            .shadow(color: .black.opacity(0.12), radius: 3, y: 1)
    }
}

/// Outlined surface card matching Material3 OutlinedCard.
struct OutlinedCardView<Content: View>: View {
    @ViewBuilder var content: () -> Content
    @Environment(\.theme) private var theme
    var body: some View {
        content()
            .background(theme.surface, in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(theme.onSurfaceVariant.opacity(0.25), lineWidth: 1))
    }
}
