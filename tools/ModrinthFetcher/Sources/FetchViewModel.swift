import Foundation
import Combine

@MainActor
final class FetchViewModel: ObservableObject {

    @Published var modrinthURL: String = ""
    @Published var state: FetchState = .idle
    @Published var statusMessage: String = ""
    @Published var runURL: String = ""

    private var currentTask: Task<Void, Never>?

    func fetch() {
        let url = modrinthURL.trimmingCharacters(in: .whitespaces)
        guard !url.isEmpty else {
            state = .failed("Please enter a Modrinth URL or slug.")
            return
        }
        currentTask?.cancel()
        state = .running(message: "Discovering repo…")
        statusMessage = ""
        runURL = ""

        currentTask = Task {
            do {
                let repo   = try GitHubService.discoverRepo()
                let branch = try GitHubService.branchWithWorkflow(
                    repo: repo,
                    workflowFile: "fetch-modrinth-curseforge-bundle.yml"
                )
                let svc    = GitHubService(repo: repo)

                await update("Dispatching to \(repo) (\(branch))…")
                let runId = try await offload { try svc.dispatch(modrinthURL: url, branch: branch) }

                let ghURL = "https://github.com/\(repo)/actions/runs/\(runId)"
                await MainActor.run { self.runURL = ghURL }
                await update("Run \(runId) queued…")

                // Poll
                let deadline = Date().addingTimeInterval(1800)
                while Date() < deadline {
                    try await Task.sleep(nanoseconds: 10_000_000_000)
                    if Task.isCancelled { return }
                    let info = try await offload { try svc.pollRun(runId: runId) }
                    await update("[\(ts())] \(info.status)")
                    if info.status == "completed" {
                        await update("Completed (\(info.conclusion)). Downloading…")
                        let outDir = FileManager.default
                            .urls(for: .downloadsDirectory, in: .userDomainMask)[0]
                            .appendingPathComponent("ModrinthBundles")
                        try FileManager.default.createDirectory(at: outDir, withIntermediateDirectories: true)
                        let dir = try await offload { try svc.downloadArtifact(runId: runId, into: outDir) }
                        let indexURL = dir.appendingPathComponent("index.json")
                        if let data = try? Data(contentsOf: indexURL),
                           let idx  = try? JSONDecoder().decode(BundleIndex.self, from: data) {
                            await MainActor.run { self.state = .done(bundleDir: dir, index: idx) }
                        } else {
                            await MainActor.run { self.state = .failed("Downloaded but could not read index.json") }
                        }
                        return
                    }
                }
                await MainActor.run { self.state = .failed("Timed out after 30 minutes.") }
            } catch {
                if !Task.isCancelled {
                    await MainActor.run { self.state = .failed(error.localizedDescription) }
                }
            }
        }
    }

    func cancel() {
        currentTask?.cancel()
        state = .idle
        statusMessage = ""
    }

    private func update(_ msg: String) async {
        await MainActor.run { self.statusMessage = msg }
    }

    private func ts() -> String {
        let f = DateFormatter(); f.dateFormat = "HH:mm:ss"; return f.string(from: Date())
    }
}

// Run blocking work off the main actor
func offload<T: Sendable>(_ block: @escaping () throws -> T) async throws -> T {
    try await withCheckedThrowingContinuation { cont in
        DispatchQueue.global(qos: .userInitiated).async {
            do    { cont.resume(returning: try block()) }
            catch { cont.resume(throwing: error) }
        }
    }
}
