import Foundation
import SwiftUI

@MainActor
final class DashboardViewModel: ObservableObject {
    // MARK: - Published state
    @Published var projects: [ModrinthProject] = []
    @Published var selectedProject: ModrinthProject?
    @Published var coverage: CoverageInfo?
    @Published var metrics: BusinessMetrics?
    @Published var analyticsPoints: [AnalyticsPoint] = []
    @Published var isLoadingList = false
    @Published var isLoadingDetail = false
    @Published var errorMessage: String?
    @Published var username: String = "Itamio"
    @Published var apiToken: String = ""
    @Published var showSettings = false

    // Best-mod reference for relative scoring
    @Published var bestModDownloads: Double = 1
    @Published var avgModDownloads: Double = 1

    // Manifest targets (loaded once)
    let manifestTargets: Set<ManifestTarget> = ManifestLoader.loadTargets()

    // MARK: - Load project list

    func loadProjects() async {
        isLoadingList = true
        errorMessage = nil
        await ModrinthAPI.shared.setToken(apiToken)
        do {
            let fetched = try await ModrinthAPI.shared.fetchUserProjects(username: username)
            projects = fetched.sorted { $0.downloads > $1.downloads }
            // Compute portfolio averages for relative scoring
            let dl = projects.map { Double($0.downloads) }
            avgModDownloads = dl.isEmpty ? 1 : dl.reduce(0, +) / Double(dl.count)
            bestModDownloads = dl.max() ?? 1
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoadingList = false
    }

    // MARK: - Load detail for selected project

    func loadDetail(for project: ModrinthProject) async {
        selectedProject = project
        isLoadingDetail = true
        coverage = nil
        metrics = nil
        analyticsPoints = []

        await withTaskGroup(of: Void.self) { group in
            group.addTask { await self.loadCoverage(for: project) }
            group.addTask { await self.loadAnalytics(for: project) }
        }
        isLoadingDetail = false
    }

    // MARK: - Coverage calculation

    private func loadCoverage(for project: ModrinthProject) async {
        do {
            let versions = try await ModrinthAPI.shared.fetchVersions(projectId: project.id)

            var supported = Set<ManifestTarget>()
            var ghost     = Set<ManifestTarget>()

            for v in versions {
                let isListed = v.status == "listed"
                for mc in v.game_versions {
                    for loader in v.loaders {
                        let t = ManifestTarget(version: mc, loader: loader)
                        if manifestTargets.contains(t) {
                            if isListed { supported.insert(t) }
                            else        { ghost.insert(t) }
                        }
                    }
                }
            }
            // Remove from ghost anything that's also supported (listed version wins)
            let ghostOnly = ghost.subtracting(supported)
            let missing   = manifestTargets.subtracting(supported).subtracting(ghostOnly)

            coverage = CoverageInfo(
                supported: supported,
                ghost: ghostOnly,
                missing: missing,
                total: manifestTargets.count
            )
        } catch {
            // Non-fatal — coverage just won't show
        }
    }

    // MARK: - Analytics

    private func loadAnalytics(for project: ModrinthProject) async {
        guard !apiToken.isEmpty else {
            // Without token: build synthetic daily points from total downloads
            // spread across days since published
            if let pub = project.publishedDate {
                let days = max(1, Int(Date().timeIntervalSince(pub) / 86400))
                let avgDl = Double(project.downloads) / Double(days)
                var pts: [AnalyticsPoint] = []
                for d in 0..<min(days, 365) {
                    let date = pub.addingTimeInterval(Double(d) * 86400)
                    pts.append(AnalyticsPoint(date: date, downloads: avgDl, views: avgDl * 2.5, revenue: 0))
                }
                analyticsPoints = pts
                buildMetrics(project: project, points: pts)
            }
            return
        }

        do {
            let end   = Date()
            let start: Date
            if let pub = project.publishedDate {
                start = pub
            } else {
                start = end.addingTimeInterval(-365 * 86400)
            }
            // Max 1024 slices — use daily slices up to 1024 days
            let days = max(1, Int(end.timeIntervalSince(start) / 86400))
            let slices = min(days, 1024)

            let pts = try await ModrinthAPI.shared.fetchAnalytics(
                projectIds: [project.id],
                start: start,
                end: end,
                slices: slices
            )
            analyticsPoints = pts
            buildMetrics(project: project, points: pts)
        } catch {
            // Fall back to synthetic
            if let pub = project.publishedDate {
                let days = max(1, Int(Date().timeIntervalSince(pub) / 86400))
                let avgDl = Double(project.downloads) / Double(days)
                var pts: [AnalyticsPoint] = []
                for d in 0..<min(days, 365) {
                    let date = pub.addingTimeInterval(Double(d) * 86400)
                    pts.append(AnalyticsPoint(date: date, downloads: avgDl, views: avgDl * 2.5, revenue: 0))
                }
                analyticsPoints = pts
                buildMetrics(project: project, points: pts)
            }
        }
    }

    private func buildMetrics(project: ModrinthProject, points: [AnalyticsPoint]) {
        let totalDl  = points.reduce(0) { $0 + $1.downloads }
        let totalV   = points.reduce(0) { $0 + $1.views }
        let totalRev = points.reduce(0) { $0 + $1.revenue }
        metrics = BusinessMetrics(
            totalDownloads: max(totalDl, Double(project.downloads)),
            totalViews: totalV,
            totalRevenue: totalRev,
            dailyDownloads: points
        )
    }

    // MARK: - Coverage % for list row

    func coveragePercent(for project: ModrinthProject) -> Double {
        let supported = Set(
            project.game_versions.flatMap { v in
                project.loaders.map { ManifestTarget(version: v, loader: $0) }
            }
        ).intersection(manifestTargets)
        guard manifestTargets.count > 0 else { return 0 }
        return Double(supported.count) / Double(manifestTargets.count) * 100.0
    }
}

// Helper to set token on actor
extension ModrinthAPI {
    func setToken(_ t: String) {
        token = t
    }
}
