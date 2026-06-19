import SwiftUI

struct OnboardingScreen: View {
    let onComplete: () -> Void
    @Environment(\.theme) private var theme
    
    @State private var currentPage = 0
    @State private var floatOffset: CGFloat = -8
    
    private let pages = [
        OnboardingPageData(
            title: "Welcome to Donezy",
            description: "Your personal dashboard to track hobbies, form good habits, and structure your daily achievements.",
            emoji: "✨",
            colors: [Color(hex: 0x1A6B48), Color(hex: 0x2D7A46)]
        ),
        OnboardingPageData(
            title: "Smart Reminders & Streaks",
            description: "Stay accountable with customizable reminders, snoozes, and automatic streak tracking for every tracker.",
            emoji: "🔔",
            colors: [Color(hex: 0xFFFF8C00), Color(hex: 0xFFFF6347)]
        ),
        OnboardingPageData(
            title: "Unlock Achievements",
            description: "Earn badges, track your insights visually, and share your progress cards directly with friends.",
            emoji: "🏆",
            colors: [Color(hex: 0x3F51B5), Color(hex: 0x00BCD4)]
        )
    ]
    
    var body: some View {
        ZStack {
            theme.background.ignoresSafeArea()
            
            VStack(spacing: 0) {
                TabView(selection: $currentPage) {
                    ForEach(0..<pages.count, id: \.self) { index in
                        pageView(for: pages[index])
                            .tag(index)
                    }
                }
                .tabViewStyle(PageTabViewStyle(indexDisplayMode: .never))
                
                // Bottom Controls
                HStack {
                    // Page Indicators
                    HStack(spacing: 6) {
                        ForEach(0..<pages.count, id: \.self) { index in
                            let active = currentPage == index
                            Capsule()
                                .fill(active ? theme.primary : theme.onSurfaceVariant.opacity(0.3))
                                .frame(width: active ? 20 : 8, height: 8)
                                .animation(.spring(response: 0.3, dampingFraction: 0.6, blendDuration: 0), value: currentPage)
                        }
                    }
                    
                    Spacer()
                    
                    // Buttons
                    HStack(spacing: 12) {
                        if currentPage < 2 {
                            Button(action: {
                                withAnimation {
                                    currentPage = 2
                                }
                            }) {
                                Text("Skip")
                                    .font(.system(size: 15, weight: .medium))
                                    .foregroundColor(theme.primary)
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 8)
                            }
                            .buttonStyle(.plain)
                            
                            Button(action: {
                                withAnimation {
                                    currentPage += 1
                                }
                            }) {
                                Text("Next")
                                    .font(.system(size: 15, weight: .semibold))
                                    .foregroundColor(theme.onPrimary)
                                    .padding(.horizontal, 20)
                                    .padding(.vertical, 10)
                                    .background(theme.primary, in: RoundedRectangle(cornerRadius: 12))
                            }
                            .buttonStyle(.plain)
                        } else {
                            Button(action: onComplete) {
                                Text("Get Started")
                                    .font(.system(size: 15, weight: .bold))
                                    .foregroundColor(theme.onPrimary)
                                    .padding(.horizontal, 24)
                                    .padding(.vertical, 12)
                                    .background(theme.primary, in: RoundedRectangle(cornerRadius: 12))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .padding(.horizontal, 32)
                .padding(.bottom, 40)
            }
        }
        .onAppear {
            withAnimation(.easeInOut(duration: 2.0).repeatForever(autoreverses: true)) {
                floatOffset = 8
            }
        }
    }
    
    @ViewBuilder
    private func pageView(for data: OnboardingPageData) -> some View {
        VStack(spacing: 0) {
            Spacer()
            
            // Floating emoji container
            ZStack {
                Circle()
                    .fill(LinearGradient(
                        colors: data.colors,
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ))
                    .frame(width: 140, height: 140)
                
                Circle()
                    .fill(theme.background)
                    .frame(width: 136, height: 136)
                
                Text(data.emoji)
                    .font(.system(size: 64))
            }
            .offset(y: floatOffset)
            
            Spacer().frame(height: 40)
            
            Text(data.title)
                .font(.system(size: 28, weight: .bold))
                .foregroundColor(theme.onBackground)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
            
            Spacer().frame(height: 16)
            
            Text(data.description)
                .font(.system(size: 16))
                .foregroundColor(theme.onSurfaceVariant)
                .multilineTextAlignment(.center)
                .lineSpacing(4)
                .padding(.horizontal, 32)
            
            Spacer()
        }
    }
}

struct OnboardingPageData {
    let title: String
    let description: String
    let emoji: String
    let colors: [Color]
}
