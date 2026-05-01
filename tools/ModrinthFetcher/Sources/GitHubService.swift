import Foundation

struct AppError: LocalizedError {
    let message: String
    init(_ m: String) { message = m }
    var errorDescription: String? { message }
}

final class GitHubService {

    private let repo: String
    private let workflowId = "fetch-modrinth-for-curseforge.yml"
    private let artifactName = "modrinth-curseforge-bundle"

    init(repo: String) { self.repo = repo }

    // MARK: - Repo discovery

    static func discoverRepo() throws -> String {
        let raw = try shell(["git", "remote", "get-url", "origin"]).trimmingCharacters(in: .whitespacesAndNewlines)
        for pattern in [#"git@github\.com:([^/]+/[^/]+?)(?:\.git)?$"#,
                        #"https?://github\.com/([^/]+/[^/]+?)(?:\.git)?$"#] {
            if let cap = raw.firstCapture(pattern) { return cap }
        }
        throw AppError("Cannot parse repo from: \(raw)")
    }

    static func defaultBranch(repo: String) throws -> String {
        let out = try gh(["repo", "view", repo,
                          "--json", "defaultBranchRef",
                          "--jq", ".defaultBranchRef.name"])
        let s = out.trimmingCharacters(in: .whitespacesAndNewlines)
        return s.isEmpty ? "main" : s
    }

    // MARK: - Dispatch

    func dispatch(modrinthURL: String, branch: String) throws -> Int {
        let before = Set(try listRunIds(branch: branch))
        try gh(["workflow", "run", workflowId,
                "-R", repo, "--ref", branch,
                "-f", "modrinth_url=\(modrinthURL)"])
        let deadline = Date().addingTimeInterval(120)
        while Date() < deadline {
            Thread.sleep(forTimeInterval: 4)
            for id in try listRunIds(branch: branch) where !before.contains(id) {
                return id
            }
        }
        throw AppError("Workflow dispatched but no run appeared within 120s.")
    }

    // MARK: - Poll

    struct RunInfo { var status: String; var conclusion: String; var url: String }

    func pollRun(runId: Int) throws -> RunInfo {
        let out = try gh(["run", "view", String(runId), "-R", repo,
                          "--json", "status,conclusion,url"])
        guard let data = out.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else { throw AppError("Bad run view JSON") }
        return RunInfo(status:     json["status"]     as? String ?? "",
                       conclusion: json["conclusion"] as? String ?? "",
                       url:        json["url"]        as? String ?? "")
    }

    // MARK: - Download

    func downloadArtifact(runId: Int, into dir: URL) throws -> URL {
        let dest = dir.appendingPathComponent(artifactName)
        try? FileManager.default.removeItem(at: dest)
        try FileManager.default.createDirectory(at: dest, withIntermediateDirectories: true)
        try gh(["run", "download", String(runId), "-R", repo,
                "-n", artifactName, "-D", dest.path])
        return dest
    }

    // MARK: - Helpers

    private func listRunIds(branch: String) throws -> [Int] {
        let out = try gh(["run", "list", "-R", repo, "-w", workflowId,
                          "-b", branch, "-e", "workflow_dispatch",
                          "--json", "databaseId", "-L", "20"])
        guard let data = out.data(using: .utf8),
              let arr = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]]
        else { return [] }
        return arr.compactMap { $0["databaseId"] as? Int }
    }

    @discardableResult
    private func gh(_ args: [String]) throws -> String {
        try GitHubService.gh(args)
    }

    @discardableResult
    static func gh(_ args: [String]) throws -> String {
        try shell(["gh"] + args)
    }

    @discardableResult
    static func shell(_ args: [String]) throws -> String {
        let p = Process()
        p.executableURL = URL(fileURLWithPath: "/usr/bin/env")
        p.arguments = args
        var env = ProcessInfo.processInfo.environment
        env["PATH"] = (env["PATH"] ?? "") + ":/opt/homebrew/bin:/usr/local/bin"
        p.environment = env
        let out = Pipe(), err = Pipe()
        p.standardOutput = out; p.standardError = err
        try p.run(); p.waitUntilExit()
        let o = String(data: out.fileHandleForReading.readDataToEndOfFile(), encoding: .utf8) ?? ""
        let e = String(data: err.fileHandleForReading.readDataToEndOfFile(), encoding: .utf8) ?? ""
        guard p.terminationStatus == 0 else {
            throw AppError((e.isEmpty ? o : e).trimmingCharacters(in: .whitespacesAndNewlines))
        }
        return o
    }
}

extension String {
    func firstCapture(_ pattern: String) -> String? {
        guard let re = try? NSRegularExpression(pattern: pattern),
              let m = re.firstMatch(in: self, range: NSRange(startIndex..., in: self)),
              m.numberOfRanges > 1,
              let r = Range(m.range(at: 1), in: self) else { return nil }
        return String(self[r])
    }
}
