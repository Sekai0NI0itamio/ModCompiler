import Foundation
import SwiftUI
import AppKit

/// Main application state manager
@MainActor
class AppState: ObservableObject {
    @Published var workspace: Workspace?
    @Published var selectedProject: Project?
    @Published var fileTree: [CodeItem] = []
    @Published var selectedItem: CodeItem?
    @Published var fileContent: String?
    @Published var isLoading = false
    @Published var searchText = ""
    @Published var showingExportProgress = false
    @Published var exportMessage = ""

    private let fileSystem = FileSystemService.shared
    private let exportService = ExportService.shared

    // MARK: - Workspace Management

    /// Open workspace folder dialog
    func openWorkspace() {
        let panel = NSOpenPanel()
        panel.canChooseFiles = false
        panel.canChooseDirectories = true
        panel.allowsMultipleSelection = false
        panel.message = "Select a project or workspace directory"

        if panel.runModal() == .OK, let url = panel.url {
            loadWorkspace(at: url)
        }
    }

    /// Load a workspace from the specified directory
    func loadWorkspace(at url: URL) {
        isLoading = true

        Task {
            let discoveredWorkspace = fileSystem.scanWorkspace(at: url)

            await MainActor.run {
                self.workspace = discoveredWorkspace
                self.isLoading = false

                if !discoveredWorkspace.projects.isEmpty {
                    self.exportMessage = "Loaded \(discoveredWorkspace.projects.count) project(s)"
                }
            }
        }
    }

    // MARK: - Project Operations

    /// Select a project and load its file tree
    func selectProject(_ project: Project) {
        selectedProject = project
        loadFileTree(for: project)
    }

    /// Load file tree for a project
    func loadFileTree(for project: Project) {
        isLoading = true

        Task {
            let tree = fileSystem.buildFileTree(at: project.path) ?? []

            await MainActor.run {
                self.fileTree = tree
                self.isLoading = false
            }
        }
    }

    /// Refresh current project
    func refreshCurrentProject() {
        guard let project = selectedProject else { return }
        loadFileTree(for: project)
    }

    // MARK: - File Item Operations

    /// Select a file or directory item
    func selectItem(_ item: CodeItem) {
        selectedItem = item

        if case .file(_, let url, _, _) = item {
            loadFileContent(from: url)
        } else {
            fileContent = nil
        }
    }

    /// Load children for a directory (lazy loading)
    func loadChildren(for item: CodeItem) -> [CodeItem]? {
        return fileSystem.loadChildren(for: item)
    }

    /// Load file content for preview
    func loadFileContent(from url: URL) {
        fileContent = fileSystem.readFileContent(at: url)
    }

    // MARK: - Context Menu Actions

    /// Handle context menu action for an item
    func handleContextAction(_ action: ContextAction, for item: CodeItem) async {
        switch action {
        case .exportToText:
            await exportItem(item)
        case .revealInFinder:
            exportService.revealInFinder(item.url)
        case .copyPath:
            exportService.copyPath(item.url)
        case .copyRelativePath:
            if let workspace = workspace {
                exportService.copyRelativePath(item.url, baseURL: workspace.path)
            }
        case .openInExternalEditor:
            exportService.openInExternalEditor(item.url)
        case .countFiles:
            if item.isDirectory {
                let stats = fileSystem.countFiles(at: item.url)
                exportService.showFileCountAlert(stats)
            }
        case .expandAll, .collapseAll:
            break
        }
    }

    /// Export a single item (directory or project)
    func exportItem(_ item: CodeItem) async {
        showingExportProgress = true
        exportMessage = "Exporting \(item.name)..."

        do {
            let outputFile = try await exportService.exportDirectory(at: item.url)

            await MainActor.run {
                showingExportProgress = false
                exportMessage = "Exported to: \(outputFile.lastPathComponent)"
                exportService.revealInFinder(outputFile)
            }
        } catch {
            await MainActor.run {
                showingExportProgress = false
                exportMessage = "Export failed: \(error.localizedDescription)"
            }
        }
    }

    /// Export entire workspace (all projects)
    func exportWorkspace() async {
        guard let workspace = workspace else { return }

        showingExportProgress = true
        exportMessage = "Exporting workspace..."

        do {
            let projectURLs = workspace.projects.map { $0.path }
            let outputFiles = try await exportService.exportMultipleDirectories(
                urls: projectURLs,
                progressHandler: { [weak self] current, total, message in
                    Task { @MainActor in
                        self?.exportMessage = "\(message) (\(current)/\(total))"
                    }
                }
            )

            await MainActor.run {
                showingExportProgress = false
                exportMessage = "Exported \(outputFiles.count) projects"
                if let firstFile = outputFiles.first {
                    exportService.revealInFinder(firstFile.deletingLastPathComponent())
                }
            }
        } catch {
            await MainActor.run {
                showingExportProgress = false
                exportMessage = "Workspace export failed: \(error.localizedDescription)"
            }
        }
    }

    // MARK: - Search & Filter

    /// Filter projects based on search text
    var filteredProjects: [Project] {
        guard let workspace = workspace else { return [] }

        if searchText.isEmpty {
            return workspace.projects
        }

        let query = searchText.lowercased()
        return workspace.projects.filter { project in
            project.name.lowercased().contains(query) ||
            (project.version?.lowercased().contains(query) ?? false) ||
            (project.platform?.displayName.lowercased().contains(query) ?? false)
        }
    }

    /// Get workspace statistics
    var workspaceStats: String {
        guard let workspace = workspace else { return "No workspace loaded" }

        let projectCount = workspace.projects.count
        return "\(projectCount) project(s)"
    }

    /// Get selected project statistics
    var selectedProjectStats: String {
        guard let project = selectedProject else { return "No project selected" }

        let stats = fileSystem.countFiles(at: project.path)
        return "Files: \(stats.total), Source: \(stats.sourceFiles), Dirs: \(stats.directories)"
    }
}
