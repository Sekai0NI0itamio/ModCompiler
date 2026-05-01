import Foundation

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
        // Try current format first; fall back to legacy (with revenue field) by stripping it
        if let history = try? JSONDecoder().decode(ProjectHistory.self, from: data) {
            return history
        }
        // Legacy cache had revenue field — migrate by re-parsing manually
        return migrateLegacy(data: data, projectId: projectId)
    }

    private func migrateLegacy(data: Data, projectId: String) -> ProjectHistory? {
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let pointsRaw = json["points"] as? [[String: Any]] else { return nil }

        let df = ISO8601DateFormatter()
        var points: [AnalyticsPoint] = []
        for dict in pointsRaw {
            // Parse date — stored as ISO8601 string or TimeInterval
            let date: Date
            if let dateStr = dict["date"] as? String, let d = df.date(from: dateStr) {
                date = d
            } else if let ts = dict["date"] as? Double {
                date = Date(timeIntervalSinceReferenceDate: ts)
            } else { continue }

            let downloads = doubleFrom(dict["downloads"])
            let views     = doubleFrom(dict["views"])
            // revenue field intentionally dropped
            points.append(AnalyticsPoint(date: date, downloads: downloads, views: views))
        }

        guard !points.isEmpty else { return nil }

        let lastFetched = (json["lastFetched"] as? Double).map { Date(timeIntervalSinceReferenceDate: $0) } ?? Date()
        let historyThrough = (json["historyThrough"] as? Double).map { Date(timeIntervalSinceReferenceDate: $0) } ?? Date()

        let history = ProjectHistory(
            projectId: projectId,
            points: points.sorted { $0.date < $1.date },
            lastFetched: lastFetched,
            historyThrough: historyThrough
        )
        // Re-save in new format
        save(history)
        return history
    }

    private func doubleFrom(_ val: Any?) -> Double {
        switch val {
        case let d as Double: return d
        case let i as Int:    return Double(i)
        case let s as String: return Double(s) ?? 0
        default:              return 0
        }
    }

    // MARK: - Save

    func save(_ history: ProjectHistory) {
        let file = dir.appendingPathComponent("\(history.projectId).json")
        if let data = try? JSONEncoder().encode(history) {
            try? data.write(to: file, options: .atomic)
        }
    }

    // MARK: - Merge

    func merge(projectId: String, newPoints: [AnalyticsPoint], fetchedThrough: Date) -> ProjectHistory {
        var existing = load(projectId: projectId) ?? ProjectHistory(
            projectId: projectId,
            points: [],
            lastFetched: Date(),
            historyThrough: fetchedThrough
        )

        var byDay: [String: AnalyticsPoint] = [:]
        for pt in existing.points { byDay[dayKey(pt.date)] = pt }
        for pt in newPoints        { byDay[dayKey(pt.date)] = pt }

        existing.points = byDay.values.sorted { $0.date < $1.date }
        existing.lastFetched = Date()
        existing.historyThrough = max(existing.historyThrough, fetchedThrough)

        save(existing)
        return existing
    }

    // MARK: - Delete

    func delete(projectId: String) {
        try? FileManager.default.removeItem(at: dir.appendingPathComponent("\(projectId).json"))
    }

    func deleteAll() {
        let files = (try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil)) ?? []
        for f in files where f.pathExtension == "json" {
            try? FileManager.default.removeItem(at: f)
        }
    }

    // MARK: - Fetch range

    func fetchRange(for projectId: String, publishedDate: Date?) -> (start: Date, end: Date, isIncremental: Bool) {
        let now = Date()
        let cal = Calendar(identifier: .gregorian)

        if let history = load(projectId: projectId), !history.points.isEmpty {
            let incrementalStart = max(
                history.historyThrough.addingTimeInterval(-2 * 86400),
                publishedDate ?? now.addingTimeInterval(-365 * 86400)
            )
            return (start: incrementalStart, end: now, isIncremental: true)
        } else {
            let start = publishedDate ?? now.addingTimeInterval(-365 * 86400)
            return (start: start, end: now, isIncremental: false)
        }
    }

    // MARK: - Helpers

    private func dayKey(_ date: Date) -> String {
        var comps = Calendar(identifier: .gregorian)
            .dateComponents(in: TimeZone(identifier: "UTC")!, from: date)
        comps.hour = 0; comps.minute = 0; comps.second = 0; comps.nanosecond = 0
        return "\(comps.year!)-\(comps.month!)-\(comps.day!)"
    }
}
