import Foundation

/// Filters and manages source code files
struct SourceFileFilter {
    /// Supported source code file extensions
    static let supportedExtensions: Set<String> = [
        "java", "kt", "kts", "groovy", "scala",
        "json", "xml", "properties", "yml", "yaml",
        "toml", "cfg", "conf"
    ]

    /// Directories to exclude from scanning
    static let excludedDirectories: Set<String> = [
        "build", ".gradle", ".idea", ".vscode",
        "assets", "resources", "generated",
        ".git", "node_modules", "bin", "out"
    ]

    /// Check if a file should be included in export
    static func shouldIncludeFile(_ url: URL) -> Bool {
        let ext = url.pathExtension.lowercased()
        return supportedExtensions.contains(ext)
    }

    /// Check if a directory should be excluded
    static func shouldExcludeDirectory(_ name: String) -> Bool {
        excludedDirectories.contains(name.lowercased())
    }
}
