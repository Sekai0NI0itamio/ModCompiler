import Foundation

// MARK: - Manifest

struct ManifestTarget: Hashable, Codable {
    let version: String
    let loader: String
}

// MARK: - Version range mapping
// Maps a published MC version string to the manifest target version string.
// e.g. "1.12.2" → "1.12" (because manifest uses min_version "1.12" for that range)
// e.g. "1.18.1" → "1.18.1" (explicit in supported_versions)
struct VersionRangeMap {
    // Maps published version → manifest target version
    // Built from version-manifest.json at startup
    private let map: [String: String]  // "1.12.2" → "1.12"

    init(manifestTargets: Set<ManifestTarget>) {
        // Build reverse lookup: for each published version that doesn't directly
        // match a manifest target, find the closest manifest target in the same range.
        // The key insight: manifest uses min_version for anchor_only ranges,
        // but mods publish the actual patch version (e.g. 1.12.2 not 1.12).
        var m: [String: String] = [:]

        // Known range mappings from version-manifest.json
        // Format: [publishedVersion: manifestTargetVersion]
        let knownMappings: [String: String] = [
            // 1.12-1.12.2 range: manifest target is "1.12" (min_version)
            // but mods publish "1.12", "1.12.1", "1.12.2"
            "1.12.1": "1.12",
            "1.12.2": "1.12",
            // 1.17-1.17.1 fabric: manifest target is "1.17" (min_version)
            // but mods may publish "1.17.1"
            // (forge has supported_versions: ["1.17.1"] so 1.17.1 maps directly)
            // 1.18-1.18.2 fabric: manifest target is "1.18" (min_version)
            // but mods publish "1.18.1", "1.18.2"
            // (forge has explicit supported_versions so those map directly)
            // 1.21-1.21.1 fabric: manifest target is "1.21" (min_version)
            // but mods may publish "1.21.1"
            // 1.21.2-1.21.8 fabric: manifest target is "1.21.2" (min_version)
            // but mods publish "1.21.3" through "1.21.8"
            // (forge/neo have explicit supported_versions so those map directly)
            // 1.21.9-1.21.11 fabric: manifest target is "1.21.9" (min_version)
            // but mods publish "1.21.10", "1.21.11"
            // (forge/neo have explicit supported_versions so those map directly)
        ]
        m = knownMappings
        self.map = m
    }

    /// Returns the manifest target version for a published version string.
    /// If the published version is already a manifest target, returns it unchanged.
    /// Otherwise looks up the range mapping.
    func manifestVersion(for published: String, loader: String, manifestTargets: Set<ManifestTarget>) -> String? {
        // Direct match first
        if manifestTargets.contains(ManifestTarget(version: published, loader: loader)) {
            return published
        }
        // Try range mapping
        if let mapped = map[published],
           manifestTargets.contains(ManifestTarget(version: mapped, loader: loader)) {
            return mapped
        }
        // Try prefix match: "1.12.2" → check if "1.12" is a target
        // Split on "." and try progressively shorter prefixes
        let parts = published.split(separator: ".")
        if parts.count >= 3 {
            // Try major.minor (e.g. "1.12" from "1.12.2")
            let majorMinor = "\(parts[0]).\(parts[1])"
            if manifestTargets.contains(ManifestTarget(version: majorMinor, loader: loader)) {
                return majorMinor
            }
        }
        return nil
    }
}

// MARK: - Modrinth Project

struct ModrinthProject: Identifiable, Decodable {
    let id: String
    let slug: String
    let title: String
    let description: String
    let downloads: Int
    let followers: Int
    let icon_url: String?
    let published: String
    let updated: String
    let game_versions: [String]
    let loaders: [String]
    let status: String
    let color: Int?
    let issues_url: String?
    let source_url: String?
    let wiki_url: String?
    let discord_url: String?
    let donation_urls: [DonationURL]?
    let body: String?
    let gallery: [GalleryImage]?

    var iconURL: URL? { icon_url.flatMap { URL(string: $0) } }
    var publishedDate: Date? { ISO8601DateFormatter().date(from: published) }
}

struct DonationURL: Decodable {
    let id: String
    let platform: String
    let url: String
}

struct GalleryImage: Decodable {
    let url: String
    let featured: Bool
    let title: String?
    let description: String?
}

// MARK: - Aesthetics Score

struct AestheticsScore {
    struct Check {
        let name: String
        let passed: Bool
        let tip: String
    }

    let checks: [Check]

    var score: Double {
        guard !checks.isEmpty else { return 0 }
        let passed = checks.filter { $0.passed }.count
        return Double(passed) / Double(checks.count) * 100.0
    }

    var missing: [Check] { checks.filter { !$0.passed } }
    var present: [Check] { checks.filter { $0.passed } }

