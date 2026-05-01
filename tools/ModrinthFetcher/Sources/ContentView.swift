import SwiftUI

struct ContentView: View {
    @StateObject private var vm = FetchViewModel()

    var body: some View {
        VStack(spacing: 0) {
            // ── Top bar ──────────────────────────────────────────────────
            HStack(spacing: 10) {
                Image(systemName: "cube.box.fill")
                    .foregroundStyle(.green)
                    .font(.title3)

                TextField("Modrinth URL or slug  (e.g. https://modrinth.com/mod/pingfix)",
                          text: $vm.modrinthURL)
                    .textFieldStyle(.roundedBorder)
                    .onSubmit { vm.fetch() }
                    .disabled(isBusy)

                Button(action: vm.fetch) {
                    Label("Fetch", systemImage: "arrow.down.circle.fill")
                        .fontWeight(.semibold)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .disabled(isBusy || vm.modrinthURL.trimmingCharacters(in: .whitespaces).isEmpty)

                if isBusy {
                    Button("Cancel", role: .cancel, action: vm.cancel)
                        .buttonStyle(.bordered)
                }
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 12)
            .background(.bar)

            Divider()

            // ── Body ─────────────────────────────────────────────────────
            switch vm.state {
            case .idle:
                IdlePlaceholder()
            case .running:
                RunningView(vm: vm)
            case .done(let dir, let index):
                ResultView(index: index, bundleDir: dir)
            case .failed(let msg):
                ErrorView(message: msg)
            }
        }
    }

    private var isBusy: Bool {
        if case .running = vm.state { return true }
        return false
    }
}

// MARK: - Idle

struct IdlePlaceholder: View {
    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: "cube.box")
                .font(.system(size: 52))
                .foregroundStyle(.tertiary)
            Text("Enter a Modrinth URL and click Fetch")
                .font(.title3)
                .foregroundStyle(.secondary)
            Text("Runs on GitHub Actions — no CurseForge token needed.")
                .font(.callout)
                .foregroundStyle(.tertiary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(40)
    }
}

// MARK: - Running

struct RunningView: View {
    @ObservedObject var vm: FetchViewModel
    var body: some View {
        VStack(spacing: 18) {
            ProgressView().scaleEffect(1.3)
            Text(vm.statusMessage)
                .font(.callout)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            if !vm.runURL.isEmpty {
                Link("View run on GitHub →", destination: URL(string: vm.runURL)!)
                    .font(.callout)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(40)
    }
}

// MARK: - Error

struct ErrorView: View {
    let message: String
    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 38))
                .foregroundStyle(.red)
            Text("Error").font(.headline)
            Text(message)
                .font(.callout)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .textSelection(.enabled)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(40)
    }
}

// MARK: - Result

struct ResultView: View {
    let index: BundleIndex
    let bundleDir: URL

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {

                // Title + stats
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(index.title).font(.largeTitle.bold())
                        Text(index.description).foregroundStyle(.secondary)
                    }
                    Spacer()
                    VStack(alignment: .trailing, spacing: 3) {
                        Text("\(index.downloads.formatted()) downloads")
                            .font(.callout).foregroundStyle(.secondary)
                        Text("\(index.versionsInBundle) versions in bundle")
                            .font(.callout).foregroundStyle(.secondary)
                    }
                }

                Divider()

                // Project fields
                SectionLabel("Project Info")
                CopyGrid(items: [
                    ("Title",        index.title),
                    ("Slug",         index.slug),
                    ("Project ID",   index.projectId),
                    ("License",      index.license),
                    ("Client side",  index.clientSide),
                    ("Server side",  index.serverSide),
                    ("Categories",   index.categories.joined(separator: ", ")),
                    ("Loaders",      index.loaders.joined(separator: ", ")),
                    ("MC versions",  index.gameVersions.joined(separator: ", ")),
                    ("Modrinth URL", index.modrinthUrl),
                    ("Source URL",   index.sourceUrl),
                    ("Issues URL",   index.issuesUrl),
                ])

                Divider()

                // Files
                SectionLabel("Description Files")
                HStack(spacing: 10) {
                    RevealBtn("description.md",   "doc.text",      bundleDir.appendingPathComponent("description.md"))
                    RevealBtn("description.html", "globe",         bundleDir.appendingPathComponent("description.html"))
                    RevealBtn("project_info.txt", "doc.plaintext", bundleDir.appendingPathComponent("project_info.txt"))
                    RevealBtn("Open Bundle Folder", "folder",      bundleDir, isFolder: true)
                }

                Divider()

                // Versions
                SectionLabel("Versions  (\(index.versions.count))")
                LazyVStack(spacing: 6) {
                    ForEach(index.versions) { v in
                        VersionRow(v: v, bundleDir: bundleDir)
                    }
                }
            }
            .padding(22)
        }
    }
}

// MARK: - Helpers

struct SectionLabel: View {
    let t: String
    init(_ t: String) { self.t = t }
    var body: some View { Text(t).font(.headline) }
}

