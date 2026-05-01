import SwiftUI
import AppKit

// Entry point — matches the ModrinthDashboard pattern (swiftc -parse-as-library)
@main
struct ModrinthFetcherApp: App {
    var body: some Scene {
        WindowGroup("Modrinth Fetcher") {
            ContentView()
                .frame(minWidth: 780, minHeight: 560)
        }
        .windowStyle(.titleBar)
        .windowToolbarStyle(.unified)
        .commands {
            CommandGroup(replacing: .newItem) {}
        }
    }
}
