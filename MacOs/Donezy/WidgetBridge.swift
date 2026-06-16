import Foundation
import WidgetKit

/// Thin wrapper so the app can ask the home-screen widget to refresh after data
/// changes — the iOS analogue of `NextDueWidgetProvider.refreshAll`.
enum WidgetBridge {
    static func reload() {
        WidgetCenter.shared.reloadAllTimelines()
    }
}
