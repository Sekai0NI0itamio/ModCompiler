import SwiftUI

struct ContentView: View {
    @EnvironmentObject var appState: AppState

    var body: some View {
        NavigationSplitView {
            SidebarView()
                .navigationSplitViewColumnWidth(min: 250, ideal: 300, max: 400)
        } content: {
            FileTreeView()
                .navigationSplitViewColumnWidth(min: 300, ideal: 350, max: 500)
        } detail: {
            CodePreviewView()
        }
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button(action: appState.openWorkspace) {
                    Label("Open Workspace", systemImage: "folder.badge.plus")
                }
                .help("Open project or workspace directory (⌘O)")
            }

            ToolbarItem(placement: .primaryAction) {
                Menu {
                    Button("Export Selected") {
                        Task {
                            if let item = appState.selectedItem {
                                await appState.exportItem(item)
                            }
                        }
                    }
                    .disabled(appState.selectedItem == nil)

                    Button("Export Current Project") {
                        Task {
                            if let project = appState.selectedProject {
                                let item = CodeItem.directory(
                                    name: project.name,
                                    url: project.path,
                                    children: nil
                                )
                                await appState.exportItem(item)
                            }
                        }
                    }
                    .disabled(appState.selectedProject == nil)

                    Divider()

                    Button("Export All Projects") {
                        Task {
                            await appState.exportWorkspace()
                        }
                    }
                    .disabled(appState.workspace?.projects.isEmpty ?? true)
                } label: {
                    Label("Export", systemImage: "doc.text")
                }
                .help("Export options")
            }

            ToolbarItem(placement: .primaryAction) {
                Button(action: appState.refreshCurrentProject) {
                    Image(systemName: "arrow.clockwise")
                }
                .disabled(appState.selectedProject == nil)
                .help("Refresh current project")
            }
        }
        .overlay {
            if appState.showingExportProgress {
                ProgressOverlay(message: appState.exportMessage)
            }
        }
    }
}

#Preview {
    ContentView()
        .environmentObject(AppState())
}
