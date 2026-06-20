import Foundation
import AppKit

/// Service for handling export operations
class ExportService {
    static let shared = ExportService()

    private init() {}

    // MARK: - Export Operations

    /// Export a single directory/project to text file
    func exportDirectory(
        at url: URL,
        baseURL: URL? = nil,
        to outputDir: URL? = nil
    ) async throws -> URL {
        let outputDirectory = outputDir ?? getDefaultExportDirectory()

        // Ensure output directory exists
        try FileManager.default.createDirectory(
            at: outputDirectory,
            withIntermediateDirectories: true
        )

        return try FileSystemService.shared.exportDirectoryToText(
            at: url,
            baseURL: baseURL,
            outputURL: outputDirectory
        )
    }

    /// Export multiple directories in batch
    func exportMultipleDirectories(
        urls: [URL],
        baseURL: URL? = nil,
        to outputDir: URL? = nil,
        progressHandler: @escaping (Int, Int, String) -> Void = { _, _, _ in }
    ) async throws -> [URL] {
        let outputDirectory = outputDir ?? getDefaultExportDirectory()

        try FileManager.default.createDirectory(
            at: outputDirectory,
            withIntermediateDirectories: true
        )

        return try FileSystemService.shared.exportMultipleDirectories(
            urls: urls,
            baseOutputURL: outputDirectory,
            progressHandler: progressHandler
        )
    }

    /// Get default export directory (Documents/ModExports)
    func getDefaultExportDirectory() -> URL {
        let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        return documents.appendingPathComponent("ModExports")
    }

    // MARK: - Finder & Clipboard Operations

    /// Reveal a file or directory in Finder
    func revealInFinder(_ url: URL) {
        NSWorkspace.shared.activateFileViewerSelecting([url])
    }

    /// Reveal multiple items in Finder
    func revealMultipleInFinder(_ urls: [URL]) {
        NSWorkspace.shared.activateFileViewerSelecting(urls)
    }

    /// Copy file/directory path to pasteboard
    func copyPath(_ url: URL) {
        let pasteboard = NSPasteboard.general
        pasteboard.clearContents()
        pasteboard.setString(url.path, forType: .string)
    }

    /// Copy multiple paths to pasteboard
    func copyMultiplePaths(_ urls: [URL]) {
        let pasteboard = NSPasteboard.general
        pasteboard.clearContents()
        let paths = urls.map { $0.path }.joined(separator: "\n")
        pasteboard.setString(paths, forType: .string)
    }

    /// Copy relative path (from a base URL)
    func copyRelativePath(_ url: URL, baseURL: URL) {
        let relativePath = url.path.replacingOccurrences(of: baseURL.path + "/", with: "")
        let pasteboard = NSPasteboard.general
        pasteboard.clearContents()
        pasteboard.setString(relativePath, forType: .string)
    }

    /// Copy file contents to pasteboard
    func copyFileContents(_ url: URL) {
        guard let content = FileSystemService.shared.readFileContent(at: url) else { return }

        let pasteboard = NSPasteboard.general
        pasteboard.clearContents()
        pasteboard.setString(content, forType: .string)
    }

    /// Open file/directory in external editor (default app)
    func openInExternalEditor(_ url: URL) {
        NSWorkspace.shared.open(url)
    }

    // MARK: - Dialog Helpers

    /// Show save panel for custom export location
    func showSavePanel(defaultName: String) -> URL? {
        let panel = NSSavePanel()
        panel.nameFieldStringValue = defaultName
        panel.allowedContentTypes = [.text]

        if panel.runModal() == .OK {
            return panel.url
        }
        return nil
    }

    /// Show folder selection panel
    func showFolderSelectionPanel(message: String = "Select Folder") -> URL? {
        let panel = NSOpenPanel()
        panel.canChooseFiles = false
        panel.canChooseDirectories = true
        panel.allowsMultipleSelection = false
        panel.message = message

        if panel.runModal() == .OK {
            return panel.url
        }
        return nil
    }

    /// Show alert with file count statistics
    func showFileCountAlert(_ stats: (total: Int, sourceFiles: Int, directories: Int)) {
        let alert = NSAlert()
        alert.messageText = "File Statistics"
        alert.informativeText = """
        Total Files: \(stats.total)
        Source Files: \(stats.sourceFiles)
        Directories: \(stats.directories)
        """
        alert.addButton(withTitle: "OK")
        alert.runModal()
    }
}
