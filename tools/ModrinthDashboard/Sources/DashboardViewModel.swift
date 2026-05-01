import Foundation
import SwiftUI

enum ProjectSelection: Equatable {
    case allProjects
    case project(String)
}

@MainActor
final class DashboardViewModel: ObservableObject {

    @Published var projects: [ModrinthProject] = []
    @Published var selection: ProjectSelection? = nil
    @Published var coverage: CoverageInfo?
    @Published var aesthetics: AestheticsScore?
    @Published var metrics: BusinessMetrics?
    @Published var analyticsPoints: [AnalyticsPoint] = []
    @Published var isLoadingList    = false
    @Published var isLoadingDetail  = false
    @Published var isLoadingHistory = false
    @Published var historyProgress: Double = 0
    @Published var errorMessage: String?
    @Published var detailError: String?
    @Published var showSettings = false

    @Published var portfolioMetrics: BusinessMetrics?
    @Published var portfolioPoints: [AnalyticsPoint] = []
    @Published var recommendations: [InvestmentRecommendation] = []
    @Published var coverageByProject: [String: CoverageInfo] = [:]
    @Published var aestheticsByProject: [String: AestheticsScore] = [:]

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
            var fetched = try await ModrinthAPI.shared.fetchUserProjects(username: settings.username)
            fetched = await withTaskGroup(of: ModrinthProject?.self) { group in
                for p in fetched {
                    group.addTask { (try? await ModrinthAPI.shared.fetchProject(id: p.id)) ?? p }
                }
                var result: [ModrinthProject] = []
                for await p in group { if let p { result.append(p) } }
                return result
            }
            projects = fetched.filter { $0.isPublic }.sorted { $0.downloads > $1.downloads }

            let dl = projects.map { Double($0.downloads) }
            avgModDownloads  = dl.isEmpty ? 1 : dl.reduce(0, +) / Double(dl.count)
            bestModDownloads = dl.max() ?? 1

