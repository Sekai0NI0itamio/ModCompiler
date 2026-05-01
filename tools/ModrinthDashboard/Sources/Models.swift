import Foundation

// MARK: - Manifest

struct ManifestTarget: Hashable, Codable {
    let version: String
    let loader: String
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
    let monetization_status: String?
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

    var isPublic: Bool {
        status == "approved" || status == "archived"
    }
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
        return Double(checks.filter { $0.passed }.count) / Double(checks.count) * 100.0
    }

    var missing: [Check] { checks.filter { !$0.passed } }
    var present: [Check] { checks.filter { $0.passed } }

    static func evaluate(_ p: ModrinthProject) -> AestheticsScore {
        var checks: [Check] = []
        checks.append(Check(name: "Icon",
            passed: p.icon_url != nil && !(p.icon_url?.isEmpty ?? true),
            tip: "Upload a mod icon (at least 128×128 PNG)"))
        checks.append(Check(name: "Title",
            passed: p.title.count >= 4,
            tip: "Set a clear, descriptive title"))
        checks.append(Check(name: "Description",
            passed: p.description.count >= 50,
            tip: "Write a detailed description (50+ chars)"))
        checks.append(Check(name: "Body / Long Description",
            passed: (p.body?.count ?? 0) >= 200,
            tip: "Add a rich body with features, screenshots, and usage instructions"))
        checks.append(Check(name: "Images in Body",
            passed: p.body.map { $0.contains("![") || $0.contains("<img") } ?? false,
            tip: "Add screenshots or GIFs to the body using Markdown ![alt](url)"))
        checks.append(Check(name: "YouTube Video",
            passed: p.body.map { $0.contains("youtube.com") || $0.contains("youtu.be") } ?? false,
            tip: "Embed a YouTube showcase video in the body"))
        checks.append(Check(name: "Gallery",
            passed: !(p.gallery?.isEmpty ?? true),
            tip: "Upload gallery images to showcase your mod visually"))
        checks.append(Check(name: "Issues / Bug Tracker",
            passed: !(p.issues_url?.isEmpty ?? true),
            tip: "Link to a GitHub Issues page or bug tracker"))
        checks.append(Check(name: "Source Code",
            passed: !(p.source_url?.isEmpty ?? true),
            tip: "Link to your source code repository"))
        checks.append(Check(name: "Discord",
            passed: !(p.discord_url?.isEmpty ?? true),
            tip: "Add a Discord invite link for community support"))
        checks.append(Check(name: "Donation Links",
            passed: !(p.donation_urls?.isEmpty ?? true),
            tip: "Add donation links (Ko-fi, Patreon, etc.) to support your work"))
        return AestheticsScore(checks: checks)
    }
}

// MARK: - Modrinth Version

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
// Revenue field removed — Modrinth's internal accounting units are not reliable
// for display. Conversion rate (views → downloads) is the meaningful signal.

struct AnalyticsPoint: Identifiable, Codable {
    let id: UUID
    let date: Date
    let downloads: Double
    let views: Double

    init(date: Date, downloads: Double, views: Double) {
        self.id = UUID()
        self.date = date
        self.downloads = downloads
        self.views = views
    }
}

// MARK: - Cached history

struct ProjectHistory: Codable {
    let projectId: String
    var points: [AnalyticsPoint]
    var lastFetched: Date
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
// Ranked by conversion rate (views → downloads) as the primary signal.
// High conversion = the mod page convinces visitors to download = high quality audience.

struct InvestmentRecommendation: Identifiable {
    let id = UUID()
    let project: ModrinthProject
    let score: Double              // 0–100 composite score
    let reason: String
    let actions: [String]
    let missingVersions: Int
    let aestheticsScore: Double
    let conversionRate: Double     // % of views that become downloads (primary signal)
    let conversionScore: Double    // normalised 0–100 vs portfolio
    let velocityScore: Double      // normalised 0–100 vs portfolio
    let coverageGapScore: Double   // 0–100, higher = more missing versions
}

// MARK: - Business Metrics

struct BusinessMetrics {
    let totalDownloads: Double
    let totalViews: Double
    let dailyDownloads: [AnalyticsPoint]

    // Conversion rate: % of page views that result in a download.
    // This is the primary quality signal — a high conversion rate means
    // the mod page is compelling and the audience is engaged.
    var viewToDownloadConversion: Double {
        guard totalViews > 0 else { return 0 }
        return totalDownloads / totalViews * 100.0
    }

    var downloadGrowthRate: Double {
        guard dailyDownloads.count >= 60 else { return 0 }
        let recent = dailyDownloads.suffix(30).reduce(0) { $0 + $1.downloads }
        let prior  = dailyDownloads.dropLast(30).suffix(30).reduce(0) { $0 + $1.downloads }
        guard prior > 0 else { return recent > 0 ? 100 : 0 }
        return (recent - prior) / prior * 100.0
    }

    var viewGrowthRate: Double {
        guard dailyDownloads.count >= 60 else { return 0 }
        let recent = dailyDownloads.suffix(30).reduce(0) { $0 + $1.views }
        let prior  = dailyDownloads.dropLast(30).suffix(30).reduce(0) { $0 + $1.views }
        guard prior > 0 else { return recent > 0 ? 100 : 0 }
        return (recent - prior) / prior * 100.0
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

    var viewVelocity7d: Double {
        let pts = dailyDownloads.suffix(7)
        guard !pts.isEmpty else { return 0 }
        return pts.reduce(0) { $0 + $1.views } / Double(pts.count)
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

    // Conversion rate over last 30 days (more recent signal)
    var recentConversionRate: Double {
        let pts = dailyDownloads.suffix(30)
        let dl = pts.reduce(0) { $0 + $1.downloads }
        let v  = pts.reduce(0) { $0 + $1.views }
        guard v > 0 else { return 0 }
        return dl / v * 100.0
    }

    var todayDownloads: Double { dailyDownloads.last?.downloads ?? 0 }
    var todayViews: Double     { dailyDownloads.last?.views ?? 0 }
}
