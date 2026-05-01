import Foundation

/// Thin wrapper around `gh` CLI for workflow dispatch + artifact download.
/// Mirrors the patterns in modcompiler/mod_compile.py.
final class GitHubService {

    private let repo: String
    private let workflowId = "fetch-modrinth-for-curseforge.yml"
    private let artifactName = "modrinth-curseforge-bundle"

    init(repo: String) {
        self.repo = repo
    }

    // MARK: - Discover repo from git remote

    static func discoverRepo(repoRoot: URL) throws -> String {
        let result = try shell(["git", "remote", "get-url", "origin"], cwd: repoRoot)
        let raw = result.trimmingCharacters(in: .whitespacesAndNewlines)
        let patterns = [
            #"git@github\.com:([^/]+/[^/]+?)(?:\.git)?$"#,
            #"https?://github\.com/([^/]+/[^/]+?)(?:\.git)?$"#,
        ]
        for pattern in patterns {
            if let cap = raw.firstCapture(pattern: pattern) {
                return cap
            }
        }
        throw AppError("Could not parse owner/repo from: \(raw)")
    }

    static func defaultBranch(repo: String) throws -> String {
        let out = try ghCLI(["repo", "view", repo,
                             "--json", "defaultBranchRef",
                             "--jq", ".defaultBranchRef.name"])
        return out.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "main"
             : out.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    // MARK: - Dispatch

    func dispatch(modrinthURL: String, branch: String) throws -> Int {
        // snapshot existing run IDs
        let before = Set(try listRunIds(branch: branch))

        try ghCLI([
            "workflow", "run", workflowId,
            "-R", repo,
            "--ref", branch,
            "-f", "modrinth_url=\(modrinthURL)",
        ])

        // poll up to 120s for a new run to appear
        let deadline = Date().addingTimeInterval(120)
        while Date() < deadline {
            Thread.sleep(forTimeInterval: 4)
            let current = try listRunIds(branch: branch)
            for id in current where !before.contains(id) {
                return id
            }
        }
        throw AppError("Workflow dispatched but no new run appeared within 120s.")
    }

    // MARK: - Poll

    struct RunInfo {
        var status: String
        var conclusion: String
        var url: String
    }

    func pollRun(runId: Int) throws -> RunInfo {
        let out = try ghCLI([
            "run", "view", String(runId),
            "-R", repo,
            "--json", "status,conclusion,url",
        ])
        guard let data = out.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else { throw AppError("Could not parse run view JSON") }
        return RunInfo(
            status:     json["status"]     as? String ?? "",
            conclusion: json["conclusion"] as? String ?? "",
            url:        json["url"]        as? String ?? ""
        )
    }

    // MARK: - Download artifact

    func downloadArtifact(runId: Int, into dir: URL) throws -> URL {
        let dest = dir.appendingPathComponent(artifactName)
        try? FileManager.default.removeItem(at: dest)
        try FileManager.default.createDirectory(at: dest, withIntermediateDirectories: true)

        try ghCLI([
            "run", "download", String(runId),
            "-R", repo,
            "-n", artifactName,
            "-D", dest.path,
        ])
        return dest
    }

    // MARK: - Helpers

    private func listRunIds(branch: String) throws -> [Int] {
        let out = try ghCLI([
            "run", "list",
            "-R", repo,
            "-w", workflowId,
            "-b", branch,
            "-e", "workflow_dispatch",
            "--json", "databaseId",
            "-L", "20",
        ])
        guard let data = out.data(using: .utf8),
              let arr = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]]
        else { return [] }
        return arr.compactMap { $0["databaseId"] as? Int }
    }

    @discardableResult
    private func ghCLI(_ args: [String]) throws -> String {
        try GitHubService.ghCLI(args)
    }

    @discardableResult
    static func ghCLI(_ args: [String]) throws -> String {
        try shell(["gh"] + args)
    }

    @discardableResult
    static func shell(_ args: [String], cwd: URL? = nil) throws -> String {
        let proc = Process()
        proc.executableURL = URL(fileURLWithPath: "/usr/bin/env")
        proc.arguments = args
        if let cwd { proc.currentDirectoryURL = cwd }

        // Pass through PATH so gh is found
        var env = ProcessInfo.processInfo.environment
        env["PATH"] = (env["PATH"] ?? "") + ":/opt/homebrew/bin:/usr/local/bin"
        proc.environment = env

        let pipe = Pipe()
        let errPipe = Pipe()
        proc.standardOutput = pipe
        proc.standardError  = errPipe

        try proc.run()
        proc.waitUntilExit()

        let out = String(data: pipe.fileHandleForReading.readDataToEndOfFile(), encoding: .utf8) ?? ""
        let err = String(data: errPipe.fileHandleForReading.readDataToEndOfFile(), encoding: .utf8) ?? ""

        guard proc.terminationStatus == 0 else {
            throw AppError((err.isEmpty ? out : err).trimmingCharacters(in: .whitespacesAndNewlines))
        }
        return out
    }
}

// MARK: - Helpers

struct AppError: LocalizedError {
    let message: String
    init(_ message: String) { self.message = message }
    var errorDescription: String? { message }
}

extension String {
    func firstCapture(pattern: String) -> String? {
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: self, range: NSRange(self.startIndex..., in: self)),
              match.numberOfRanges > 1,
              let range = Range(match.range(at: 1), in: self)
        else { return nil }
        return String(self[range])
    }
}
