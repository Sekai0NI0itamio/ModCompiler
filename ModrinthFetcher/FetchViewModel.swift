import Foundation
import Combine

@MainActor
final class FetchViewModel: ObservableObject {

    @Published var modrinthURL: String = ""
    @Published var state: FetchState = .idle
    @Published var statusMessage: String = ""
    @Published var runURL: String = ""
    @Published var bundleIndex: BundleIndex? = nil
    @Published var bundleDir: URL? = nil

    private let repoRoot: URL
    private var pollTask: Task<Void, Never>? = nil

    init() {
        // Find the repo root by walking up from the app bundle looking for .git
        var candidate = Bundle.main.bundleURL
        var found: URL? = nil
        for _ in 0..<10 {
            candidate = candidate.deletingLastPathComponent()
            if FileManager.default.fileExists(atPath: candidate.appendingPathComponent(".git").path) {
                found = candidate
                break
            }
        }
        // Fallback: current working directory (works when launched from terminal or Xcode)
        repoRoot = found ?? URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
    }

    // MARK: - Fetch

    func fetch() {
        guard !modrinthURL.trimmingCharacters(in: .whitespaces).isEmpty else {
            state = .failed("Please enter a Modrinth URL or slug.")
            return
        }
        pollTask?.cancel()
        state = .dispatching
        statusMessage = "Dispatching workflow…"
        bundleIndex = nil
        bundleDir = nil
        runURL = ""

        Task {
            do {
                let repo   = try GitHubService.discoverRepo(repoRoot: repoRoot)
                let branch = try GitHubService.defaultBranch(repo: repo)
                let svc    = GitHubService(repo: repo)

                statusMessage = "Dispatching to \(repo) (\(branch))…"
                let runId = try await svc.dispatch(modrinthURL: modrinthURL.trimmingCharacters(in: .whitespaces), branch: branch)

                runURL = "https://github.com/\(repo)/actions/runs/\(runId)"
                state = .waiting(runId: runId, status: "queued")
                statusMessage = "Run \(runId) queued…"

                // Poll
                try await poll(svc: svc, runId: runId, repo: repo)

            } catch {
                state = .failed(error.localizedDescription)
                statusMessage = error.localizedDescription
            }
        }
    }

    private func poll(svc: GitHubService, runId: Int, repo: String) async throws {
        let deadline = Date().addingTimeInterval(1800) // 30 min max
        while Date() < deadline {
            try await Task.sleep(nanoseconds: 10_000_000_000) // 10s
            let info = try await svc.pollRun(runId: runId)
            state = .waiting(runId: runId, status: info.status)
            statusMessage = "[\(timestamp())] status: \(info.status)"

            if info.status == "completed" {
                statusMessage = "Completed — \(info.conclusion). Downloading artifact…"
                state = .downloading

                let outputDir = FileManager.default.urls(for: .downloadsDirectory, in: .userDomainMask)[0]
                    .appendingPathComponent("ModrinthBundles")
                try FileManager.default.createDirectory(at: outputDir, withIntermediateDirectories: true)

                let dir = try await svc.downloadArtifact(runId: runId, into: outputDir)
                bundleDir = dir

                // Load index.json
                let indexURL = dir.appendingPathComponent("index.json")
                if let data = try? Data(contentsOf: indexURL),
                   let index = try? JSONDecoder().decode(BundleIndex.self, from: data) {
                    bundleIndex = index
                }

                state = .done(bundleDir: dir)
                statusMessage = "Done! Bundle saved to \(dir.path)"
                return
            }
        }
        throw AppError("Timed out waiting for workflow to complete.")
    }

    func cancel() {
        pollTask?.cancel()
        state = .idle
        statusMessage = ""
    }

    private func timestamp() -> String {
        let f = DateFormatter()
        f.dateFormat = "HH:mm:ss"
        return f.string(from: Date())
    }
}

// Make GitHubService methods async-friendly
extension GitHubService {
    func dispatch(modrinthURL: String, branch: String) async throws -> Int {
        let url = modrinthURL
        let br  = branch
        return try await withCheckedThrowingContinuation { continuation in
            DispatchQueue.global(qos: .userInitiated).async {
                do {
                    let id = try self.dispatch(modrinthURL: url, branch: br)
                    continuation.resume(returning: id)
                } catch {
                    continuation.resume(throwing: error)
                }
            }
        }
    }

    func pollRun(runId: Int) async throws -> RunInfo {
        let id = runId
        return try await withCheckedThrowingContinuation { continuation in
            DispatchQueue.global(qos: .background).async {
                do {
                    let info = try self.pollRun(runId: id)
                    continuation.resume(returning: info)
                } catch {
                    continuation.resume(throwing: error)
                }
            }
        }
    }

    func downloadArtifact(runId: Int, into dir: URL) async throws -> URL {
        let id  = runId
        let dst = dir
        return try await withCheckedThrowingContinuation { continuation in
            DispatchQueue.global(qos: .userInitiated).async {
                do {
                    let result = try self.downloadArtifact(runId: id, into: dst)
                    continuation.resume(returning: result)
                } catch {
                    continuation.resume(throwing: error)
                }
            }
        }
    }
}
