import SwiftUI

struct ContentView: View {
    @StateObject private var vm = FetchViewModel()

    var body: some View {
        VStack(spacing: 0) {
            // ── Header bar ───────────────────────────────────────────────
            HStack(spacing: 12) {
                Image(systemName: "cube.box.fill")
                    .font(.title2)
                    .foregroundStyle(.green)

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
            .padding(.horizontal, 20)
            .padding(.vertical, 14)
            .background(.bar)

            Divider()

            // ── Body ─────────────────────────────────────────────────────
            Group {
                switch vm.state {
                case .idle:
                    IdlePlaceholder()

                case .dispatching, .waiting, .downloading:
                    ProgressView(vm: vm)

                case .done:
                    if let index = vm.bundleIndex, let dir = vm.bundleDir {
                        ResultView(index: index, bundleDir: dir)
                    } else {
                        Text("Bundle downloaded but could not read index.json")
                            .foregroundStyle(.secondary)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }

                case .failed(let msg):
                    ErrorView(message: msg)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private var isBusy: Bool {
        switch vm.state {
        case .dispatching, .waiting, .downloading: return true
        default: return false
        }
    }
}

// MARK: - Idle placeholder

struct IdlePlaceholder: View {
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "cube.box")
                .font(.system(size: 56))
                .foregroundStyle(.tertiary)
            Text("Enter a Modrinth URL and click Fetch")
                .font(.title3)
                .foregroundStyle(.secondary)
            Text("The workflow will run on GitHub Actions and download the bundle here.")
                .font(.callout)
                .foregroundStyle(.tertiary)
                .multilineTextAlignment(.center)
        }
        .padding(40)
    }
}

// MARK: - Progress view

struct ProgressView: View {
    @ObservedObject var vm: FetchViewModel

    var body: some View {
        VStack(spacing: 20) {
            SwiftUI.ProgressView()
                .scaleEffect(1.4)
                .padding(.bottom, 4)

            Text(vm.statusMessage)
                .font(.callout)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)

            if !vm.runURL.isEmpty {
                Link("View on GitHub →", destination: URL(string: vm.runURL)!)
                    .font(.callout)
            }
        }
        .padding(40)
    }
}

// MARK: - Error view

struct ErrorView: View {
    let message: String
    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 40))
                .foregroundStyle(.red)
            Text("Error")
                .font(.headline)
            Text(message)
                .font(.callout)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .textSelection(.enabled)
        }
        .padding(40)
    }
}

// MARK: - Result view

struct ResultView: View {
    let index: BundleIndex
    let bundleDir: URL

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {

                // Project header
                HStack(alignment: .top, spacing: 16) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(index.title)
                            .font(.largeTitle.bold())
                        Text(index.description)
                            .font(.body)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    VStack(alignment: .trailing, spacing: 4) {
                        Text("\(index.downloads.formatted()) downloads")
                            .font(.callout)
                            .foregroundStyle(.secondary)
                        Text("\(index.versionsInBundle) versions")
                            .font(.callout)
                            .foregroundStyle(.secondary)
                    }
                }

                Divider()

                // Copy-paste fields
                SectionHeader("Project Info")
                InfoGrid(items: [
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

                // Description files
                SectionHeader("Description Files")
                HStack(spacing: 12) {
                    RevealButton(label: "description.md",
                                 icon: "doc.text",
                                 url: bundleDir.appendingPathComponent("description.md"))
                    RevealButton(label: "description.html",
                                 icon: "globe",
                                 url: bundleDir.appendingPathComponent("description.html"))
                    RevealButton(label: "project_info.txt",
                                 icon: "doc.plaintext",
                                 url: bundleDir.appendingPathComponent("project_info.txt"))
                    RevealButton(label: "Open Bundle Folder",
                                 icon: "folder",
                                 url: bundleDir,
                                 isFolder: true)
                }

                Divider()

                // Versions
                SectionHeader("Versions  (\(index.versions.count))")
                LazyVStack(spacing: 8) {
                    ForEach(index.versions) { v in
                        VersionRow(version: v, bundleDir: bundleDir)
                    }
                }
            }
            .padding(24)
        }
    }
}

// MARK: - Section header

struct SectionHeader: View {
    let title: String
    init(_ title: String) { self.title = title }
    var body: some View {
        Text(title)
            .font(.headline)
            .foregroundStyle(.primary)
    }
}