    static func evaluate(_ p: ModrinthProject) -> AestheticsScore {
        var checks: [Check] = []

        checks.append(Check(
            name: "Icon",
            passed: p.icon_url != nil && !(p.icon_url?.isEmpty ?? true),
            tip: "Upload a mod icon (at least 128×128 PNG)"
        ))
        checks.append(Check(
            name: "Title",
            passed: p.title.count >= 4,
            tip: "Set a clear, descriptive title"
        ))
        checks.append(Check(
            name: "Description",
            passed: p.description.count >= 50,
            tip: "Write a detailed description (50+ chars)"
        ))
        let bodyLen = p.body?.count ?? 0
        checks.append(Check(
            name: "Body / Long Description",
            passed: bodyLen >= 200,
            tip: "Add a rich body with features, screenshots, and usage instructions"
        ))
        let hasBodyImage = p.body.map { $0.contains("![") || $0.contains("<img") } ?? false
        checks.append(Check(
            name: "Images in Body",
            passed: hasBodyImage,
            tip: "Add screenshots or GIFs to the body using Markdown ![alt](url)"
        ))
        let hasYouTube = p.body.map {
            $0.contains("youtube.com") || $0.contains("youtu.be")
        } ?? false
        checks.append(Check(
            name: "YouTube Video",
            passed: hasYouTube,
            tip: "Embed a YouTube showcase video in the body"
        ))
        checks.append(Check(
            name: "Gallery",
            passed: !(p.gallery?.isEmpty ?? true),
            tip: "Upload gallery images to showcase your mod visually"
        ))
        checks.append(Check(
            name: "Issues / Bug Tracker",
            passed: !(p.issues_url?.isEmpty ?? true),
            tip: "Link to a GitHub Issues page or bug tracker"
        ))
        checks.append(Check(
            name: "Source Code",
            passed: !(p.source_url?.isEmpty ?? true),
            tip: "Link to your source code repository"
        ))
        checks.append(Check(
            name: "Discord",
            passed: !(p.discord_url?.isEmpty ?? true),
            tip: "Add a Discord invite link for community support"
        ))
        let hasDonation = !(p.donation_urls?.isEmpty ?? true)
        checks.append(Check(
            name: "Donation Links",
            passed: hasDonation,
            tip: "Add donation links (Ko-fi, Patreon, etc.) to support your work"
        ))

        return AestheticsScore(checks: checks)
    }
}

// MARK: - Modrinth Version (for ghost detection)

struct ModrinthVersion: Decodable {
    let id: String
    let name: String
    let version_number: String
    let game_versions: [String]
    let loaders: [String]
    let status: String
    let date_published: String
    let downloads: Int
    let files: [ModrinthFile]
}

struct ModrinthFile: Decodable {
    let url: String
    let filename: String
    let primary: Bool
    let size: Int
}

// MARK: - Analytics

struct AnalyticsPoint: Identifiable, Codable {
    let id: UUID
    let date: Date
    let downloads: Double
    let views: Double
    let revenue: Double

    init(date: Date, downloads: Double, views: Double, revenue: Double) {
        self.id = UUID()
        self.date = date
        self.downloads = downloads
        self.views = views
        self.revenue = revenue
    }
}

// MARK: - Cached history entry

struct ProjectHistory: Codable {
    let projectId: String
    var points: [AnalyticsPoint]
    var lastFetched: Date
    // The date up to which we have "complete" history (day before yesterday)
    var historyThrough: Date
}

// MARK: - Coverage

struct CoverageInfo {
    let supported: Set<ManifestTarget>
    let ghost: Set<ManifestTarget>
    let missing: Set<ManifestTarget>
    let total: Int

    var percentage: Double {
        guard total > 0 else { return 0 }
        return Double(supported.count) / Double(total) * 100.0
    }
}

// MARK: - Investment Recommendation

struct InvestmentRecommendation: Identifiable {
    let id = UUID()
    let project: ModrinthProject
    let score: Double          // 0–100 composite investment score
    let reason: String         // Primary reason to invest
    let actions: [String]      // Specific actions to take
    let missingVersions: Int
    let aestheticsScore: Double
    let velocityScore: Double  // Recent download momentum
    let revenueScore: Double
}

// MARK: - Business Metrics

struct BusinessMetrics {
    let totalDownloads: Double
    let totalViews: Double
    let totalRevenue: Double
    let dailyDownloads: [AnalyticsPoint]

    var downloadGrowthRate: Double {
        guard dailyDownloads.count >= 60 else { return 0 }
        let recent = dailyDownloads.suffix(30).reduce(0) { $0 + $1.downloads }
        let prior  = dailyDownloads.dropLast(30).suffix(30).reduce(0) { $0 + $1.downloads }
        guard prior > 0 else { return recent > 0 ? 100 : 0 }
        return (recent - prior) / prior * 100.0
    }

    var viewToDownloadConversion: Double {
        guard totalViews > 0 else { return 0 }
        return totalDownloads / totalViews * 100.0
    }

    var avgDailyDownloads: Double {
        guard !dailyDownloads.isEmpty else { return 0 }
        return dailyDownloads.reduce(0) { $0 + $1.downloads } / Double(dailyDownloads.count)
    }

    var revenuePerDownload: Double {
        guard totalDownloads > 0 else { return 0 }
        return totalRevenue / totalDownloads
    }

    var downloadVelocity7d: Double {
        let pts = dailyDownloads.suffix(7)
        guard !pts.isEmpty else { return 0 }
        return pts.reduce(0) { $0 + $1.downloads } / Double(pts.count)
    }

    var downloadVelocity30d: Double {
        let pts = dailyDownloads.suffix(30)
        guard !pts.isEmpty else { return 0 }
        return pts.reduce(0) { $0 + $1.downloads } / Double(pts.count)
    }

    var peakDailyDownloads: Double {
        dailyDownloads.map { $0.downloads }.max() ?? 0
    }

    var downloadRetentionRate: Double {
        guard peakDailyDownloads > 0 else { return 0 }
        return downloadVelocity7d / peakDailyDownloads * 100.0
    }

    var cagr: Double {
        guard dailyDownloads.count >= 60 else { return 0 }
        let first30 = dailyDownloads.prefix(30).reduce(0) { $0 + $1.downloads }
        let last30  = dailyDownloads.suffix(30).reduce(0) { $0 + $1.downloads }
        guard first30 > 0 else { return last30 > 0 ? 100 : 0 }
        let years = Double(dailyDownloads.count) / 365.0
        guard years > 0 else { return 0 }
        return (pow(last30 / first30, 1.0 / years) - 1.0) * 100.0
    }

    var todayDownloads: Double { dailyDownloads.last?.downloads ?? 0 }
    var todayViews: Double     { dailyDownloads.last?.views ?? 0 }
}
