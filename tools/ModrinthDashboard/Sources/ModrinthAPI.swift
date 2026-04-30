import Foundation

// MARK: - API Client
// Token format: bare PAT string, no "Bearer" prefix.
// Matches exactly how the Python scripts use it:
//   h["Authorization"] = token   (not "Bearer " + token)

actor ModrinthAPI {
    static let shared = ModrinthAPI()

    private let v2 = "https://api.modrinth.com/v2"
    private let v3 = "https://api.modrinth.com/v3"
    // User-Agent is required by Modrinth — requests without it may be blocked
    private let userAgent = "itamio/ModrinthDashboard/1.0 (github.com/Sekai0NI0itamio/ModCompiler)"

    var token: String = ""

    // MARK: - User Projects

    func fetchUserProjects(username: String) async throws -> [ModrinthProject] {
        let url = URL(string: "\(v2)/user/\(username)/projects")!
        let data = try await get(url: url)
        let decoder = JSONDecoder()
        return try decoder.decode([ModrinthProject].self, from: data)
    }

    // MARK: - Project Versions (for ghost/missing detection)

    func fetchVersions(projectId: String) async throws -> [ModrinthVersion] {
        // No filters — fetch ALL versions across all loaders and game versions
        let encoded = projectId.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? projectId
        let url = URL(string: "\(v2)/project/\(encoded)/version")!
        let data = try await get(url: url)
        return try JSONDecoder().decode([ModrinthVersion].self, from: data)
    }

    // MARK: - Analytics (v3, requires PAT with ANALYTICS_READ + PAYOUTS_READ)
    // POST /v3/analytics
    // Returns array of time slices, each slice is array of data points.

    func fetchAnalytics(
        projectIds: [String],
        start: Date,
        end: Date,
        slices: Int
    ) async throws -> [AnalyticsPoint] {
        guard !token.isEmpty else { throw APIError.noToken }

        let fmt = ISO8601DateFormatter()
        fmt.formatOptions = [.withInternetDateTime]

        // The v3 analytics API requires:
        //   - "project_ids": array of project IDs (top-level field)
        //   - "time_range": { "start", "end", "resolution" }
        //   - "return_metrics": { metric_name: { "bucket_by": [...] } }
        let body: [String: Any] = [
            "project_ids": projectIds,
            "time_range": [
                "start":      fmt.string(from: start),
                "end":        fmt.string(from: end),
                "resolution": ["slices": min(slices, 1024)]
            ],
            "return_metrics": [
                "project_views": [
                    "bucket_by": ["project_id"]
                ],
                "project_downloads": [
                    "bucket_by": ["project_id"]
                ],
                "project_revenue": [
                    "bucket_by": ["project_id"]
                ]
            ]
        ]

        let url = URL(string: "\(v3)/analytics")!
        let data = try await post(url: url, body: body)

        // Response: [[DataPoint]] — outer array = time slices, inner = data points in that slice
        guard let sliceArray = try? JSONSerialization.jsonObject(with: data) as? [[Any]] else {
            // Try to decode error message
            if let errStr = String(data: data, encoding: .utf8) {
                throw APIError.parseError("analytics: \(errStr.prefix(200))")
            }
            throw APIError.parseError("analytics response not [[Any]]")
        }

        let sliceCount = sliceArray.count
        guard sliceCount > 0 else { return [] }

        let totalSeconds = end.timeIntervalSince(start)
        let sliceDuration = totalSeconds / Double(sliceCount)

        var points: [AnalyticsPoint] = []
        for (i, slice) in sliceArray.enumerated() {
            // Date = start of this slice
            let sliceDate = start.addingTimeInterval(Double(i) * sliceDuration)
            var downloads = 0.0
            var views     = 0.0
            var revenue   = 0.0

            for item in slice {
                guard let dict = item as? [String: Any] else { continue }
                let kind = dict["metric_kind"] as? String ?? ""
                switch kind {
                case "downloads":
                    downloads += doubleFrom(dict["downloads"])
                case "views":
                    views += doubleFrom(dict["views"])
                case "revenue":
                    revenue += doubleFrom(dict["revenue"])
                default:
                    break
                }
            }
            points.append(AnalyticsPoint(
                date: sliceDate,
                downloads: downloads,
                views: views,
                revenue: revenue
            ))
        }
        return points
    }

    // MARK: - HTTP helpers

    private func get(url: URL) async throws -> Data {
        var req = URLRequest(url: url)
        req.httpMethod = "GET"
        req.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        // Token: bare PAT, no "Bearer" prefix — matches Python scripts exactly
        if !token.isEmpty {
            req.setValue(token, forHTTPHeaderField: "Authorization")
        }
        return try await perform(req)
    }

    private func post(url: URL, body: [String: Any]) async throws -> Data {
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        req.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        if !token.isEmpty {
            req.setValue(token, forHTTPHeaderField: "Authorization")
        }
        req.httpBody = try JSONSerialization.data(withJSONObject: body)
        return try await perform(req)
    }

    private func perform(_ req: URLRequest) async throws -> Data {
        let (data, response) = try await URLSession.shared.data(for: req)
        guard let http = response as? HTTPURLResponse else { return data }
        switch http.statusCode {
        case 200...299: return data
        case 401:       throw APIError.unauthorized
        case 403:       throw APIError.forbidden
        case 404:       throw APIError.notFound
        case 429:       throw APIError.rateLimited
        default:
            let msg = String(data: data, encoding: .utf8) ?? "HTTP \(http.statusCode)"
            throw APIError.httpError(http.statusCode, String(msg.prefix(300)))
        }
    }

    // MARK: - Helpers

    private func doubleFrom(_ val: Any?) -> Double {
        switch val {
        case let d as Double:  return d
        case let i as Int:     return Double(i)
        case let s as String:  return Double(s) ?? 0
        default:               return 0
        }
    }
}

// MARK: - Errors

enum APIError: LocalizedError {
    case noToken
    case unauthorized
    case forbidden
    case notFound
    case rateLimited
    case parseError(String)
    case httpError(Int, String)

    var errorDescription: String? {
        switch self {
        case .noToken:
            return "No API token. Open Settings (⚙️) and add your Modrinth PAT to see analytics."
        case .unauthorized:
            return "Invalid or expired API token. Check your PAT in Settings."
        case .forbidden:
            return "Token missing required scopes. Needs ANALYTICS_READ + PAYOUTS_READ."
        case .notFound:
            return "Resource not found on Modrinth."
        case .rateLimited:
            return "Rate limited (300 req/min). Wait a moment and refresh."
        case .parseError(let m):
            return "Parse error: \(m)"
        case .httpError(let c, let m):
            return "HTTP \(c): \(m)"
        }
    }
}
