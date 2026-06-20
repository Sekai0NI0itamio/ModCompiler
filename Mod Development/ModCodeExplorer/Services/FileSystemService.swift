import Foundation

// Import models from same module (no import needed in Swift)
// Workspace, Project, CodeItem are available automatically

/// Service for file system operations and directory traversal
class FileSystemService {
    static let shared = FileSystemService()

    private init() {}

    // MARK: - Workspace & Project Scanning

    /// Scan a directory and create a workspace with all discovered projects
    func scanWorkspace(at rootURL: URL) -> Workspace {
        let workspaceName = rootURL.lastPathComponent
        let projects = scanForProjects(in: rootURL)

        return Workspace(name: workspaceName, path: rootURL, projects: projects)
    }

    /// Scan a directory for projects (mods or any codebase)
    func scanForProjects(in rootURL: URL) -> [Project] {
        var projects: [Project] = []

        guard let contents = try? FileManager.default.contentsOfDirectory(
            at: rootURL,
            includingPropertiesForKeys: nil,
            options: [.skipsHiddenFiles]
        ) else {
            return projects
        }

        for itemURL in contents where itemURL.hasDirectoryPath {
            let itemName = itemURL.lastPathComponent

            // Skip excluded directories
            if SourceFileFilter.shouldExcludeDirectory(itemName) {
                continue
            }

            // Check if this is a valid project
            if isValidProject(itemURL) {
                let platform = detectPlatform(from: itemURL)
                let version = extractVersion(from: itemName, in: itemURL)

                let project = Project(
                    name: itemName,
                    path: itemURL,
                    platform: platform,
                    version: version,
                    rootItems: nil
                )
                projects.append(project)
            }
        }

        return projects.sorted { $0.name < $1.name }
    }

    /// Load root items for a project (lazy loading)
    func loadProjectRoot(for project: inout Project) {
        project.rootItems = buildFileTree(at: project.path)
    }

    // MARK: - File Tree Building

    /// Build a hierarchical tree of code items from a directory
    func buildFileTree(at url: URL, maxDepth: Int = 10, currentDepth: Int = 0) -> [CodeItem]? {
        guard currentDepth < maxDepth else { return nil }

        guard let contents = try? FileManager.default.contentsOfDirectory(
            at: url,
            includingPropertiesForKeys: [.isDirectoryKey, .fileSizeKey],
            options: [.skipsHiddenFiles]
        ) else {
            return nil
        }

        var items: [CodeItem] = []

        for itemURL in contents {
            let name = itemURL.lastPathComponent

            // Skip excluded directories
            if itemURL.hasDirectoryPath && SourceFileFilter.shouldExcludeDirectory(name) {
                continue
            }

            if itemURL.hasDirectoryPath {
                // For directories, don't load children immediately (lazy loading)
                let directory = CodeItem.directory(
                    name: name,
                    url: itemURL,
                    children: nil
                )
                items.append(directory)
            } else {
                // Get file size
                let fileSize = (try? itemURL.resourceValues(forKeys: [.fileSizeKey]))?.fileSize ?? 0
                let ext = itemURL.pathExtension.lowercased()

                let file = CodeItem.file(
                    name: name,
                    url: itemURL,
                    extension: ext,
                    size: Int64(fileSize)
                )
                items.append(file)
            }
        }

        return items.sorted { $0.name < $1.name }
    }

    /// Load children for a directory (for lazy loading)
    func loadChildren(for directory: CodeItem) -> [CodeItem]? {
        guard case .directory(_, let url, _) = directory else {
            return nil
        }
        return buildFileTree(at: url)
    }

    // MARK: - File Operations

    /// Read file content as string
    func readFileContent(at url: URL) -> String? {
        return try? String(contentsOf: url, encoding: .utf8)
    }

    /// Get file size in bytes
    func getFileSize(at url: URL) -> Int64? {
        guard let attrs = try? FileManager.default.attributesOfItem(atPath: url.path) else {
            return nil
        }
        return attrs[.size] as? Int64
    }

    /// Count files in a directory (recursively)
    func countFiles(at url: URL) -> (total: Int, sourceFiles: Int, directories: Int) {
        var total = 0
        var sourceFiles = 0
        var directories = 0

        guard let enumerator = FileManager.default.enumerator(
            at: url,
            includingPropertiesForKeys: [.isDirectoryKey],
            options: [.skipsHiddenFiles]
        ) else {
            return (0, 0, 0)
        }

        for case let fileURL as URL in enumerator {
            if fileURL.hasDirectoryPath {
                let dirName = fileURL.lastPathComponent
                if SourceFileFilter.shouldExcludeDirectory(dirName) {
                    enumerator.skipDescendants()
                } else {
                    directories += 1
                }
            } else {
                total += 1
                if SourceFileFilter.shouldIncludeFile(fileURL) {
                    sourceFiles += 1
                }
            }
        }

        return (total, sourceFiles, directories)
    }