struct CopyGrid: View {
    let items: [(String, String)]
    var body: some View {
        VStack(spacing: 0) {
            ForEach(Array(items.enumerated()), id: \.offset) { i, item in
                if !item.1.isEmpty {
                    CopyRow(label: item.0, value: item.1)
                    if i < items.count - 1 { Divider().padding(.leading, 110) }
                }
            }
        }
        .background(.quaternary.opacity(0.35))
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

struct CopyRow: View {
    let label: String
    let value: String
    @State private var copied = false
    var body: some View {
        HStack(spacing: 0) {
            Text(label)
                .font(.callout).foregroundStyle(.secondary)
                .frame(width: 110, alignment: .leading)
                .padding(.leading, 10)
            Text(value)
                .font(.callout.monospaced())
                .textSelection(.enabled)
                .lineLimit(2)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 8)
            Button {
                NSPasteboard.general.clearContents()
                NSPasteboard.general.setString(value, forType: .string)
                copied = true
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { copied = false }
            } label: {
                Image(systemName: copied ? "checkmark" : "doc.on.doc")
                    .foregroundStyle(copied ? .green : .secondary)
                    .frame(width: 30)
            }
            .buttonStyle(.plain)
            .padding(.trailing, 6)
        }
        .padding(.vertical, 6)
    }
}

struct RevealBtn: View {
    let label: String; let icon: String; let url: URL; var isFolder = false
    init(_ label: String, _ icon: String, _ url: URL, isFolder: Bool = false) {
        self.label = label; self.icon = icon; self.url = url; self.isFolder = isFolder
    }
    var body: some View {
        Button {
            if isFolder { NSWorkspace.shared.open(url) }
            else        { NSWorkspace.shared.activateFileViewerSelecting([url]) }
        } label: {
            Label(label, systemImage: icon).font(.callout)
        }
        .buttonStyle(.bordered)
        .disabled(!FileManager.default.fileExists(atPath: url.path))
    }
}

struct VersionRow: View {
    let v: VersionEntry
    let bundleDir: URL
    @State private var expanded = false

    private var vDir: URL {
        bundleDir.appendingPathComponent("versions").appendingPathComponent(v.folder)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                // Type badge
                Text(v.versionType.uppercased())
                    .font(.caption2.bold())
                    .padding(.horizontal, 5).padding(.vertical, 2)
                    .background(badgeColor)
                    .foregroundStyle(.white)
                    .clipShape(Capsule())

                Text(v.displayName).font(.callout.bold()).lineLimit(1)

                Text(v.loaders.joined(separator: "·"))
                    .font(.caption).foregroundStyle(.secondary)

                Text(v.gameVersions.prefix(3).joined(separator: ", ")
                     + (v.gameVersions.count > 3 ? "…" : ""))
                    .font(.caption).foregroundStyle(.secondary)

                Spacer()
                Text(v.published).font(.caption).foregroundStyle(.tertiary)

                Button {
                    NSWorkspace.shared.activateFileViewerSelecting([vDir])
                } label: {
                    Image(systemName: "folder.badge.magnifyingglass")
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
                .help("Reveal in Finder")
                .disabled(!FileManager.default.fileExists(atPath: vDir.path))

                Button {
                    withAnimation(.easeInOut(duration: 0.12)) { expanded.toggle() }
                } label: {
                    Image(systemName: expanded ? "chevron.up" : "chevron.down")
                        .foregroundStyle(.secondary).frame(width: 18)
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 10).padding(.vertical, 7)

            if expanded {
                Divider()
                VStack(alignment: .leading, spacing: 8) {
                    CopyGrid(items: [
                        ("Version #",    v.versionNumber),
                        ("Display name", v.displayName),
                        ("Type",         v.versionType),
                        ("Loaders",      v.loaders.joined(separator: ", ")),
                        ("MC versions",  v.gameVersions.joined(separator: ", ")),
                        ("Published",    v.published),
                        ("Modrinth ID",  v.versionId),
                        ("JAR file",     v.jar),
                        ("JAR size",     v.jarSize > 0 ? "\(v.jarSize.formatted()) bytes" : "—"),
                    ])
                    if !v.changelog.isEmpty {
                        Text("Changelog").font(.caption.bold()).foregroundStyle(.secondary)
                        ScrollView {
                            Text(v.changelog)
                                .font(.caption.monospaced())
                                .textSelection(.enabled)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .frame(maxHeight: 110)
                        .background(.quaternary.opacity(0.35))
                        .clipShape(RoundedRectangle(cornerRadius: 6))
                    }
                }
                .padding(10)
            }
        }
        .background(.background)
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(.separator, lineWidth: 0.5))
    }

    private var badgeColor: Color {
        switch v.versionType.lowercased() {
        case "release": return .green
        case "beta":    return .orange
        case "alpha":   return .red
        default:        return .gray
        }
    }
}