// MARK: - Info grid (click-to-copy)

struct InfoGrid: View {
    let items: [(String, String)]

    var body: some View {
        VStack(spacing: 0) {
            ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                if !item.1.isEmpty {
                    CopyRow(label: item.0, value: item.1)
                    Divider().padding(.leading, 120)
                }
            }
        }
        .background(.quaternary.opacity(0.4))
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
                .font(.callout)
                .foregroundStyle(.secondary)
                .frame(width: 120, alignment: .leading)
                .padding(.leading, 12)

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
                    .frame(width: 32)
            }
            .buttonStyle(.plain)
            .padding(.trailing, 8)
        }
        .padding(.vertical, 7)
    }
}

// MARK: - Reveal button

struct RevealButton: View {
    let label: String
    let icon: String
    let url: URL
    var isFolder: Bool = false

    var body: some View {
        Button {
            if isFolder {
                NSWorkspace.shared.open(url)
            } else {
                NSWorkspace.shared.activateFileViewerSelecting([url])
            }
        } label: {
            Label(label, systemImage: icon)
                .font(.callout)
        }
        .buttonStyle(.bordered)
        .disabled(!FileManager.default.fileExists(atPath: url.path))
    }
}

// MARK: - Version row

struct VersionRow: View {
    let version: VersionEntry
    let bundleDir: URL
    @State private var expanded = false

    private var versionDir: URL {
        bundleDir.appendingPathComponent("versions").appendingPathComponent(version.folder)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Header row
            HStack(spacing: 10) {
                // Type badge
                Text(version.versionType.uppercased())
                    .font(.caption2.bold())
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(badgeColor(version.versionType))
                    .foregroundStyle(.white)
                    .clipShape(Capsule())

                Text(version.displayName)
                    .font(.callout.bold())
                    .lineLimit(1)

                Text(version.loaders.joined(separator: " · "))
                    .font(.caption)
                    .foregroundStyle(.secondary)

                Text(version.gameVersions.prefix(3).joined(separator: ", ")
                     + (version.gameVersions.count > 3 ? "…" : ""))
                    .font(.caption)
                    .foregroundStyle(.secondary)

                Spacer()

                Text(version.published)
                    .font(.caption)
                    .foregroundStyle(.tertiary)

                // Reveal in Finder
                Button {
                    NSWorkspace.shared.activateFileViewerSelecting([versionDir])
                } label: {
                    Image(systemName: "folder.badge.magnifyingglass")
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
                .help("Reveal in Finder")
                .disabled(!FileManager.default.fileExists(atPath: versionDir.path))

                // Expand toggle
                Button {
                    withAnimation(.easeInOut(duration: 0.15)) { expanded.toggle() }
                } label: {
                    Image(systemName: expanded ? "chevron.up" : "chevron.down")
                        .foregroundStyle(.secondary)
                        .frame(width: 20)
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)

            // Expanded detail
            if expanded {
                Divider()
                VStack(alignment: .leading, spacing: 8) {
                    InfoGrid(items: [
                        ("Version #",   version.versionNumber),
                        ("Display name", version.displayName),
                        ("Type",        version.versionType),
                        ("Loaders",     version.loaders.joined(separator: ", ")),
                        ("MC versions", version.gameVersions.joined(separator: ", ")),
                        ("Published",   version.published),
                        ("Modrinth ID", version.versionId),
                        ("JAR file",    version.jar),
                        ("JAR size",    version.jarSize > 0 ? "\(version.jarSize.formatted()) bytes" : "—"),
                    ])

                    if !version.changelog.isEmpty {
                        Text("Changelog")
                            .font(.caption.bold())
                            .foregroundStyle(.secondary)
                        ScrollView {
                            Text(version.changelog)
                                .font(.caption.monospaced())
                                .textSelection(.enabled)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .frame(maxHeight: 120)
                        .background(.quaternary.opacity(0.4))
                        .clipShape(RoundedRectangle(cornerRadius: 6))
                    }
                }
                .padding(12)
            }
        }
        .background(.background)
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(.separator, lineWidth: 0.5)
        )
    }

    private func badgeColor(_ type: String) -> Color {
        switch type.lowercased() {
        case "release": return .green
        case "beta":    return .orange
        case "alpha":   return .red
        default:        return .gray
        }
    }
}

#Preview {
    ContentView()
        .frame(width: 900, height: 700)
}