    // MARK: - Export Operations

    /// Export a directory (project, folder, or any level) to text
    func exportDirectoryToText(at url: URL, baseURL: URL? = nil, outputURL: URL) throws -> URL {
        var content = ""
        let separator = String(repeating: "=", count: 80)
        let base = baseURL ?? url.deletingLastPathComponent()

        // Collect all source files
        let sourceFiles = collectSourceFiles(at: url)

        for fileURL in sourceFiles {
            guard let fileContent = readFileContent(at: fileURL) else { continue }

            // Calculate relative path
            let relativePath = fileURL.path.replacingOccurrences(
                of: base.path + "/",
                with: ""
            )

            content += "\n\(separator)\n"
            content += "File: \(relativePath)\n"
            content += "\(separator)\n\n"
            content += fileContent
            content += "\n\n"
        }

        // Generate output filename
        let dirName = url.lastPathComponent
        let timestamp = DateFormatter.localizedString(
            from: Date(),
            dateStyle: .short,
            timeStyle: .short
        ).replacingOccurrences(of: "[^0-9]", with: "", options: .regularExpression)
        let outputFile = outputURL.appendingPathComponent("\(dirName)_export_\(timestamp).txt")

        try content.write(to: outputFile, atomically: true, encoding: .utf8)

        return outputFile
    }

    /// Export multiple directories/projects to separate files
    func exportMultipleDirectories(
        urls: [URL],
        baseOutputURL: URL,
        progressHandler: ((Int, Int, String) -> Void)? = nil
    ) throws -> [URL] {
        var outputFiles: [URL] = []

        for (index, url) in urls.enumerated() {
            let dirName = url.lastPathComponent
            progressHandler?(index + 1, urls.count, "Exporting \(dirName)...")

            let outputFile = try exportDirectoryToText(
                at: url,
                outputURL: baseOutputURL
            )
            outputFiles.append(outputFile)
        }

        return outputFiles
    }

    // MARK: - Private Helpers

    /// Collect all source files recursively
    private func collectSourceFiles(at url: URL) -> [URL] {
        var sourceFiles: [URL] = []

        guard let enumerator = FileManager.default.enumerator(
            at: url,
            includingPropertiesForKeys: [.isDirectoryKey],
            options: [.skipsHiddenFiles]
        ) else {
            return sourceFiles
        }

        for case let fileURL as URL in enumerator {
            // Skip excluded directories
            if fileURL.hasDirectoryPath {
                let dirName = fileURL.lastPathComponent
                if SourceFileFilter.shouldExcludeDirectory(dirName) {
                    enumerator.skipDescendants()
                }
                continue
            }

            // Include only source files
            if SourceFileFilter.shouldIncludeFile(fileURL) {
                sourceFiles.append(fileURL)
            }
        }

        return sourceFiles.sorted { $0.path < $1.path }
    }

    /// Check if a directory is a valid project
    private func isValidProject(_ url: URL) -> Bool {
        // Check for common project indicators
        let indicators = [
            "src", "build.gradle", "build.gradle.kts",
            "pom.xml", "package.json", "Cargo.toml",
            ".git", "Makefile", "CMakeLists.txt"
        ]

        for indicator in indicators {
            let path = url.appendingPathComponent(indicator)
            if FileManager.default.fileExists(atPath: path.path) {
                return true
            }
        }

        // Also check if it has source files
        let stats = countFiles(at: url)
        return stats.sourceFiles > 0
    }

    /// Detect platform type from directory
    private func detectPlatform(from url: URL) -> Project.PlatformType {
        let name = url.lastPathComponent.lowercased()

        if name.contains("fabric") {
            return .fabric
        } else if name.contains("forge") {
            return .forge
        } else if name.contains("vanilla") {
            return .vanilla
        }

        // Check for platform-specific files
        if FileManager.default.fileExists(atPath: url.appendingPathComponent("fabric.mod.json").path) {
            return .fabric
        } else if FileManager.default.fileExists(atPath: url.appendingPathComponent("META-INF/mods.toml").path) {
            return .forge
        }

        return .other
    }

    /// Extract version from directory name or files
    private func extractVersion(from dirName: String, in url: URL) -> String? {
        // Try to extract version pattern from directory name
        let pattern = "\\d+\\.\\d+\\.\\d+"
        if let range = dirName.range(of: pattern, options: .regularExpression) {
            return String(dirName[range])
        }

        // Try to read version from common files
        let versionFiles = ["version.txt", "VERSION", "gradle.properties"]
        for fileName in versionFiles {
            let fileURL = url.appendingPathComponent(fileName)
            if let content = readFileContent(at: fileURL) {
                // Look for version patterns
                if let range = content.range(of: pattern, options: .regularExpression) {
                    return String(content[range])
                }
            }
        }

        return nil
    }
}
