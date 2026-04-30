import Foundation

struct ManifestLoader {

    // MARK: - Load from file

    /// Loads all (version, loader) targets from version-manifest.json.
    /// Logic matches the Python manifest comparison script exactly:
    ///   versions = cfg.get('supported_versions', [r.get('min_version')])
    static func loadTargets() -> Set<ManifestTarget> {
        guard let path = findManifest(),
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
                if let sv = cfgDict["supported_versions"] as? [String], !sv.isEmpty {
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

    // MARK: - Find manifest file

    private static func findManifest() -> String? {
        var url = URL(fileURLWithPath: CommandLine.arguments[0])
            .deletingLastPathComponent()
        for _ in 0..<10 {
            let candidate = url.appendingPathComponent("version-manifest.json")
            if FileManager.default.fileExists(atPath: candidate.path) {
                return candidate.path
            }
            url = url.deletingLastPathComponent()
        }
        let cwd = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
            .appendingPathComponent("version-manifest.json")
        if FileManager.default.fileExists(atPath: cwd.path) { return cwd.path }
        return nil
    }

    // MARK: - Hardcoded fallback

    /// Matches Python script logic: supported_versions if present, else min_version.
    static func hardcodedTargets() -> Set<ManifestTarget> {
        var t = Set<ManifestTarget>()
        let forge = "forge", fabric = "fabric", neo = "neoforge"

        t.insert(.init(version: "1.8.9",  loader: forge))
        t.insert(.init(version: "1.12",   loader: forge))   // min_version of 1.12-1.12.2
        t.insert(.init(version: "1.16.5", loader: forge))
        t.insert(.init(version: "1.16.5", loader: fabric))
        t.insert(.init(version: "1.17.1", loader: forge))   // supported_versions: ["1.17.1"]
        t.insert(.init(version: "1.17",   loader: fabric))  // min_version of 1.17-1.17.1

        for v in ["1.18", "1.18.1", "1.18.2"] { t.insert(.init(version: v, loader: forge)) }
        t.insert(.init(version: "1.18", loader: fabric))    // min_version of 1.18-1.18.2

        for v in ["1.19", "1.19.1", "1.19.2", "1.19.3", "1.19.4"] {
            t.insert(.init(version: v, loader: forge))
            t.insert(.init(version: v, loader: fabric))
        }

        for v in ["1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.20.6"] {
            t.insert(.init(version: v, loader: forge))
        }
        for v in ["1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6"] {
            t.insert(.init(version: v, loader: fabric))
        }
        for v in ["1.20.2", "1.20.4", "1.20.5", "1.20.6"] {
            t.insert(.init(version: v, loader: neo))
        }

        for v in ["1.21", "1.21.1"] {
            t.insert(.init(version: v, loader: forge))
            t.insert(.init(version: v, loader: neo))
        }
        t.insert(.init(version: "1.21", loader: fabric))    // min_version of 1.21-1.21.1

        for v in ["1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8"] {
            t.insert(.init(version: v, loader: forge))
        }
        for v in ["1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8"] {
            t.insert(.init(version: v, loader: neo))
        }
        t.insert(.init(version: "1.21.2", loader: fabric))  // min_version of 1.21.2-1.21.8

        for v in ["1.21.9", "1.21.10", "1.21.11"] {
            t.insert(.init(version: v, loader: forge))
            t.insert(.init(version: v, loader: neo))
        }
        t.insert(.init(version: "1.21.9", loader: fabric))  // min_version of 1.21.9-1.21.11

        t.insert(.init(version: "26.1.2", loader: forge))
        for v in ["26.1", "26.1.1", "26.1.2"] {
            t.insert(.init(version: v, loader: neo))
            t.insert(.init(version: v, loader: fabric))
        }
        return t
    }
}
