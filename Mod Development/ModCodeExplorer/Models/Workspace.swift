import Foundation

/// Represents a workspace that can contain multiple projects
struct Workspace: Identifiable {
    let id = UUID()
    let name: String
    let path: URL
    var projects: [Project]

    init(name: String, path: URL, projects: [Project] = []) {
        self.name = name
        self.path = path
        self.projects = projects
    }
}

/// Represents a project (e.g., a mod or any codebase)
struct Project: Identifiable, Hashable {
    let id = UUID()
    let name: String
    let path: URL
    let platform: PlatformType?
    let version: String?
    var rootItems: [CodeItem]? // Lazy loaded

    enum PlatformType: String {
        case forge = "Forge"
        case fabric = "Fabric"
        case vanilla = "Vanilla"
        case other = "Other"

        var displayName: String { rawValue }
    }

    var displayName: String {
        if let version = version, let platform = platform {
            return "\(name) (\(version) - \(platform.displayName))"
        }
        return name
    }

    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }
}

/// Represents a file or directory in the codebase
enum CodeItem: Identifiable, Hashable {
    case directory(name: String, url: URL, children: [CodeItem]?)
    case file(name: String, url: URL, extension: String, size: Int64?)

    var id: String { url.path }

    var name: String {
        switch self {
        case .directory(let name, _, _):
            return name
        case .file(let name, _, _, _):
            return name
        }
    }

    var url: URL {
        switch self {
        case .directory(_, let url, _):
            return url
        case .file(_, let url, _, _):
            return url
        }
    }

    var isDirectory: Bool {
        switch self {
        case .directory:
            return true
        case .file:
            return false
        }
    }

    var fileExtension: String? {
        switch self {
        case .directory:
            return nil
        case .file(_, _, let ext, _):
            return ext
        }
    }

    var fileSize: Int64? {
        switch self {
        case .directory:
            return nil
        case .file(_, _, _, let size):
            return size
        }
    }

    /// Check if this is a source code file
    var isSourceFile: Bool {
        guard let ext = fileExtension else { return false }
        return SourceFileFilter.supportedExtensions.contains(ext.lowercased())
    }

    func hash(into hasher: inout Hasher) {
        hasher.combine(url.path)
    }
}

/// Context menu actions
enum ContextAction {
    case exportToText
    case revealInFinder
    case copyPath
    case copyRelativePath
    case openInExternalEditor
    case countFiles
    case expandAll
    case collapseAll

    var title: String {
        switch self {
        case .exportToText: return "Export to Text"
        case .revealInFinder: return "Reveal in Finder"
        case .copyPath: return "Copy Path"
        case .copyRelativePath: return "Copy Relative Path"
        case .openInExternalEditor: return "Open in External Editor"
        case .countFiles: return "Count Files"
        case .expandAll: return "Expand All"
        case .collapseAll: return "Collapse All"
        }
    }
}
