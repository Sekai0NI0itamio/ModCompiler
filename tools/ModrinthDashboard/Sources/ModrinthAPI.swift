import Foundation

actor ModrinthAPI {
    static let shared = ModrinthAPI()

    private let v2 = "https://api.modrinth.com/v2"
    private let v3 = "https://api.modrinth.com/v3"
    private let userAgent = "itamio/ModrinthDashboard/1.0 (github.com/Sekai0NI0itamio/ModCompiler)"

    var token: String = ""
    func setToken(_ t: String) { token = t }

    // MARK: - User Projects

    func fetchUserProjects(username: String) async throws -> [ModrinthProject] {
        let data = try await get(url: URL(string: "\(v2)/user/\(username)/projects")!)
        return try JSONDecoder().decode([ModrinthProject].self, from: data)
    }

    // MARK: - Single project (full body/gallery for aesthetics)

    func fetchProject(id: String) async throws -> ModrinthProject {
        let data = try await get(url: URL(string: "\(v2)/project/\(id)")!)
        return try JSONDecoder().decode(ModrinthProject.self, from: data)
    }

    // MARK: - Project Versions

    func fetchVersions(projectId: String) async throws -> [ModrinthVersion] {
        let encoded = projectId.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? projectId
        let data = try await get(url: URL(string: "\(v2)/project/\(encoded)/version")!)
        return try JSONDecoder().decode([ModrinthVersion].self, from: data)
    }

    // MARK: - Analytics (downloads + views only — no revenue)
    // Revenue removed: Modrinth's internal accounting units are unreliable for display.
    // Conversion rate (views → downloads) is the meaningful signal instead.

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
            "project_ids": projectIds,
            "time_range": [
                "start":      fmt.string(from: start),
                "end":        fmt.string(from: end),
                "resolution": ["slices": min(max(slices, 1), 1024)]
            ],
            "return_metrics": [
                "project_views":     ["bucket_by": ["project_id"]],
                "project_downloads": ["bucket_by": ["project_id"]]
            ]
        ]

        let url = URL(string: "\(v3)/analytics")!
        let data = try await post(url: url, body: body)

        guard let sliceArray = try? JSONSerialization.jsonObject(with: data) as? [[Any]] else {
            if let errStr = String(data: data, encoding: .utf8) {
                throw APIError.parseError(String(errStr.prefix(300)))
            }
            throw APIError.parseError("analytics response not [[Any]]")
        }

        let sliceCount = sliceArray.count
        guard sliceCount > 0 else { return [] }

        let totalSeconds  = end.timeIntervalSince(start)
        let sliceDuration = totalSeconds / Double(sliceCount)
        let requestedIds  = Set(projectIds)

        var points: [AnalyticsPoint] = []
        for (i, slice) in sliceArray.enumerated() {
            let sliceDate = start.addingTimeInterval(Double(i) * sliceDuration)
            var downloads = 0.0, views = 0.0

            for item in slice {
                guard let dict = item as? [String: Any] else { continue }
                if let src = dict["source_project"] as? String,
                   !requestedIds.isEmpty, !requestedIds.contains(src) { continue }
                switch dict["metric_kind"] as? String ?? "" {
                case "downloads": downloads += doubleFrom(dict["downloads"])
                case "views":     views     += doubleFrom(dict["views"])
                default: break
                }
            }
            points.append(AnalyticsPoint(date: sliceDate, downloads: downloads, views: views))
        }
        return points
    }

    // MARK: - HTTP

    private func get(url: URL) async throws -> Data {
        var req = URLRequest(url: url)
        req.httpMethod = "GET"
        req.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        if !token.isEmpty { req.setValue(token, forHTTPHeaderField: "Authorization") }
        return try await perform(req)
    }

    private func post(url: URL, body: [String: Any]) async throws -> Data {
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        req.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        if !token.isEmpty { req.setValue(token, forHTTPHeaderField: "Authorization") }
        req.httpBody = try JSONSerialization.data(withJSONObject: body)
        return try await perform(req)
    }

    private func perform(_ req: URLRequest) async throws -> Data {
        let (data, response) = try await URLSession.shared.data(for: req)
        guard let http = response as? HTTPURLResponse else { return data }
        switch http.statusCode {
        case 200...299: return data
        case 401: throw APIError.unauthorized
        case 403: throw APIError.forbidden
        case 404: throw APIError.notFound
        case 429: throw APIError.rateLimited
        default:
            let msg = String(data: data, encoding: .utf8) ?? "HTTP \(http.statusCode)"
            throw APIError.httpError(http.statusCode, String(msg.prefix(300)))
        }
    }

    private func doubleFrom(_ val: Any?) -> Double {
        switch val {
        case let d as Double: return d
        case let i as Int:    return Double(i)
        case let s as String: return Double(s) ?? 0
        default:              return 0
        }
    }
}

enum APIError: LocalizedError {
    case noToken, unauthorized, forbidden, notFound, rateLimited
    case parseError(String), httpError(Int, String)

    var errorDescription: String? {
        switch self {
        case .noToken:           return "No API token. Open Settings (⚙️) and add your Modrinth PAT."
        case .unauthorized:      return "Invalid or expired API token."
        case .forbidden:         return "Token missing ANALYTICS_READ scope."
        case .notFound:          return "Resource not found."
        case .rateLimited:       return "Rate limited — wait a moment and refresh."
        case .parseError(let m): return "Parse error: \(m)"
        case .httpError(let c, let m): return "HTTP \(c): \(m)"
        }
    }
}
