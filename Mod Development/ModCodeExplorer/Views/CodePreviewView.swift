import SwiftUI

struct CodePreviewView: View {
    @EnvironmentObject var appState: AppState

    var body: some View {
        VStack(spacing: 0) {
            if let item = appState.selectedItem {
                switch item {
                case .file(let name, _, let ext, let size):
                    fileView(name: name, extension: ext, size: size, item: item)
                case .directory(let name, _, _):
                    directoryView(name: name, item: item)
                }
            } else {
                EmptyStateView(
                    title: "No File Selected",
                    subtitle: "Select a file to view its contents"
                )
            }
        }
        .frame(minWidth: 300)
    }

    @ViewBuilder
    private func fileView(name: String, extension ext: String?, size: Int64?, item: CodeItem) -> some View {
        VStack(spacing: 0) {
            // Header with file info
            HStack {
                Image(systemName: fileIcon(for: ext))
                    .foregroundColor(fileColor(for: ext))

                Text(name)
                    .font(.headline)

                Spacer()

                // File size
                if let size = size {
                    Text(formatFileSize(size))
                        .font(.caption)
                        .foregroundColor(.secondary)
                }

                // Action buttons
                Button(action: copyFileContent) {
                    Image(systemName: "doc.on.doc")
                }
                .help("Copy file content")

                Button(action: revealFileInFinder) {
                    Image(systemName: "folder")
                }
                .help("Reveal in Finder")

                Button(action: openInExternalEditor) {
                    Image(systemName: "square.and.arrow.up")
                }
                .help("Open in external editor")
            }
            .padding(.horizontal)
            .padding(.vertical, 8)

            Divider()

            // Code content
            if let content = appState.fileContent {
                ScrollView {
                    TextEditor(text: .constant(content))
                        .font(.system(.body, design: .monospaced))
                        .textSelection(.enabled)
                        .padding(8)
                }
            } else {
                ProgressView("Loading...")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
    }

    @ViewBuilder
    private func directoryView(name: String, item: CodeItem) -> some View {
        VStack(spacing: 16) {
            Image(systemName: "folder.fill")
                .font(.system(size: 48))
                .foregroundColor(.blue)

            Text(name)
                .font(.title2)

            Button(action: {
                Task {
                    await appState.exportItem(item)
                }
            }) {
                Label("Export to Text", systemImage: "doc.text")
            }
            .buttonStyle(.borderedProminent)

            Button(action: revealFileInFinder) {
                Label("Reveal in Finder", systemImage: "folder")
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func fileIcon(for ext: String?) -> String {
        switch ext {
        case "java":
            return "file.text"
        case "kt", "kts":
            return "file.text"
        case "json":
            return "curlybraces"
        case "xml":
            return "anglebrackets"
        default:
            return "doc"
        }
    }

    private func fileColor(for ext: String?) -> Color {
        switch ext {
        case "java":
            return .orange
        case "kt", "kts":
            return .purple
        case "json":
            return .green
        case "xml":
            return .blue
        default:
            return .secondary
        }
    }

    private func formatFileSize(_ size: Int64) -> String {
        let formatter = ByteCountFormatter()
        formatter.allowedUnits = [.useKB, .useBytes]
        formatter.countStyle = .file
        return formatter.string(fromByteCount: size)
    }

    private func copyFileContent() {
        guard let item = appState.selectedItem else { return }
        ExportService.shared.copyFileContents(item.url)
    }

    private func revealFileInFinder() {
        guard let item = appState.selectedItem else { return }
        ExportService.shared.revealInFinder(item.url)
    }

    private func openInExternalEditor() {
        guard let item = appState.selectedItem else { return }
        ExportService.shared.openInExternalEditor(item.url)
    }
}

#Preview {
    CodePreviewView()
        .environmentObject(AppState())
}
