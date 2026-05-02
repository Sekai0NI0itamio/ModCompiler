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
            VStack(spacing: 4) {
                ForEach(index.versions) { v in
                    VersionRevealRow(v: v, bundleDir: bundleDir)
                }
            }
        }

        // Gallery Images
        PanelSection(title: "Gallery Images  (\(index.gallery.count))") {
            let galleryDir = bundleDir.appendingPathComponent("gallery")
            if index.gallery.isEmpty {
                Text("No gallery images")
                    .font(.callout)
                    .foregroundStyle(.tertiary)
                    .padding(.vertical, 8)
                    .padding(.horizontal, 10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(.quaternary.opacity(0.35))
                    .clipShape(RoundedRectangle(cornerRadius: 8))
            } else {
                VStack(spacing: 4) {
                    ForEach(index.gallery, id: \.self) { filename in
                        GalleryRevealRow(
                            filename: filename,
                            fileURL: galleryDir.appendingPathComponent(filename)
                        )
                    }
                }
            }
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

// MARK: - Version reveal row

struct VersionRevealRow: View {
    let v: VersionEntry
    let bundleDir: URL

    private var vDir: URL {
        bundleDir.appendingPathComponent("versions").appendingPathComponent(v.folder)
    }
    private var exists: Bool { FileManager.default.fileExists(atPath: vDir.path) }

    var body: some View {
        HStack(spacing: 8) {
            // Release type badge
            Text(v.versionType.prefix(1).uppercased())
                .font(.caption2.bold())
                .frame(width: 16, height: 16)
                .background(badgeColor)
                .foregroundStyle(.white)
                .clipShape(RoundedRectangle(cornerRadius: 3))

            Text(v.displayName)
                .font(.callout)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)

            Text(v.loaders.prefix(2).joined(separator: "·"))
                .font(.caption2)
                .foregroundStyle(.secondary)

            Button {
                NSWorkspace.shared.activateFileViewerSelecting([vDir])
            } label: {
                Image(systemName: "folder.badge.magnifyingglass")
                    .foregroundStyle(exists ? .secondary : .tertiary)
                    .frame(width: 28)
            }
            .buttonStyle(.plain)
            .help("Reveal in Finder")
            .disabled(!exists)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(.quaternary.opacity(0.25))
        .clipShape(RoundedRectangle(cornerRadius: 6))
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

// MARK: - Gallery reveal row

struct GalleryRevealRow: View {
    let filename: String
    let fileURL: URL
    private var exists: Bool { FileManager.default.fileExists(atPath: fileURL.path) }

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "photo")
                .foregroundStyle(.secondary)
                .frame(width: 18)

            Text(filename)
                .font(.callout)
                .lineLimit(1)
                .truncationMode(.middle)
                .frame(maxWidth: .infinity, alignment: .leading)
                .foregroundStyle(exists ? .primary : .tertiary)

            Button {
                NSWorkspace.shared.activateFileViewerSelecting([fileURL])
            } label: {
                Image(systemName: "folder.badge.magnifyingglass")
                    .foregroundStyle(exists ? .secondary : .tertiary)
                    .frame(width: 28)
            }
            .buttonStyle(.plain)
            .help("Reveal in Finder")
            .disabled(!exists)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(.quaternary.opacity(0.25))
        .clipShape(RoundedRectangle(cornerRadius: 6))
    }
}
