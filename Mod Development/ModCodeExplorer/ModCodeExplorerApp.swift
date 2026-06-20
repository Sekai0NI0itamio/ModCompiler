import SwiftUI

@main
struct ModCodeExplorerApp: App {
    @StateObject private var appState = AppState()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(appState)
                .frame(minWidth: 900, minHeight: 600)
        }
        .windowStyle(.automatic)
        .commands {
            CommandGroup(replacing: .newItem) {
                Button("Open Workspace") {
                    appState.openWorkspace()
                }
                .keyboardShortcut("o", modifiers: .command)
            }
        }
    }
}
