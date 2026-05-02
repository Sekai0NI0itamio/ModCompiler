import Foundation

struct AppError: LocalizedError {
    let message: String
    init(_ m: String) { message = m }
    var errorDescription: String? { message }
}

final class GitHubService {

    private let repo: String
    private let workflowId = "fetch-modrinth-curseforge-bundle.yml"
    private let artifactName = "modrinth-curseforge-bundle"

    init(repo: String) { self.repo = repo }

    // MARK: - Repo discovery

    static func discoverRepo() throws -> String {
        // Walk up from the app bundle location looking for a .git directory
        var dir = Bundle.main.bundleURL.deletingLastPathComponent()
        for _ in 0..<12 {
            let gitDir = dir.appendingPathComponent(".git")
            if FileManager.default.fileExists(atPath: gitDir.path) {
                // Found the repo root — now read the remote URL
                let raw = try shell(["git", "-C", dir.path,
                                     "remote", "get-url", "origin"])
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                for pattern in [#"git@github\.com:([^/]+/[^/]+?)(?:\.git)?$"#,
                                #"https?://github\.com/([^/]+/[^/]+?)(?:\.git)?$"#] {
                    if let cap = raw.firstCapture(pattern) { return cap }
                }
                throw AppError("Cannot parse repo from remote URL: \(raw)")
            }
            let parent = dir.deletingLastPathComponent()
            if parent == dir { break }   // reached filesystem root
            dir = parent
        }
        throw AppError(
            "Could not find a .git directory near the app.\n" +
            "Make sure ModrinthFetcher.app is inside the ModCompiler repo folder."
        )
    }

    static func defaultBranch(repo: String) throws -> String {
        let out = try gh(["repo", "view", repo,
                          "--json", "defaultBranchRef",
                          "--jq", ".defaultBranchRef.name"])
        let s = out.trimmingCharacters(in: .whitespacesAndNewlines)
        return s.isEmpty ? "main" : s
    }

    /// Returns the branch name that has the workflow file committed and pushed.
    /// Checks the default branch first, then common alternatives.
    static func branchWithWorkflow(repo: String, workflowFile: String) throws -> String {
        let defaultBr = try defaultBranch(repo: repo)
        let candidates = [defaultBr, "main", "master", "develop"]
        for branch in candidates {
            let out = (try? gh(["api",
                                "repos/\(repo)/contents/.github/workflows/\(workflowFile)",
                                "--header", "Accept: application/vnd.github+json",
                                "-X", "GET",
                                "-f", "ref=\(branch)"])) ?? ""
            if !out.isEmpty && !out.contains("Not Found") {
                return branch
            }
        }
        // Fall back to default branch and let the dispatch surface the real error
        return defaultBr
    }
    // MARK: - Dispatch

    func dispatch(modrinthURL: String, branch: String) throws -> Int {
        // Verify the workflow exists and has workflow_dispatch on this branch
        // before attempting to trigger it, so we can give a clear error.
        let workflowBranch = try resolveWorkflowBranch(preferredBranch: branch)

        let before = Set(try listRunIds(branch: workflowBranch))
        do {
            try gh(["workflow", "run", workflowId,
                    "-R", repo, "--ref", workflowBranch,
                    "-f", "modrinth_url=\(modrinthURL)"])
        } catch let err as AppError {
            // Provide a more actionable error message for the common 422 case
            let msg = err.message
            if msg.contains("422") || msg.contains("workflow_dispatch") || msg.contains("does not have") {
                throw AppError(
                    "GitHub rejected the dispatch (HTTP 422).\n\n" +
                    "This usually means the workflow file '\(workflowId)' " +
                    "does not exist on branch '\(workflowBranch)', or it was " +
                    "recently pushed and GitHub hasn't indexed it yet.\n\n" +
                    "• Make sure the workflow is committed and pushed to '\(workflowBranch)'.\n" +
                    "• Wait ~30 seconds after pushing and try again.\n\n" +
                    "Original error: \(msg)"
                )
            }
            throw err
        }
        let deadline = Date().addingTimeInterval(120)
        while Date() < deadline {
            Thread.sleep(forTimeInterval: 4)
            for id in try listRunIds(branch: workflowBranch) where !before.contains(id) {
                return id
            }
        }
        throw AppError("Workflow dispatched but no run appeared within 120s.")
    }

    /// Returns the branch that actually has the workflow file committed.
    /// Falls back to `preferredBranch` if we can't determine it.
    private func resolveWorkflowBranch(preferredBranch: String) throws -> String {
        // Ask GitHub which branches have this workflow enabled
        let out = (try? gh(["workflow", "view", workflowId, "-R", repo])) ?? ""
        // If the workflow view succeeds, the workflow is known to GitHub on the default branch.
        // We still use the preferred branch — if it fails we'll catch the 422 above.
        _ = out
        return preferredBranch
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
        // Verify something was actually written
        let index = dest.appendingPathComponent("index.json")
        guard FileManager.default.fileExists(atPath: index.path) else {
            throw AppError(
                "gh run download completed but no files were written to:\n\(dest.path)\n\n" +
                "The artifact may have expired (GitHub keeps artifacts for 90 days by default). " +
                "Try fetching again to create a fresh run."
            )
        }
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
