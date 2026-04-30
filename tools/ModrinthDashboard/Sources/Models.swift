import Foundation

// MARK: - Manifest

struct ManifestTarget: Hashable {
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
    let color: Int?

    var iconURL: URL? { icon_url.flatMap { URL(string: $0) } }
    var publishedDate: Date? { ISO8601DateFormatter().date(from: published) }
}

// MARK: - Modrinth Version (for ghost detection)

struct ModrinthVersion: Decodable {
    let id: String
    let name: String
    let version_number: String
    let game_versions: [String]
    let loaders: [String]
    let status: String   // "listed", "archived", "draft", "unlisted", "scheduled", "unknown"
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

struct AnalyticsPoint: Identifiable {
    let id = UUID()
    let date: Date
    let downloads: Double
    let views: Double
    let revenue: Double
}

// MARK: - Coverage

struct CoverageInfo {
    let supported: Set<ManifestTarget>   // green — published & listed
    let ghost: Set<ManifestTarget>       // orange — published but archived/unlisted
    let missing: Set<ManifestTarget>     // red — in manifest, not published
    let total: Int

    var percentage: Double {
        guard total > 0 else { return 0 }
        return Double(supported.count) / Double(total) * 100.0
    }
}

// MARK: - Business Metrics

struct BusinessMetrics {
    // Growth
    let totalDownloads: Double
    let totalViews: Double
    let totalRevenue: Double

    // Time-series derived
    let dailyDownloads: [AnalyticsPoint]

    // Computed KPIs
    var downloadGrowthRate: Double {        // MoM % change
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

    var avgDailyViews: Double {
        guard !dailyDownloads.isEmpty else { return 0 }
        return dailyDownloads.reduce(0) { $0 + $1.views } / Double(dailyDownloads.count)
    }

    var revenuePerDownload: Double {
        guard totalDownloads > 0 else { return 0 }
        return totalRevenue / totalDownloads
    }

    var downloadVelocity7d: Double {
        dailyDownloads.suffix(7).reduce(0) { $0 + $1.downloads } / 7.0
    }

    var downloadVelocity30d: Double {
        dailyDownloads.suffix(30).reduce(0) { $0 + $1.downloads } / 30.0
    }

    var peakDailyDownloads: Double {
        dailyDownloads.map { $0.downloads }.max() ?? 0
    }

    var downloadRetentionRate: Double {
        // % of peak still being achieved in last 7d
        guard peakDailyDownloads > 0 else { return 0 }
        return downloadVelocity7d / peakDailyDownloads * 100.0
    }

    var cagr: Double {
        // Compound Annual Growth Rate based on first vs last 30d
        guard dailyDownloads.count >= 60 else { return 0 }
        let first30 = dailyDownloads.prefix(30).reduce(0) { $0 + $1.downloads }
        let last30  = dailyDownloads.suffix(30).reduce(0) { $0 + $1.downloads }
        guard first30 > 0 else { return last30 > 0 ? 100 : 0 }
        let years = Double(dailyDownloads.count) / 365.0
        guard years > 0 else { return 0 }
        return (pow(last30 / first30, 1.0 / years) - 1.0) * 100.0
    }

    var todayDownloads: Double {
        dailyDownloads.last?.downloads ?? 0
    }

    var todayViews: Double {
        dailyDownloads.last?.views ?? 0
    }
}
