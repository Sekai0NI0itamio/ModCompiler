import Foundation
import SwiftUI

// MARK: - Selection

enum ProjectSelection: Equatable {
    case allProjects
    case project(String)  // project id
}

@MainActor
final class DashboardViewModel: ObservableObject {

    // MARK: - Published state

    @Published var projects: [ModrinthProject] = []
    @Published var selection: ProjectSelection? = nil
    @Published var coverage: CoverageInfo?
    @Published var aesthetics: AestheticsScore?
    @Published var metrics: BusinessMetrics?
    @Published var analyticsPoints: [AnalyticsPoint] = []
    @Published var isLoadingList   = false
    @Published var isLoadingDetail = false
    @Published var isLoadingHistory = false   // full history download in progress
    @Published var historyProgress: Double = 0  // 0–1
    @Published var errorMessage: String?
    @Published var detailError: String?
    @Published var showSettings = false

    // "All Projects" combined
    @Published var portfolioMetrics: BusinessMetrics?
    @Published var portfolioPoints: [AnalyticsPoint] = []
    @Published var recommendations: [InvestmentRecommendation] = []
    @Published var coverageByProject: [String: CoverageInfo] = [:]
    @Published var aestheticsByProject: [String: AestheticsScore] = [:]

    // Portfolio stats for relative KPI scoring
    @Published var bestModDownloads: Double = 1
    @Published var avgModDownloads:  Double = 1

    let manifestTargets: Set<ManifestTarget> = ManifestLoader.loadTargets()
    let settings = AppSettings.shared

    var selectedProject: ModrinthProject? {
        guard case .project(let id) = selection else { return nil }
        return projects.first { $0.id == id }
    }

    // MARK: - Load project list

