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

// MARK: - Result (two-column layout)

struct ResultView: View {
    let index: BundleIndex
    let bundleDir: URL

    var body: some View {
        HStack(alignment: .top, spacing: 0) {
            // ── Left column ───────────────────────────────────────────────
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    LeftColumn(index: index, bundleDir: bundleDir)
                }
                .padding(18)
            }
            .frame(minWidth: 280, maxWidth: .infinity)

            Divider()

            // ── Right column ──────────────────────────────────────────────
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    RightColumn(index: index, bundleDir: bundleDir)
                }
                .padding(18)
            }
            .frame(minWidth: 280, maxWidth: .infinity)
        }
    }
}

// MARK: - Left column

struct LeftColumn: View {
    let index: BundleIndex
    let bundleDir: URL

    var body: some View {
        // Project Name
        PanelSection(title: "Project Name") {
            CopyOnlyRow(value: index.title)
        }

        // Project Summary
        PanelSection(title: "Project Summary") {
            CopyOnlyRow(value: index.description)
        }

        // Project Tags
        PanelSection(title: "Project Tags") {
            let tags = (index.categories + index.loaders).filter { !$0.isEmpty }
            CopyOnlyRow(value: tags.joined(separator: ", "))
        }

        // Links
        PanelSection(title: "Links") {
            VStack(spacing: 0) {
                let links: [(String, String)] = [
                    ("Modrinth",  index.modrinthUrl),
                    ("Source",    index.sourceUrl),
                    ("Issues",    index.issuesUrl),
                    ("Wiki",      index.wikiUrl),
                    ("Discord",   index.discordUrl),
                ].filter { !$1.isEmpty }

                if links.isEmpty {
                    Text("No links available")
                        .font(.callout)
                        .foregroundStyle(.tertiary)
                        .padding(.vertical, 8)
                        .padding(.horizontal, 10)
                } else {
                    ForEach(Array(links.enumerated()), id: \.offset) { i, link in
                        LinkCopyRow(label: link.0, value: link.1)
                        if i < links.count - 1 {
                            Divider().padding(.leading, 70)
                        }
                    }
                }
            }
            .background(.quaternary.opacity(0.35))
            .clipShape(RoundedRectangle(cornerRadius: 8))
        }
    }
}

// MARK: - Right column

struct RightColumn: View {
    let index: BundleIndex
    let bundleDir: URL

    var body: some View {
        // Description in Markdown
        PanelSection(title: "Project Description in Markdown") {
            FileContentCopyRow(
                icon: "doc.text",
                label: "description.md",
                fileURL: bundleDir.appendingPathComponent("description.md")
            )
        }

        // Description in HTML
        PanelSection(title: "Project Description in HTML") {
            FileContentCopyRow(
                icon: "globe",
                label: "description.html",
                fileURL: bundleDir.appendingPathComponent("description.html")
            )
        }

        // Version Folders
        PanelSection(title: "Version Folders  (\(index.versions.count))") {
            VersionsExpandableSection(versions: index.versions, bundleDir: bundleDir)
        }

        // Gallery Images
        PanelSection(title: "Gallery Images  (\(index.gallery.count))") {
            FolderRevealRow(
                icon: "photo.on.rectangle",
                label: "gallery/",
                folderURL: bundleDir.appendingPathComponent("gallery")
            )
        }
    }
}

// MARK: - Panel section wrapper

struct PanelSection<Content: View>: View {
    let title: String
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.caption.bold())
                .foregroundStyle(.secondary)
                .textCase(.uppercase)
                .tracking(0.5)
            content()
        }
    }
}

// MARK: - Copy-only row (single value, copy button)

struct CopyOnlyRow: View {
    let value: String
    @State private var copied = false

    var body: some View {
        HStack(spacing: 0) {
            Text(value.isEmpty ? "—" : value)
                .font(.callout)
                .textSelection(.enabled)
                .lineLimit(4)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 10)
                .padding(.vertical, 8)

            if !value.isEmpty {
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
                .padding(.trailing, 4)
            }
        }
        .background(.quaternary.opacity(0.35))
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

// MARK: - Link copy row (label + URL, copy button)

struct LinkCopyRow: View {
    let label: String
    let value: String
    @State private var copied = false

    var body: some View {
        HStack(spacing: 0) {
            Text(label)
                .font(.caption)
                .foregroundStyle(.secondary)
                .frame(width: 60, alignment: .leading)
                .padding(.leading, 10)

            Text(value)
                .font(.callout.monospaced())
                .textSelection(.enabled)
                .lineLimit(1)
                .truncationMode(.middle)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 6)

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
            .padding(.trailing, 4)
        }
        .padding(.vertical, 6)
    }
}

// MARK: - File content copy row (reads file, copies contents)

struct FileContentCopyRow: View {
    let icon: String
    let label: String
    let fileURL: URL
    @State private var copied = false

    private var exists: Bool { FileManager.default.fileExists(atPath: fileURL.path) }

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .foregroundStyle(.secondary)
                .frame(width: 18)

            Text(label)
                .font(.callout)
                .foregroundStyle(exists ? .primary : .tertiary)
                .frame(maxWidth: .infinity, alignment: .leading)

