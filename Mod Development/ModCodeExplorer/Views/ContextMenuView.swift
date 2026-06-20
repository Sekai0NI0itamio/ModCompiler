import SwiftUI

struct ContextMenuView: View {
    let itemURL: URL
    let baseURL: URL?
    let isDirectory: Bool

    @EnvironmentObject var appState: AppState

    init(itemURL: URL, baseURL: URL? = nil, isDirectory: Bool = true) {
        self.itemURL = itemURL
        self.baseURL = baseURL
        self.isDirectory = isDirectory
    }

    var body: some View {
        Group {
            // Export options
            Button(action: handleExport) {
                Label("Export to Text", systemImage: "doc.text")
            }

            if isDirectory {
                Button(action: handleCountFiles) {
                    Label("Count Files", systemImage: "calculator")
                }
            }

            Divider()

            // File operations
            Button(action: handleReveal) {
                Label("Reveal in Finder", systemImage: "folder")
            }

            Button(action: handleCopyPath) {
                Label("Copy Path", systemImage: "doc.on.doc")
            }

            if let baseURL = baseURL, baseURL != itemURL {
                Button(action: handleCopyRelativePath) {
                    Label("Copy Relative Path", systemImage: "link")
                }
            }

            Divider()

            // Open externally
            Button(action: handleOpenExternal) {
                Label("Open in External Editor", systemImage: "square.and.arrow.up")
            }
        }
    }

    private func handleExport() {
        Task {
            let item = isDirectory ?
                CodeItem.directory(name: itemURL.lastPathComponent, url: itemURL, children: nil) :
                CodeItem.file(
                    name: itemURL.lastPathComponent,
                    url: itemURL,
                    extension: itemURL.pathExtension,
                    size: nil
                )
            await appState.exportItem(item)
        }
    }

    private func handleCountFiles() {
        let stats = FileSystemService.shared.countFiles(at: itemURL)
        ExportService.shared.showFileCountAlert(stats)
    }

    private func handleReveal() {
        ExportService.shared.revealInFinder(itemURL)
    }

    private func handleCopyPath() {
        ExportService.shared.copyPath(itemURL)
    }

    private func handleCopyRelativePath() {
        guard let baseURL = baseURL else { return }
        ExportService.shared.copyRelativePath(itemURL, baseURL: baseURL)
    }

    private func handleOpenExternal() {
        ExportService.shared.openInExternalEditor(itemURL)
    }
}

#Preview {
    ContextMenuView(
        itemURL: URL(fileURLWithPath: "/tmp/test"),
        baseURL: URL(fileURLWithPath: "/tmp"),
        isDirectory: true
    )
    .environmentObject(AppState())
}
