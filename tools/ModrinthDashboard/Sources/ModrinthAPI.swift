import Foundation

// MARK: - API Client

actor ModrinthAPI {
    static let shared = ModrinthAPI()
    private let base = "https://api.modrinth.com"
    private let v2   = "https://api.modrinth.com/v2"
    private let v3   = "https://api.modrinth.com/v3"
    private let userAgent = "itamio/ModrinthDashboard/1.0 (contact@itamio.com)"

    var token: String = ""

    // MARK: - User Projects

    func fetchUserProjects(username: String) async throws -> [ModrinthProject] {
        let url = URL(string: "\(v2)/user/\(username)/projects")!
        let data = try await get(url: url)
        return try JSONDecoder().decode([ModrinthProject].self, from: data)
    }

    // MARK: - Project Versions (for ghost detection)

    func fetchVersions(projectId: String) async throws -> [ModrinthVersion] {
        let url = URL(string: "\(v2)/project/\(projectId)/version")!
        let data = try await get(url: url)
        return try JSONDecoder().decode([ModrinthVersion].self, from: data)
    }

    // MARK: - Analytics (v3, requires token)

    func fetchAnalytics(
        projectIds: [String],
        start: Date,
        end: Date,
        slices: Int
    ) async throws -> [AnalyticsPoint] {
        guard !token.isEmpty else { throw APIError.noToken }

        let fmt = ISO8601DateFormatter()
        fmt.formatOptions = [.withInternetDateTime]

        let body: [String: Any] = [
            "time_range": [
                "start": fmt.string(from: start),
                "end":   fmt.string(from: end),
                "resolution": ["slices": slices]
            ],
            "return_metrics": [
                "project_views": ["bucket_by": ["project_id"]],
                "project_downloads": ["bucket_by": ["project_id"]],
                "project_revenue": ["bucket_by": ["project_id"]]
            ]
        ]

        let url = URL(string: "\(v3)/analytics")!
        let data = try await post(url: url, body: body)

        // Response: array of time slices, each slice is array of data points
        guard let sliceArray = try JSONSerialization.jsonObject(with: data) as? [[Any]] else {
            throw APIError.parseError("analytics response not array of arrays")
        }

        // Build one point per slice, summing across all projects
        let sliceCount = sliceArray.count
        guard sliceCount > 0 else { return [] }

        // Compute date for each slice
        let totalSeconds = end.timeIntervalSince(start)
        let sliceDuration = totalSeconds / Double(sliceCount)

        var points: [AnalyticsPoint] = []
        for (i, slice) in sliceArray.enumerated() {
            let sliceDate = start.addingTimeInterval(Double(i) * sliceDuration + sliceDuration / 2)
            var downloads = 0.0
            var views = 0.0
            var revenue = 0.0

            for item in slice {
                guard let dict = item as? [String: Any] else { continue }
                let kind = dict["metric_kind"] as? String ?? ""
                switch kind {
                case "downloads":
                    downloads += (dict["downloads"] as? Double) ?? Double(dict["downloads"] as? Int ?? 0)
                case "views":
                    views += (dict["views"] as? Double) ?? Double(dict["views"] as? Int ?? 0)
                case "revenue":
                    if let r = dict["revenue"] as? Double { revenue += r }
                    else if let r = dict["revenue"] as? String { revenue += Double(r) ?? 0 }
                default: break
                }
            }
            points.append(AnalyticsPoint(date: sliceDate, downloads: downloads, views: views, revenue: revenue))
        }
        return points
    }

    // MARK: - HTTP helpers

    private func get(url: URL) async throws -> Data {
        var req = URLRequest(url: url)
        req.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        if !token.isEmpty { req.setValue(token, forHTTPHeaderField: "Authorization") }
        let (data, resp) = try await URLSession.shared.data(for: req)
        try checkStatus(resp, data: data)
        return data
    }

    private func post(url: URL, body: [String: Any]) async throws -> Data {
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        if !token.isEmpty { req.setValue(token, forHTTPHeaderField: "Authorization") }
        req.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (data, resp) = try await URLSession.shared.data(for: req)
        try checkStatus(resp, data: data)
        return data
    }

    private func checkStatus(_ response: URLResponse, data: Data) throws {
        guard let http = response as? HTTPURLResponse else { return }
        if http.statusCode == 401 { throw APIError.unauthorized }
        if http.statusCode == 404 { throw APIError.notFound }
        if http.statusCode == 429 { throw APIError.rateLimited }
        if http.statusCode >= 400 {
            let msg = String(data: data, encoding: .utf8) ?? "HTTP \(http.statusCode)"
            throw APIError.httpError(http.statusCode, msg)
        }
    }
}

enum APIError: LocalizedError {
    case noToken
    case unauthorized
    case notFound
    case rateLimited
    case parseError(String)
    case httpError(Int, String)

    var errorDescription: String? {
        switch self {
        case .noToken:          return "No API token set. Add your Modrinth PAT in Settings."
        case .unauthorized:     return "Invalid or expired API token."
        case .notFound:         return "Resource not found."
        case .rateLimited:      return "Rate limited — wait a moment and refresh."
        case .parseError(let m): return "Parse error: \(m)"
        case .httpError(let c, let m): return "HTTP \(c): \(m)"
        }
    }
}