            Button {
                guard let content = try? String(contentsOf: fileURL, encoding: .utf8) else { return }
                NSPasteboard.general.clearContents()
                NSPasteboard.general.setString(content, forType: .string)
                copied = true
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { copied = false }
            } label: {
                Image(systemName: copied ? "checkmark" : "doc.on.doc")
                    .foregroundStyle(copied ? .green : .secondary)
                    .frame(width: 32)
            }
            .buttonStyle(.plain)
            .disabled(!exists)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background(.quaternary.opacity(0.35))
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

// MARK: - Versions expandable section

struct VersionsExpandableSection: View {
    let versions: [VersionEntry]
    let bundleDir: URL

    @State private var expanded = false
    // Persisted ticked state keyed by version folder name
    @State private var ticked: Set<String> = []

    private var versionsDir: URL { bundleDir.appendingPathComponent("versions") }
    private var exists: Bool { FileManager.default.fileExists(atPath: versionsDir.path) }

    var body: some View {
        VStack(spacing: 0) {
            // ── Header row (always visible) ───────────────────────────────
            Button {
                withAnimation(.easeInOut(duration: 0.15)) { expanded.toggle() }
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: "folder")
                        .foregroundStyle(.secondary)
                        .frame(width: 18)

                    Text("versions/")
                        .font(.callout)
                        .foregroundStyle(exists ? .primary : .tertiary)
                        .frame(maxWidth: .infinity, alignment: .leading)

                    Image(systemName: expanded ? "chevron.down" : "chevron.right")
                        .font(.caption.bold())
                        .foregroundStyle(.secondary)
                        .frame(width: 16)
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 8)
            }
            .buttonStyle(.plain)
            .disabled(!exists)

            // ── Expanded list ─────────────────────────────────────────────
            if expanded && exists {
                Divider()
                ScrollView {
                    VStack(spacing: 3) {
                        ForEach(versions) { v in
                            VersionItemRow(
                                v: v,
                                bundleDir: bundleDir,
                                ticked: Binding(
                                    get: { ticked.contains(v.folder) },
                                    set: { on in
                                        if on { ticked.insert(v.folder) }
                                        else  { ticked.remove(v.folder) }
                                    }
                                )
                            )
                        }
                    }
                    .padding(6)
                }
                .frame(height: min(CGFloat(versions.count) * 38, 7 * 38))
            }
        }
        .background(.quaternary.opacity(0.35))
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

// MARK: - Version item row

struct VersionItemRow: View {
    let v: VersionEntry
    let bundleDir: URL
    @Binding var ticked: Bool

    private var vDir: URL {
        bundleDir.appendingPathComponent("versions").appendingPathComponent(v.folder)
    }
    private var jarURL: URL? {
        guard !v.jar.isEmpty else { return nil }
        return vDir.appendingPathComponent(v.jar)
    }
    private var exists: Bool { FileManager.default.fileExists(atPath: vDir.path) }

    // "1.21.1 · forge" from the folder name / version data
    private var mcLoaderLabel: String {
        let mc  = v.gameVersions.first ?? "?"
        let ldr = v.loaders.first ?? "?"
        return "\(mc) · \(ldr)"
    }

    var body: some View {
        HStack(spacing: 8) {
            // MC version + loader
            Text(mcLoaderLabel)
                .font(.caption.monospacedDigit())
                .foregroundStyle(ticked ? .white.opacity(0.85) : .secondary)
                .lineLimit(1)
                .frame(width: 110, alignment: .leading)

            // Mod display name
            Text(v.displayName)
                .font(.callout)
                .foregroundStyle(ticked ? .white : .primary)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)

            // Version number
            Text(v.versionNumber)
                .font(.caption.monospacedDigit())
                .foregroundStyle(ticked ? .white.opacity(0.85) : .secondary)
                .lineLimit(1)

            // Reveal in Finder
            Button {
                let target = jarURL ?? vDir
                if jarURL != nil {
                    NSWorkspace.shared.activateFileViewerSelecting([target])
                } else {
                    NSWorkspace.shared.open(target)
                }
                ticked = true
            } label: {
                Image(systemName: "folder.badge.magnifyingglass")
                    .foregroundStyle(ticked ? .white.opacity(0.9) : .secondary)
                    .frame(width: 24)
            }
            .buttonStyle(.plain)
            .help("Reveal in Finder")
            .disabled(!exists)

            // Tick toggle
            Button {
                ticked.toggle()
            } label: {
                Image(systemName: ticked ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(ticked ? Color.white : Color.secondary)
                    .frame(width: 20)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 7)
        .background(ticked ? Color.green : Color.clear)
        .clipShape(RoundedRectangle(cornerRadius: 6))
        .overlay(
            RoundedRectangle(cornerRadius: 6)
                .stroke(ticked ? Color.green : Color.clear, lineWidth: 1.5)
        )
        .animation(.easeInOut(duration: 0.12), value: ticked)
    }
}

// MARK: - Folder reveal row (single button, opens a folder)

struct FolderRevealRow: View {
    let icon: String
    let label: String
    let folderURL: URL
    private var exists: Bool { FileManager.default.fileExists(atPath: folderURL.path) }

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .foregroundStyle(.secondary)
                .frame(width: 18)

            Text(label)
                .font(.callout)
                .foregroundStyle(exists ? .primary : .tertiary)
                .frame(maxWidth: .infinity, alignment: .leading)

            Button {
                NSWorkspace.shared.open(folderURL)
            } label: {
                Label("Reveal in Finder", systemImage: "folder.badge.magnifyingglass")
                    .font(.callout)
            }
            .buttonStyle(.bordered)
            .disabled(!exists)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background(.quaternary.opacity(0.35))
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}
