import Foundation

struct ManifestLoader {
    /// Loads all (version, loader) targets from version-manifest.json
    /// Searches upward from the app bundle to find the repo root.
    static func loadTargets() -> Set<ManifestTarget> {
        let manifestPath = findManifest()
        guard let path = manifestPath,
              let data = try? Data(contentsOf: URL(fileURLWithPath: path)),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let ranges = json["ranges"] as? [[String: Any]] else {
            return hardcodedTargets()
        }

        var targets = Set<ManifestTarget>()
        for range in ranges {
            guard let loaders = range["loaders"] as? [String: Any] else { continue }
            for (loader, cfg) in loaders {
                guard let cfgDict = cfg as? [String: Any] else { continue }
                let versions: [String]
                if let sv = cfgDict["supported_versions"] as? [String] {
                    versions = sv
                } else if let min = range["min_version"] as? String {
                    versions = [min]
                } else {
                    continue
                }
                for v in versions {
                    targets.insert(ManifestTarget(version: v, loader: loader))
                }
            }
        }
        return targets.isEmpty ? hardcodedTargets() : targets
    }

    private static func findManifest() -> String? {
        // Try relative to executable, then walk up
        var url = URL(fileURLWithPath: CommandLine.arguments[0])
            .deletingLastPathComponent()
        for _ in 0..<8 {
            let candidate = url.appendingPathComponent("version-manifest.json")
            if FileManager.default.fileExists(atPath: candidate.path) {
                return candidate.path
            }
            url = url.deletingLastPathComponent()
        }
        // Try current working directory
        let cwd = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
            .appendingPathComponent("version-manifest.json")
        if FileManager.default.fileExists(atPath: cwd.path) { return cwd.path }
        return nil
    }

    /// Hardcoded fallback matching the current manifest (April 2026)
    static func hardcodedTargets() -> Set<ManifestTarget> {
        var t = Set<ManifestTarget>()
        let forge = "forge", fabric = "fabric", neo = "neoforge"

        // 1.8.9 forge
        t.insert(.init(version: "1.8.9", loader: forge))
        // 1.12-1.12.2 forge
        for v in ["1.12", "1.12.2"] { t.insert(.init(version: v, loader: forge)) }
        // 1.16.5
        t.insert(.init(version: "1.16.5", loader: forge))
        t.insert(.init(version: "1.16.5", loader: fabric))
        // 1.17-1.17.1
        t.insert(.init(version: "1.17.1", loader: forge))
        t.insert(.init(version: "1.17", loader: fabric))
        // 1.18-1.18.2
        for v in ["1.18", "1.18.1", "1.18.2"] {
            t.insert(.init(version: v, loader: forge))
            t.insert(.init(version: v, loader: fabric))
        }
        // 1.19-1.19.4
        for v in ["1.19", "1.19.1", "1.19.2", "1.19.3", "1.19.4"] {
            t.insert(.init(version: v, loader: forge))
            t.insert(.init(version: v, loader: fabric))
        }
        // 1.20-1.20.6
        for v in ["1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.20.6"] {
            t.insert(.init(version: v, loader: forge))
        }
        for v in ["1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6"] {
            t.insert(.init(version: v, loader: fabric))
        }
        for v in ["1.20.2", "1.20.4", "1.20.5", "1.20.6"] {
            t.insert(.init(version: v, loader: neo))
        }
        // 1.21-1.21.1
        for v in ["1.21", "1.21.1"] {
            t.insert(.init(version: v, loader: forge))
            t.insert(.init(version: v, loader: neo))
        }
        t.insert(.init(version: "1.21", loader: fabric))
        // 1.21.2-1.21.8
        for v in ["1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8"] {
            t.insert(.init(version: v, loader: forge))
        }
        for v in ["1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8"] {
            t.insert(.init(version: v, loader: neo))
            t.insert(.init(version: v, loader: fabric))
        }
        // 1.21.9-1.21.11
        for v in ["1.21.9", "1.21.10", "1.21.11"] {
            t.insert(.init(version: v, loader: forge))
            t.insert(.init(version: v, loader: neo))
            t.insert(.init(version: v, loader: fabric))
        }
        // 26.1-26.x
        t.insert(.init(version: "26.1.2", loader: forge))
        for v in ["26.1", "26.1.1", "26.1.2"] {
            t.insert(.init(version: v, loader: neo))
            t.insert(.init(version: v, loader: fabric))
        }
        return t
    }
}
