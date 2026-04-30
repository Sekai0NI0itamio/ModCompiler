import Foundation

// MARK: - Local analytics history cache
// Stores per-project daily analytics to disk so we only need to fetch
// recent days (today + yesterday) on subsequent refreshes.
//
// Storage location: ~/Library/Application Support/com.itamio.ModrinthDashboard/history/
// One JSON file per project: {projectId}.json

actor HistoryStore {
    static let shared = HistoryStore()

    private let dir: URL

    private init() {
        let appSupport = FileManager.default.urls(
            for: .applicationSupportDirectory, in: .userDomainMask
        ).first!
        dir = appSupport
            .appendingPathComponent("com.itamio.ModrinthDashboard")
            .appendingPathComponent("history")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
    }

    // MARK: - Load

    func load(projectId: String) -> ProjectHistory? {
        let file = dir.appendingPathComponent("\(projectId).json")
        guard let data = try? Data(contentsOf: file) else { return nil }
        return try? JSONDecoder().decode(ProjectHistory.self, from: data)
    }

    // MARK: - Save

    func save(_ history: ProjectHistory) {
        let file = dir.appendingPathComponent("\(history.projectId).json")
        if let data = try? JSONEncoder().encode(history) {
            try? data.write(to: file, options: .atomic)
        }
    }

    // MARK: - Merge new points into existing history

    /// Merges freshly-fetched points into stored history.
    /// Points are keyed by day (midnight UTC). Newer points overwrite older ones for the same day.
    func merge(projectId: String, newPoints: [AnalyticsPoint], fetchedThrough: Date) -> ProjectHistory {
        var existing = load(projectId: projectId) ?? ProjectHistory(
            projectId: projectId,
            points: [],
            lastFetched: Date(),
            historyThrough: fetchedThrough
        )

        // Build a day-keyed dict from existing points
        var byDay: [String: AnalyticsPoint] = [:]
        for pt in existing.points {
            byDay[dayKey(pt.date)] = pt
        }
        // Overwrite with new points
        for pt in newPoints {
            byDay[dayKey(pt.date)] = pt
        }

        // Sort by date
        let merged = byDay.values.sorted { $0.date < $1.date }
        existing.points = merged
        existing.lastFetched = Date()
        existing.historyThrough = max(existing.historyThrough, fetchedThrough)

        save(existing)
        return existing
    }

    // MARK: - Delete (force full re-fetch)

    func delete(projectId: String) {
        let file = dir.appendingPathComponent("\(projectId).json")
        try? FileManager.default.removeItem(at: file)
    }

    func deleteAll() {
        let files = (try? FileManager.default.contentsOfDirectory(
            at: dir, includingPropertiesForKeys: nil
        )) ?? []
        for f in files where f.pathExtension == "json" {
            try? FileManager.default.removeItem(at: f)
        }
    }

    // MARK: - Helpers

    private func dayKey(_ date: Date) -> String {
        let cal = Calendar(identifier: .gregorian)
        var comps = cal.dateComponents(in: TimeZone(identifier: "UTC")!, from: date)
        comps.hour = 0; comps.minute = 0; comps.second = 0; comps.nanosecond = 0
        return "\(comps.year!)-\(comps.month!)-\(comps.day!)"
    }

    /// Returns the date range we need to fetch to bring history up to date.
    /// "History" = everything up to and including day-before-yesterday (complete days).
    /// "Recent" = yesterday + today (may still be accumulating).
    func fetchRange(for projectId: String, publishedDate: Date?) -> (start: Date, end: Date, isIncremental: Bool) {
        let now = Date()
        let cal = Calendar(identifier: .gregorian)
        let utc = TimeZone(identifier: "UTC")!

        // Day-before-yesterday midnight UTC = the last "complete" day
        let dayBeforeYesterday = cal.date(
            byAdding: .day, value: -2,
            to: cal.startOfDay(for: now)
        )!

        if let history = load(projectId: projectId), !history.points.isEmpty {
            // Incremental: fetch from historyThrough to now
            // We re-fetch the last 2 days to catch any late-arriving data
            let incrementalStart = max(
                history.historyThrough.addingTimeInterval(-2 * 86400),
                publishedDate ?? now.addingTimeInterval(-365 * 86400)
            )
            return (start: incrementalStart, end: now, isIncremental: true)
        } else {
            // Full fetch from published date
            let start = publishedDate ?? now.addingTimeInterval(-365 * 86400)
            return (start: start, end: now, isIncremental: false)
        }
    }
}
