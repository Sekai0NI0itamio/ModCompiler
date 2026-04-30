import SwiftUI

@main
struct ModrinthDashboardApp: App {
    var body: some Scene {
        WindowGroup("Modrinth Dashboard") {
            ContentView()
        }
        .windowStyle(.hiddenTitleBar)
        .windowResizability(.contentSize)
        .commands {
            CommandGroup(replacing: .newItem) {}
        }
    }
}
