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
    @Published var isLoadingList   = false
    @Published var isLoadingDetail = false
    @Published var errorMessage: String?
    @Published var detailError: String?
    @Published var showSettings = false

    // Portfolio-level stats for relative KPI scoring
    @Published var bestModDownloads: Double = 1
    @Published var avgModDownloads:  Double = 1

    // Manifest targets — loaded once at startup
    let manifestTargets: Set<ManifestTarget> = ManifestLoader.loadTargets()

    // Settings (persisted)
    let settings = AppSettings.shared

    // MARK: - Load project list

    func loadProjects() async {
        isLoadingList = true
        errorMessage  = nil

        // Push current token to the API actor
        let tok = settings.apiToken
        let usr = settings.username
        await ModrinthAPI.shared.setToken(tok)

        do {
            let fetched = try await ModrinthAPI.shared.fetchUserProjects(username: usr)
            projects = fetched.sorted { $0.downloads > $1.downloads }

            let dl = projects.map { Double($0.downloads) }
            avgModDownloads  = dl.isEmpty ? 1 : dl.reduce(0, +) / Double(dl.count)
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
        coverage        = nil
        metrics         = nil
        analyticsPoints = []
        detailError     = nil

        // Ensure token is current before detail calls
        await ModrinthAPI.shared.setToken(settings.apiToken)

        await withTaskGroup(of: Void.self) { group in
            group.addTask { await self.loadCoverage(for: project) }
            group.addTask { await self.loadAnalytics(for: project) }
        }
        isLoadingDetail = false
    }

    // MARK: - Coverage

    private func loadCoverage(for project: ModrinthProject) async {
        do {
            let versions = try await ModrinthAPI.shared.fetchVersions(projectId: project.id)

            var supported = Set<ManifestTarget>()
            var ghost      = Set<ManifestTarget>()

            for v in versions {
                let isListed = (v.status == "listed")
                for mc in v.game_versions {
                    for loader in v.loaders {
                        let t = ManifestTarget(version: mc, loader: loader)
                        guard manifestTargets.contains(t) else { continue }
                        if isListed { supported.insert(t) }
                        else        { ghost.insert(t) }
                    }
                }
            }

            // A target that has both a listed and an archived version → count as supported
            let ghostOnly = ghost.subtracting(supported)
            let missing   = manifestTargets.subtracting(supported).subtracting(ghostOnly)

            coverage = CoverageInfo(
                supported: supported,
                ghost:     ghostOnly,
                missing:   missing,
                total:     manifestTargets.count
            )
        } catch {
            // Non-fatal — show partial UI
        }
    }

    // MARK: - Analytics

    private func loadAnalytics(for project: ModrinthProject) async {
        let hasToken = !settings.apiToken.isEmpty

        if hasToken {
            await loadRealAnalytics(for: project)
        } else {
            buildSyntheticAnalytics(for: project)
        }
    }

    private func loadRealAnalytics(for project: ModrinthProject) async {
        let end   = Date()
        let start: Date
        if let pub = project.publishedDate {
            start = pub
        } else {
            // Fall back to 1 year ago
            start = end.addingTimeInterval(-365 * 86400)
        }

        let days   = max(1, Int(end.timeIntervalSince(start) / 86400))
        let slices = min(days, 1024)

        do {
            let pts = try await ModrinthAPI.shared.fetchAnalytics(
                projectIds: [project.id],
                start: start,
                end:   end,
                slices: slices
            )
            if pts.isEmpty {
                // API returned empty — fall back to synthetic
                buildSyntheticAnalytics(for: project)
            } else {
                analyticsPoints = pts
                buildMetrics(project: project, points: pts)
            }
        } catch APIError.noToken {
            buildSyntheticAnalytics(for: project)
        } catch APIError.unauthorized {
            detailError = "Analytics: invalid token. Check Settings."
            buildSyntheticAnalytics(for: project)
        } catch APIError.forbidden {
            detailError = "Analytics: token missing ANALYTICS_READ or PAYOUTS_READ scope."
            buildSyntheticAnalytics(for: project)
        } catch {
            detailError = "Analytics: \(error.localizedDescription)"
            buildSyntheticAnalytics(for: project)
        }
    }

    /// Synthetic analytics: spread total downloads evenly across the mod's lifetime.
    /// Used when no token is set or analytics API fails.
    private func buildSyntheticAnalytics(for project: ModrinthProject) {
        guard let pub = project.publishedDate else { return }
        let days   = max(1, Int(Date().timeIntervalSince(pub) / 86400))
        let avgDl  = Double(project.downloads) / Double(days)
        let avgV   = avgDl * 2.5   // rough views estimate

        var pts: [AnalyticsPoint] = []
        for d in 0..<min(days, 730) {
            let date = pub.addingTimeInterval(Double(d) * 86400)
            pts.append(AnalyticsPoint(date: date, downloads: avgDl, views: avgV, revenue: 0))
        }
        analyticsPoints = pts
        buildMetrics(project: project, points: pts)
    }

    private func buildMetrics(project: ModrinthProject, points: [AnalyticsPoint]) {
        let totalDl  = points.reduce(0) { $0 + $1.downloads }
        let totalV   = points.reduce(0) { $0 + $1.views }
        let totalRev = points.reduce(0) { $0 + $1.revenue }

        metrics = BusinessMetrics(
            totalDownloads: max(totalDl, Double(project.downloads)),
            totalViews:     totalV,
            totalRevenue:   totalRev,
            dailyDownloads: points
        )
    }

    // MARK: - Coverage % for list row (fast estimate, no API call)
    // Uses the project's game_versions × loaders cross-product as an upper bound.
    // The detail view uses actual per-version data for precise counts.
    func coveragePercent(for project: ModrinthProject) -> Double {
        guard manifestTargets.count > 0 else { return 0 }
        // Build the set of (version, loader) pairs this project claims to support
        var count = 0
        for mc in project.game_versions {
            for loader in project.loaders {
                if manifestTargets.contains(ManifestTarget(version: mc, loader: loader)) {
                    count += 1
                }
            }
        }
        // Cap at 100% — cross-join can overcount vs actual per-version data
        return min(Double(count) / Double(manifestTargets.count) * 100.0, 100.0)
    }
}

// MARK: - Actor helper

extension ModrinthAPI {
    func setToken(_ t: String) {
        token = t
    }
}