    func loadProjects() async {
        isLoadingList = true
        errorMessage  = nil
        await ModrinthAPI.shared.setToken(settings.apiToken)

        do {
            // Fetch with full body for aesthetics scoring
            var fetched = try await ModrinthAPI.shared.fetchUserProjects(username: settings.username)
            // Fetch full project details (includes body, gallery, links) for each
            fetched = await withTaskGroup(of: ModrinthProject?.self) { group in
                for p in fetched {
                    group.addTask {
                        (try? await ModrinthAPI.shared.fetchProject(id: p.id)) ?? p
                    }
                }
                var result: [ModrinthProject] = []
                for await p in group { if let p { result.append(p) } }
                return result
            }
            projects = fetched.sorted { $0.downloads > $1.downloads }

            let dl = projects.map { Double($0.downloads) }
            avgModDownloads  = dl.isEmpty ? 1 : dl.reduce(0, +) / Double(dl.count)
            bestModDownloads = dl.max() ?? 1

            // Score aesthetics for all projects
            for p in projects {
                aestheticsByProject[p.id] = AestheticsScore.evaluate(p)
            }
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoadingList = false
    }

    // MARK: - Initial history download (first launch)
    // Downloads full analytics history for all projects and caches to disk.
    // Subsequent refreshes only fetch the last 2 days.

    func downloadAllHistory() async {
        guard !settings.apiToken.isEmpty else { return }
        isLoadingHistory = true
        historyProgress  = 0
        await ModrinthAPI.shared.setToken(settings.apiToken)

        let total = Double(projects.count)
        for (i, project) in projects.enumerated() {
            await downloadHistory(for: project, forceFullRefetch: false)
            historyProgress = Double(i + 1) / total
        }

        // Build portfolio after all history is loaded
        buildPortfolioAnalytics()
        buildRecommendations()
        isLoadingHistory = false
    }

    // MARK: - Download history for a single project

    func downloadHistory(for project: ModrinthProject, forceFullRefetch: Bool) async {
        guard !settings.apiToken.isEmpty else { return }
        await ModrinthAPI.shared.setToken(settings.apiToken)

        if forceFullRefetch {
            await HistoryStore.shared.delete(projectId: project.id)
        }

        let (start, end, isIncremental) = await HistoryStore.shared.fetchRange(
            for: project.id,
            publishedDate: project.publishedDate
        )

        let days   = max(1, Int(end.timeIntervalSince(start) / 86400))
        let slices = min(days, 1024)

        do {
            let pts = try await ModrinthAPI.shared.fetchAnalytics(
                projectIds: [project.id],
                start: start,
                end: end,
                slices: slices
            )

            // "History through" = day before yesterday (complete days only)
            let cal = Calendar(identifier: .gregorian)
            let historyThrough = cal.date(byAdding: .day, value: -2,
                                          to: cal.startOfDay(for: Date()))!

            let history = await HistoryStore.shared.merge(
                projectId: project.id,
                newPoints: pts,
                fetchedThrough: historyThrough
            )

            // If this is the currently selected project, update the UI
            if case .project(let id) = selection, id == project.id {
                analyticsPoints = history.points
                buildMetrics(project: project, points: history.points)
            }
        } catch {
            // Fall back to synthetic if API fails
            if case .project(let id) = selection, id == project.id {
                buildSyntheticAnalytics(for: project)
            }
        }
    }

    // MARK: - Refresh (incremental — only last 2 days)

    func refreshAllHistory() async {
        await downloadAllHistory()
    }

    // MARK: - Load detail for selected project

    func loadDetail(for project: ModrinthProject) async {
        selection       = .project(project.id)
        isLoadingDetail = true
        coverage        = nil
        aesthetics      = aestheticsByProject[project.id]
        metrics         = nil
        analyticsPoints = []
        detailError     = nil

        await ModrinthAPI.shared.setToken(settings.apiToken)

        await withTaskGroup(of: Void.self) { group in
            group.addTask { await self.loadCoverage(for: project) }
            group.addTask { await self.loadAnalyticsForDetail(for: project) }
        }
        isLoadingDetail = false
    }

    // MARK: - Load "All Projects" view

    func loadAllProjects() async {
        selection       = .allProjects
        isLoadingDetail = true
        coverage        = nil
        metrics         = nil
        analyticsPoints = []
        detailError     = nil

        // Build portfolio from cached history
        buildPortfolioAnalytics()
        buildRecommendations()

        // If we have a token and no cached data, trigger a download
        if portfolioPoints.isEmpty && !settings.apiToken.isEmpty {
            await downloadAllHistory()
        }

        isLoadingDetail = false
    }

    // MARK: - Coverage

    func loadCoverage(for project: ModrinthProject) async {
        do {
            let versions = try await ModrinthAPI.shared.fetchVersions(projectId: project.id)
            let cov = computeCoverage(versions: versions)
            coverage = cov
            coverageByProject[project.id] = cov
        } catch { /* non-fatal */ }
    }

    private func computeCoverage(versions: [ModrinthVersion]) -> CoverageInfo {
        var supported = Set<ManifestTarget>()
        var ghost      = Set<ManifestTarget>()

        for v in versions {
            let isListed = (v.status == "listed")
            for mc in v.game_versions {
                for loader in v.loaders {
                    // Use range resolver to handle "1.12.2" → "1.12" mapping
                    guard let target = ManifestLoader.resolveTarget(
                        publishedVersion: mc,
                        loader: loader,
                        manifestTargets: manifestTargets
                    ) else { continue }

                    if isListed { supported.insert(target) }
                    else        { ghost.insert(target) }
                }
            }
        }

        let ghostOnly = ghost.subtracting(supported)
        let missing   = manifestTargets.subtracting(supported).subtracting(ghostOnly)

        return CoverageInfo(
            supported: supported,
            ghost:     ghostOnly,
            missing:   missing,
            total:     manifestTargets.count
        )
    }

    // MARK: - Analytics for detail view

    private func loadAnalyticsForDetail(for project: ModrinthProject) async {
        // Try cached history first
        if let history = await HistoryStore.shared.load(projectId: project.id),
           !history.points.isEmpty {
            analyticsPoints = history.points
            buildMetrics(project: project, points: history.points)

            // Trigger incremental update in background (don't block UI)
            if !settings.apiToken.isEmpty {
                Task { await downloadHistory(for: project, forceFullRefetch: false) }
            }
            return
        }

        // No cache — fetch now
        if !settings.apiToken.isEmpty {
            await downloadHistory(for: project, forceFullRefetch: false)
        } else {
            buildSyntheticAnalytics(for: project)
        }
    }

    // MARK: - Portfolio analytics (combine all projects)

    func buildPortfolioAnalytics() {
        guard !projects.isEmpty else { return }

        var allPoints: [Date: (downloads: Double, views: Double, revenue: Double)] = [:]
        let cal = Calendar(identifier: .gregorian)

        for project in projects {
            let history = HistoryStore.shared.loadSync(projectId: project.id)
            let pts: [AnalyticsPoint]
            let hasRealData = !(history?.points.isEmpty ?? true)

            if hasRealData, let h = history {
                pts = h.points
            } else {
                // Synthetic: spread downloads evenly, revenue = 0 (we don't know it)
                pts = syntheticPoints(for: project)
            }

            for pt in pts {
                let day = cal.startOfDay(for: pt.date)
                var existing = allPoints[day] ?? (0, 0, 0)
                existing.downloads += pt.downloads
                existing.views     += pt.views
                // Only add revenue from real API data — synthetic points have revenue=0
                existing.revenue   += pt.revenue
                allPoints[day] = existing
            }
        }

        let combined = allPoints.map { (day, vals) in
            AnalyticsPoint(date: day, downloads: vals.downloads, views: vals.views, revenue: vals.revenue)
        }.sorted { $0.date < $1.date }

        portfolioPoints = combined

        let totalDl  = combined.reduce(0) { $0 + $1.downloads }
        let totalV   = combined.reduce(0) { $0 + $1.views }
        // Revenue: sum from real API data only
        let totalRev = combined.reduce(0) { $0 + $1.revenue }
        let totalProjectDl = projects.reduce(0) { $0 + Double($1.downloads) }

        portfolioMetrics = BusinessMetrics(
            totalDownloads: max(totalDl, totalProjectDl),
            totalViews:     totalV,
            totalRevenue:   totalRev,
            dailyDownloads: combined
        )
    }

    // MARK: - Investment recommendations

    func buildRecommendations() {
        guard !projects.isEmpty else { return }

        // First pass: compute per-mod metrics to find portfolio maximums for normalisation
        struct ModStats {
            let project: ModrinthProject
            let metrics: BusinessMetrics
            let missingCount: Int
            let aestheticsScore: Double
            let revenuePerView: Double      // $ earned per page view — key monetisation signal
            let revenuePerDownload: Double  // $ earned per download
            let velocity7d: Double
        }

        var allStats: [ModStats] = []

        for project in projects {
            let cov = coverageByProject[project.id]
            let aes = aestheticsByProject[project.id] ?? AestheticsScore.evaluate(project)
            let missingCount = cov?.missing.count ?? max(0, manifestTargets.count - project.game_versions.count)

            let pts: [AnalyticsPoint]
            if let history = HistoryStore.shared.loadSync(projectId: project.id),
               !history.points.isEmpty {
                pts = history.points
            } else {
                pts = syntheticPoints(for: project)
            }

            let totalDl  = max(pts.reduce(0) { $0 + $1.downloads }, Double(project.downloads))
            let totalV   = pts.reduce(0) { $0 + $1.views }
            let totalRev = pts.reduce(0) { $0 + $1.revenue }

            let m = BusinessMetrics(
                totalDownloads: totalDl,
                totalViews:     totalV,
                totalRevenue:   totalRev,
                dailyDownloads: pts
            )

            // Revenue per view: how much each page view earns.
            // This is the most important signal — a high $/view mod attracts
            // high-value traffic that Modrinth's ad system monetises well.
            let revenuePerView     = totalV > 0 ? totalRev / totalV : 0
            let revenuePerDownload = totalDl > 0 ? totalRev / totalDl : 0

            allStats.append(ModStats(
                project: project,
                metrics: m,
                missingCount: missingCount,
                aestheticsScore: aes.score,
                revenuePerView: revenuePerView,
                revenuePerDownload: revenuePerDownload,
                velocity7d: m.downloadVelocity7d
            ))
        }

        // Portfolio maximums for normalisation (avoid division by zero)
        let maxRevenuePerView     = allStats.map { $0.revenuePerView }.max() ?? 1
        let maxRevenuePerDownload = allStats.map { $0.revenuePerDownload }.max() ?? 1
        let maxVelocity           = allStats.map { $0.velocity7d }.max() ?? 1

        var recs: [InvestmentRecommendation] = []

        for stats in allStats {
            let aes = aestheticsByProject[stats.project.id] ?? AestheticsScore.evaluate(stats.project)

            // Normalised scores 0–100
            // Revenue per view (40%): the single most important signal.
            // A mod with high $/view is attracting premium traffic — worth investing in.
            let revenuePerViewScore = maxRevenuePerView > 0
                ? min(stats.revenuePerView / maxRevenuePerView * 100, 100)
                : 0

            // Revenue per download (15%): secondary monetisation signal
            let revenuePerDlScore = maxRevenuePerDownload > 0
                ? min(stats.revenuePerDownload / maxRevenuePerDownload * 100, 100)
                : 0

            // Velocity (25%): current momentum — high velocity = audience is active
            let velocityScore = maxVelocity > 0
                ? min(stats.velocity7d / maxVelocity * 100, 100)
                : 0

            // Coverage gap (15%): missing versions = untapped audience
            let coverageGapScore = Double(stats.missingCount) / Double(max(manifestTargets.count, 1)) * 100

            // Aesthetics gap (5%): poor page = easy wins (lower weight — less impactful than revenue)
            let aestheticsGapScore = 100 - stats.aestheticsScore

            // Composite investment score
            // Weights: revenue/view 40%, velocity 25%, coverage gap 15%, revenue/dl 15%, aesthetics 5%
            let investScore = (revenuePerViewScore  * 0.40)
                            + (velocityScore         * 0.25)
                            + (coverageGapScore      * 0.15)
                            + (revenuePerDlScore     * 0.15)
                            + (aestheticsGapScore    * 0.05)

            // Build action list
            var actions: [String] = []
            if stats.missingCount > 0 {
                actions.append("Port to \(stats.missingCount) missing version\(stats.missingCount == 1 ? "" : "s") — more downloads = more revenue")
            }
            if stats.revenuePerView > 0 {
                actions.append(String(format: "High value: $%.5f/view — prioritise traffic growth", stats.revenuePerView))
            }
            for check in aes.missing.prefix(2) {
                actions.append("Add \(check.name.lowercased()) to improve discoverability")
            }
            if stats.metrics.downloadVelocity7d < stats.metrics.downloadVelocity30d * 0.7 {
                actions.append("Momentum declining — post an update or announcement")
            }

            // Primary reason — lead with the most impactful signal
            let reason: String
            if revenuePerViewScore > 70 {
                reason = String(format: "$%.5f/view — top earning mod, maximise its reach", stats.revenuePerView)
            } else if revenuePerViewScore > 30 {
                reason = String(format: "$%.5f/view — solid earner, more versions = more revenue", stats.revenuePerView)
            } else if velocityScore > 70 {
                reason = String(format: "%.0f dl/day momentum — capitalise with more versions", stats.velocity7d)
            } else if coverageGapScore > 50 {
                reason = "\(stats.missingCount) missing versions — large untapped audience"
            } else if aestheticsGapScore > 40 {
                reason = String(format: "Page quality %.0f%% — easy wins to boost discoverability", stats.aestheticsScore)
            } else {
                reason = "Balanced opportunity across revenue, versions, and page quality"
            }

            recs.append(InvestmentRecommendation(
                project: stats.project,
                score: investScore,
                reason: reason,
                actions: actions,
                missingVersions: stats.missingCount,
                aestheticsScore: stats.aestheticsScore,
                velocityScore: velocityScore,
                revenueScore: revenuePerViewScore,
                revenuePerView: stats.revenuePerView,
                revenuePerDownload: stats.revenuePerDownload
            ))
        }

        recommendations = recs.sorted { $0.score > $1.score }
    }

    // MARK: - Synthetic analytics fallback

    private func buildSyntheticAnalytics(for project: ModrinthProject) {
        let pts = syntheticPoints(for: project)
        analyticsPoints = pts
        buildMetrics(project: project, points: pts)
    }

    private func syntheticPoints(for project: ModrinthProject) -> [AnalyticsPoint] {
        guard let pub = project.publishedDate else { return [] }
        let days  = max(1, Int(Date().timeIntervalSince(pub) / 86400))
        let avgDl = Double(project.downloads) / Double(days)
        var pts: [AnalyticsPoint] = []
        for d in 0..<min(days, 730) {
            let date = pub.addingTimeInterval(Double(d) * 86400)
            pts.append(AnalyticsPoint(date: date, downloads: avgDl, views: avgDl * 2.5, revenue: 0))
        }
        return pts
    }

    private func buildMetrics(project: ModrinthProject, points: [AnalyticsPoint]) {
        // Sum revenue directly from API points — do NOT override with project.downloads.
        // Revenue is what Modrinth actually paid; it's independent of download count.
        let totalDl  = points.reduce(0) { $0 + $1.downloads }
        let totalV   = points.reduce(0) { $0 + $1.views }
        let totalRev = points.reduce(0) { $0 + $1.revenue }

        metrics = BusinessMetrics(
            // Use max(api_sum, project.downloads) for downloads only —
            // the API may return fewer downloads than the project total due to
            // processing delays, but revenue is always exact from the API.
            totalDownloads: max(totalDl, Double(project.downloads)),
            totalViews:     totalV,
            totalRevenue:   totalRev,   // exact from API, never synthesised
            dailyDownloads: points
        )
    }

    // MARK: - Coverage % for list row (fast, uses range resolver)

    func coveragePercent(for project: ModrinthProject) -> Double {
        guard manifestTargets.count > 0 else { return 0 }
        var matched = Set<ManifestTarget>()
        for mc in project.game_versions {
            for loader in project.loaders {
                if let t = ManifestLoader.resolveTarget(
                    publishedVersion: mc,
                    loader: loader,
                    manifestTargets: manifestTargets
                ) {
                    matched.insert(t)
                }
            }
        }
        return min(Double(matched.count) / Double(manifestTargets.count) * 100.0, 100.0)
    }
}

// MARK: - HistoryStore sync helper (called from MainActor)

extension HistoryStore {
    /// Synchronous load — safe to call from MainActor since it just reads from disk.
    nonisolated func loadSync(projectId: String) -> ProjectHistory? {
        let appSupport = FileManager.default.urls(
            for: .applicationSupportDirectory, in: .userDomainMask
        ).first!
        let dir = appSupport
            .appendingPathComponent("com.itamio.ModrinthDashboard")
            .appendingPathComponent("history")
        let file = dir.appendingPathComponent("\(projectId).json")
        guard let data = try? Data(contentsOf: file) else { return nil }
        return try? JSONDecoder().decode(ProjectHistory.self, from: data)
    }
}
