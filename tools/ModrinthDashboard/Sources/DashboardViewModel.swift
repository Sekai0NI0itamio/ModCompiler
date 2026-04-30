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

        // Collect all cached histories
        var allPoints: [Date: (downloads: Double, views: Double, revenue: Double)] = [:]
        let cal = Calendar(identifier: .gregorian)

        for project in projects {
            // Use cached history if available, else synthetic
            let pts: [AnalyticsPoint]
            if let history = HistoryStore.shared.loadSync(projectId: project.id),
               !history.points.isEmpty {
                pts = history.points
            } else {
                pts = syntheticPoints(for: project)
            }

            for pt in pts {
                let day = cal.startOfDay(for: pt.date)
                var existing = allPoints[day] ?? (0, 0, 0)
                existing.downloads += pt.downloads
                existing.views     += pt.views
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

        var recs: [InvestmentRecommendation] = []

        for project in projects {
            let cov = coverageByProject[project.id]
            let aes = aestheticsByProject[project.id] ?? AestheticsScore.evaluate(project)
            let missingCount = cov?.missing.count ?? (manifestTargets.count - project.game_versions.count)

            // Get cached analytics
            let pts: [AnalyticsPoint]
            if let history = HistoryStore.shared.loadSync(projectId: project.id),
               !history.points.isEmpty {
                pts = history.points
            } else {
                pts = syntheticPoints(for: project)
            }

            let m = BusinessMetrics(
                totalDownloads: Double(project.downloads),
                totalViews: pts.reduce(0) { $0 + $1.views },
                totalRevenue: pts.reduce(0) { $0 + $1.revenue },
                dailyDownloads: pts
            )

            // Velocity score: 7d velocity relative to best mod
            let velocityScore = bestModDownloads > 0
                ? min(m.downloadVelocity7d / bestModDownloads * 100, 100)
                : 0

            // Revenue score
            let revenueScore = m.revenuePerDownload * 10000  // scale to 0-100 range

            // Coverage gap score: higher = more room to grow
            let coverageGap = Double(missingCount) / Double(manifestTargets.count) * 100

            // Aesthetics gap
            let aestheticsGap = 100 - aes.score

            // Investment score = weighted combination of:
            // - Velocity (momentum): 35% — high velocity = worth investing
            // - Coverage gap: 30% — many missing versions = high opportunity
            // - Aesthetics gap: 20% — poor page = easy wins
            // - Revenue: 15% — monetised mods worth more investment
            let investScore = (velocityScore * 0.35)
                + (coverageGap * 0.30)
                + (aestheticsGap * 0.20)
                + (min(revenueScore, 100) * 0.15)

            // Build action list
            var actions: [String] = []
            if missingCount > 0 {
                actions.append("Port to \(missingCount) missing version\(missingCount == 1 ? "" : "s")")
            }
            for check in aes.missing.prefix(3) {
                actions.append("Add \(check.name.lowercased())")
            }
            if m.downloadVelocity7d < m.downloadVelocity30d * 0.7 {
                actions.append("Momentum declining — consider a new update or announcement")
            }

            // Primary reason
            let reason: String
            if coverageGap > 50 {
                reason = "Only \(Int(100 - coverageGap))% version coverage — large untapped audience"
            } else if velocityScore > 60 {
                reason = "Strong momentum (\(Int(m.downloadVelocity7d))/day) — capitalise now"
            } else if aestheticsGap > 40 {
                reason = "Page quality \(Int(aes.score))% — easy wins to boost discoverability"
            } else if m.revenuePerDownload > 0 {
                reason = "Monetised mod — more versions = more revenue"
            } else {
                reason = "Balanced opportunity across versions and page quality"
            }

            recs.append(InvestmentRecommendation(
                project: project,
                score: investScore,
                reason: reason,
                actions: actions,
                missingVersions: missingCount,
                aestheticsScore: aes.score,
                velocityScore: velocityScore,
                revenueScore: min(revenueScore, 100)
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
        metrics = BusinessMetrics(
            totalDownloads: max(points.reduce(0) { $0 + $1.downloads }, Double(project.downloads)),
            totalViews:     points.reduce(0) { $0 + $1.views },
            totalRevenue:   points.reduce(0) { $0 + $1.revenue },
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