            for p in projects {
                aestheticsByProject[p.id] = AestheticsScore.evaluate(p)
            }
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoadingList = false
    }

    // MARK: - History download

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
        buildPortfolioAnalytics()
        buildRecommendations()
        isLoadingHistory = false
    }

    func downloadHistory(for project: ModrinthProject, forceFullRefetch: Bool) async {
        guard !settings.apiToken.isEmpty else { return }
        await ModrinthAPI.shared.setToken(settings.apiToken)

        if forceFullRefetch { await HistoryStore.shared.delete(projectId: project.id) }

        let (start, end, _) = await HistoryStore.shared.fetchRange(
            for: project.id, publishedDate: project.publishedDate)

        let days   = max(1, Int(end.timeIntervalSince(start) / 86400))
        let slices = min(days, 1024)

        do {
            let pts = try await ModrinthAPI.shared.fetchAnalytics(
                projectIds: [project.id], start: start, end: end, slices: slices)

            let cal = Calendar(identifier: .gregorian)
            let historyThrough = cal.date(byAdding: .day, value: -2,
                                          to: cal.startOfDay(for: Date()))!
            let history = await HistoryStore.shared.merge(
                projectId: project.id, newPoints: pts, fetchedThrough: historyThrough)

            if case .project(let id) = selection, id == project.id {
                analyticsPoints = history.points
                buildMetrics(project: project, points: history.points)
            }
        } catch {
            if case .project(let id) = selection, id == project.id {
                buildSyntheticAnalytics(for: project)
            }
        }
    }

    func refreshAllHistory() async { await downloadAllHistory() }

    // MARK: - Load detail

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

    func loadAllProjects() async {
        selection       = .allProjects
        isLoadingDetail = true
        coverage        = nil
        metrics         = nil
        analyticsPoints = []
        detailError     = nil

        buildPortfolioAnalytics()
        buildRecommendations()

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
        } catch {}
    }

    private func computeCoverage(versions: [ModrinthVersion]) -> CoverageInfo {
        var supported = Set<ManifestTarget>()
        var ghost      = Set<ManifestTarget>()
        for v in versions {
            let isListed = (v.status == "listed")
            for mc in v.game_versions {
                for loader in v.loaders {
                    guard let target = ManifestLoader.resolveTarget(
                        publishedVersion: mc, loader: loader, manifestTargets: manifestTargets
                    ) else { continue }
                    if isListed { supported.insert(target) } else { ghost.insert(target) }
                }
            }
        }
        let ghostOnly = ghost.subtracting(supported)
        let missing   = manifestTargets.subtracting(supported).subtracting(ghostOnly)
        return CoverageInfo(supported: supported, ghost: ghostOnly, missing: missing, total: manifestTargets.count)
    }

    // MARK: - Analytics for detail

    private func loadAnalyticsForDetail(for project: ModrinthProject) async {
        if let history = await HistoryStore.shared.load(projectId: project.id),
           !history.points.isEmpty {
            analyticsPoints = history.points
            buildMetrics(project: project, points: history.points)
            if !settings.apiToken.isEmpty {
                Task { await downloadHistory(for: project, forceFullRefetch: false) }
            }
            return
        }
        if !settings.apiToken.isEmpty {
            await downloadHistory(for: project, forceFullRefetch: false)
        } else {
            buildSyntheticAnalytics(for: project)
        }
    }

    // MARK: - Portfolio analytics

    func buildPortfolioAnalytics() {
        guard !projects.isEmpty else { return }
        var allPoints: [Date: (downloads: Double, views: Double)] = [:]
        let cal = Calendar(identifier: .gregorian)

        for project in projects {
            let pts: [AnalyticsPoint]
            if let h = HistoryStore.shared.loadSync(projectId: project.id), !h.points.isEmpty {
                pts = h.points
            } else {
                pts = syntheticPoints(for: project)
            }
            for pt in pts {
                let day = cal.startOfDay(for: pt.date)
                var e = allPoints[day] ?? (0, 0)
                e.downloads += pt.downloads
                e.views     += pt.views
                allPoints[day] = e
            }
        }

        let combined = allPoints.map { (day, vals) in
            AnalyticsPoint(date: day, downloads: vals.downloads, views: vals.views)
        }.sorted { $0.date < $1.date }

        portfolioPoints = combined
        let totalDl = max(combined.reduce(0) { $0 + $1.downloads },
                          projects.reduce(0) { $0 + Double($1.downloads) })
        let totalV  = combined.reduce(0) { $0 + $1.views }
        portfolioMetrics = BusinessMetrics(totalDownloads: totalDl, totalViews: totalV, dailyDownloads: combined)
    }

    // MARK: - Investment recommendations
    // Primary signal: view-to-download conversion rate.
    // A high conversion rate means the mod page convinces visitors to download —
    // this is the best proxy for "quality" and "profit potential" without revenue data.

    func buildRecommendations() {
        let scorable = projects.filter { $0.status == "approved" }
        guard !scorable.isEmpty else { return }

        struct ModStats {
            let project: ModrinthProject
            let missingCount: Int
            let aestheticsScore: Double
            let conversionRate: Double   // % views → downloads (lifetime)
            let recentConversion: Double // % views → downloads (last 30d)
            let velocity7d: Double
            let velocity30d: Double
            let totalDownloads: Double
            let totalViews: Double
        }

        var allStats: [ModStats] = []

        for project in scorable {
            let cov = coverageByProject[project.id]
            let aes = aestheticsByProject[project.id] ?? AestheticsScore.evaluate(project)
            let missingCount = cov?.missing.count ?? max(0, manifestTargets.count - project.game_versions.count)

            let history = HistoryStore.shared.loadSync(projectId: project.id)
            let pts: [AnalyticsPoint] = (!(history?.points.isEmpty ?? true))
                ? (history?.points ?? [])
                : syntheticPoints(for: project)

            let totalDl = max(pts.reduce(0) { $0 + $1.downloads }, Double(project.downloads))
            let totalV  = pts.reduce(0) { $0 + $1.views }

            let conversionRate   = totalV > 0 ? totalDl / totalV * 100.0 : 0
            let recent30dl = pts.suffix(30).reduce(0) { $0 + $1.downloads }
            let recent30v  = pts.suffix(30).reduce(0) { $0 + $1.views }
            let recentConversion = recent30v > 0 ? recent30dl / recent30v * 100.0 : 0

            let velocity7d  = pts.suffix(7).reduce(0)  { $0 + $1.downloads } / max(Double(min(pts.count, 7)), 1)
            let velocity30d = pts.suffix(30).reduce(0) { $0 + $1.downloads } / max(Double(min(pts.count, 30)), 1)

            allStats.append(ModStats(
                project: project,
                missingCount: missingCount,
                aestheticsScore: aes.score,
                conversionRate: conversionRate,
                recentConversion: recentConversion,
                velocity7d: velocity7d,
                velocity30d: velocity30d,
                totalDownloads: totalDl,
                totalViews: totalV
            ))
        }

        // Portfolio maximums for normalisation
        let maxConversion = allStats.map { $0.conversionRate }.max() ?? 1
        let maxVelocity   = allStats.map { $0.velocity7d }.max() ?? 1

        var recs: [InvestmentRecommendation] = []

        for stats in allStats {
            let aes = aestheticsByProject[stats.project.id] ?? AestheticsScore.evaluate(stats.project)

            // Conversion score (40%): primary signal
            // High conversion = compelling page + engaged audience = worth investing in
            let conversionScore = maxConversion > 0
                ? min(stats.conversionRate / maxConversion * 100, 100)
                : 0

            // Velocity score (30%): current momentum
            let velocityScore = maxVelocity > 0
                ? min(stats.velocity7d / maxVelocity * 100, 100)
                : 0

            // Coverage gap (20%): missing versions = untapped audience
            let coverageGapScore = Double(stats.missingCount) / Double(max(manifestTargets.count, 1)) * 100

            // Aesthetics gap (10%): poor page = easy wins
            let aestheticsGapScore = 100 - stats.aestheticsScore

            let investScore = (conversionScore    * 0.40)
                            + (velocityScore       * 0.30)
                            + (coverageGapScore    * 0.20)
                            + (aestheticsGapScore  * 0.10)

            // Actions
            var actions: [String] = []
            if stats.missingCount > 0 {
                actions.append("Port to \(stats.missingCount) missing version\(stats.missingCount == 1 ? "" : "s") — more reach")
            }
            if stats.conversionRate > 20 {
                actions.append(String(format: "%.1f%% conversion — high-quality audience, grow it with more versions", stats.conversionRate))
            } else if stats.conversionRate < 5 && stats.totalViews > 100 {
                actions.append(String(format: "%.1f%% conversion is low — improve page quality to convert more visitors", stats.conversionRate))
            }
            for check in aes.missing.prefix(2) {
                actions.append("Add \(check.name.lowercased()) to improve discoverability")
            }
            if stats.velocity7d < stats.velocity30d * 0.7 && stats.velocity30d > 0 {
                actions.append("Momentum declining — post an update or announcement")
            }

            // Primary reason
            let reason: String
            if conversionScore > 70 {
                reason = String(format: "%.1f%% conversion rate — top performer, maximise its reach", stats.conversionRate)
            } else if conversionScore > 40 {
                reason = String(format: "%.1f%% conversion — solid mod, more versions = more downloads", stats.conversionRate)
            } else if velocityScore > 70 {
                reason = String(format: "%.0f dl/day momentum — capitalise with more versions", stats.velocity7d)
            } else if coverageGapScore > 50 {
                reason = "\(stats.missingCount) missing versions — large untapped audience"
            } else if aestheticsGapScore > 40 {
                reason = String(format: "Page quality %.0f%% — easy wins to boost discoverability", stats.aestheticsScore)
            } else {
                reason = "Balanced opportunity across conversion, versions, and page quality"
            }

            recs.append(InvestmentRecommendation(
                project: stats.project,
                score: investScore,
                reason: reason,
                actions: actions,
                missingVersions: stats.missingCount,
                aestheticsScore: stats.aestheticsScore,
                conversionRate: stats.conversionRate,
                conversionScore: conversionScore,
                velocityScore: velocityScore,
                coverageGapScore: coverageGapScore
            ))
        }

        recommendations = recs.sorted { $0.score > $1.score }
    }

    // MARK: - Synthetic fallback

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
            pts.append(AnalyticsPoint(date: date, downloads: avgDl, views: avgDl * 2.5))
        }
        return pts
    }

    private func buildMetrics(project: ModrinthProject, points: [AnalyticsPoint]) {
        let totalDl = max(points.reduce(0) { $0 + $1.downloads }, Double(project.downloads))
        let totalV  = points.reduce(0) { $0 + $1.views }
        metrics = BusinessMetrics(totalDownloads: totalDl, totalViews: totalV, dailyDownloads: points)
    }

    // MARK: - Coverage % for list row

    func coveragePercent(for project: ModrinthProject) -> Double {
        guard manifestTargets.count > 0 else { return 0 }
        var matched = Set<ManifestTarget>()
        for mc in project.game_versions {
            for loader in project.loaders {
                if let t = ManifestLoader.resolveTarget(
                    publishedVersion: mc, loader: loader, manifestTargets: manifestTargets) {
                    matched.insert(t)
                }
            }
        }
        return min(Double(matched.count) / Double(manifestTargets.count) * 100.0, 100.0)
    }
}

extension HistoryStore {
    nonisolated func loadSync(projectId: String) -> ProjectHistory? {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)
            .first!
            .appendingPathComponent("com.itamio.ModrinthDashboard")
            .appendingPathComponent("history")
        let file = dir.appendingPathComponent("\(projectId).json")
        guard let data = try? Data(contentsOf: file) else { return nil }
        return try? JSONDecoder().decode(ProjectHistory.self, from: data)
    }
}
