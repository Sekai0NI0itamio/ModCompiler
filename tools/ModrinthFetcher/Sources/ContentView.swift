import SwiftUI

// MARK: - App shell

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

            switch vm.state {
            case .idle:    IdlePlaceholder()
            case .running: RunningView(vm: vm)
            case .done(let dir, let index): ResultView(index: index, bundleDir: dir)
            case .failed(let msg):          ErrorView(message: msg)
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
                .font(.body)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            if !vm.runURL.isEmpty {
                Link("View run on GitHub →", destination: URL(string: vm.runURL)!)
                    .font(.body)
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
                .font(.body)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .textSelection(.enabled)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(40)
    }
}

// MARK: - Result — single vertical scroll

struct ResultView: View {
    let index: BundleIndex
    let bundleDir: URL

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 28) {

                // ── Icon + Name header ────────────────────────────────────
                IconNameHeader(index: index, bundleDir: bundleDir)

                // ── Title copy rows ───────────────────────────────────────
                PanelSection(title: "Project Name") {
                    CopyOnlyRow(value: index.title)
                }

                PanelSection(title: "Project Name (slug format)") {
                    CopyOnlyRow(value: index.title.replacingOccurrences(of: " ", with: "-"))
                }

                Divider()

                // ── Small fields ──────────────────────────────────────────
                PanelSection(title: "Project Summary") {
                    CopyOnlyRow(value: index.description)
                }

                PanelSection(title: "Project Tags") {
                    let tags = (index.categories + index.loaders).filter { !$0.isEmpty }
                    CopyOnlyRow(value: tags.joined(separator: ", "))
                }

                PanelSection(title: "Links") {
                    LinksBlock(index: index)
                }

                Divider()

                // ── Description files ─────────────────────────────────────
                PanelSection(title: "Project Description — Markdown") {
                    FileContentCopyRow(
                        icon: "doc.text",
                        label: "description.md",
                        fileURL: bundleDir.appendingPathComponent("description.md")
                    )
                }

                PanelSection(title: "Project Description — HTML") {
                    FileContentCopyRow(
                        icon: "globe",
                        label: "description.html",
                        fileURL: bundleDir.appendingPathComponent("description.html")
                    )
                }

                Divider()

                // ── Gallery ───────────────────────────────────────────────
                PanelSection(title: "Gallery Images  (\(index.gallery.count))") {
                    FolderRevealRow(
                        icon: "photo.on.rectangle",
                        label: "gallery/",
                        folderURL: bundleDir.appendingPathComponent("gallery")
                    )
                }

                Divider()

                // ── Versions ──────────────────────────────────────────────
                PanelSection(title: "Versions  (\(index.versions.count))") {
                    VersionsSection(versions: index.versions, bundleDir: bundleDir)
                }
            }
            .padding(22)
        }
    }
}

// MARK: - Icon + Name header

struct IconNameHeader: View {
    let index: BundleIndex
    let bundleDir: URL

    private var iconURL: URL? {
        guard !index.iconFile.isEmpty else { return nil }
        let u = bundleDir.appendingPathComponent(index.iconFile)
        return FileManager.default.fileExists(atPath: u.path) ? u : nil
    }

    var body: some View {
        HStack(alignment: .center, spacing: 16) {
            // Icon image
            if let url = iconURL, let img = NSImage(contentsOf: url) {
                Image(nsImage: img)
                    .resizable()
                    .interpolation(.high)
                    .frame(width: 64, height: 64)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.secondary.opacity(0.2), lineWidth: 1))
            } else {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color.secondary.opacity(0.12))
                    .frame(width: 64, height: 64)
                    .overlay(Image(systemName: "cube.box").font(.title).foregroundStyle(.tertiary))
            }

            VStack(alignment: .leading, spacing: 4) {
                Text(index.title)
                    .font(.title.bold())
                Text("\(index.downloads.formatted()) downloads  ·  \(index.versionsInBundle) versions")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            // Reveal icon in Finder
            if let url = iconURL {
                Button {
                    NSWorkspace.shared.activateFileViewerSelecting([url])
                } label: {
                    Label("Reveal Icon", systemImage: "folder.badge.magnifyingglass")
                        .font(.body)
                }
                .buttonStyle(.bordered)
            }
        }
    }
}

// MARK: - Links block

struct LinksBlock: View {
    let index: BundleIndex

    private var links: [(String, String)] {
        [
            ("Modrinth", index.modrinthUrl),
            ("Source",   index.sourceUrl),
            ("Issues",   index.issuesUrl),
            ("Wiki",     index.wikiUrl),
            ("Discord",  index.discordUrl),
        ].filter { !$1.isEmpty }
    }

    var body: some View {
        if links.isEmpty {
            Text("No links available")
                .font(.body)
                .foregroundStyle(.tertiary)
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(.quaternaryLabelColor).opacity(0.25))
                .clipShape(RoundedRectangle(cornerRadius: 8))
        } else {
            VStack(spacing: 0) {
                ForEach(Array(links.enumerated()), id: \.offset) { i, link in
                    LinkCopyRow(label: link.0, value: link.1)
                    if i < links.count - 1 {
                        Divider().padding(.leading, 52)
                    }
                }
            }
            .background(Color(.quaternaryLabelColor).opacity(0.25))
            .clipShape(RoundedRectangle(cornerRadius: 8))
        }
    }
}

// MARK: - Versions section (expanded by default, scrollable list)

struct VersionsSection: View {
    let versions: [VersionEntry]
    let bundleDir: URL

    @State private var expanded = true   // open by default
    @State private var ticked: Set<String> = []

    private var versionsDir: URL { bundleDir.appendingPathComponent("versions") }
    private var exists: Bool { FileManager.default.fileExists(atPath: versionsDir.path) }

    var body: some View {
        VStack(spacing: 0) {
            // ── Collapse / expand header ──────────────────────────────────
            Button {
                withAnimation(.easeInOut(duration: 0.15)) { expanded.toggle() }
            } label: {
                HStack(spacing: 10) {
                    Image(systemName: "folder.fill")
                        .foregroundStyle(.secondary)
                    Text("versions/")
                        .font(.body)
                        .foregroundStyle(exists ? .primary : .secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Image(systemName: expanded ? "chevron.down" : "chevron.right")
                        .font(.subheadline.bold())
                        .foregroundStyle(.secondary)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
            }
            .buttonStyle(.plain)
            .disabled(!exists)

            // ── Version list ──────────────────────────────────────────────
            if expanded && exists {
                Divider()
                ScrollView {
                    VStack(spacing: 4) {
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
                    .padding(8)
                }
                // Show 7 rows, then scroll
                .frame(height: min(CGFloat(versions.count) * 46, 7 * 46))
            }
        }
        .background(Color(.quaternaryLabelColor).opacity(0.25))
        .clipShape(RoundedRectangle(cornerRadius: 10))
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
        let u = vDir.appendingPathComponent(v.jar)
        return FileManager.default.fileExists(atPath: u.path) ? u : nil
    }
    private var exists: Bool { FileManager.default.fileExists(atPath: vDir.path) }

    private var mcLoaderLabel: String {
        let mc  = v.gameVersions.first ?? "?"
        let ldr = v.loaders.first ?? "?"
        return "\(mc) · \(ldr)"
    }

    var body: some View {
        HStack(spacing: 10) {
            // Tick circle — front
            Button { ticked.toggle() } label: {
                Image(systemName: ticked ? "checkmark.circle.fill" : "circle")
                    .font(.body)
                    .foregroundStyle(ticked ? Color.green : Color.secondary)
                    .frame(width: 22)
            }
            .buttonStyle(.plain)

            // MC version + loader
            Text(mcLoaderLabel)
                .font(.subheadline.monospacedDigit())
                .foregroundStyle(ticked ? Color.green : Color.secondary)
                .lineLimit(1)
                .frame(width: 120, alignment: .leading)

            // Display name
            Text(v.displayName)
                .font(.body)
                .foregroundStyle(ticked ? Color.green : Color.primary)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)

            // Version number
            Text(v.versionNumber)
                .font(.subheadline.monospacedDigit())
                .foregroundStyle(ticked ? Color.green : Color.secondary)
                .lineLimit(1)

            // Reveal in Finder — auto-ticks
            Button {
                if let jar = jarURL {
                    NSWorkspace.shared.activateFileViewerSelecting([jar])
                } else {
                    NSWorkspace.shared.open(vDir)
                }
                ticked = true
            } label: {
                Image(systemName: "folder.badge.magnifyingglass")
                    .font(.body)
                    .foregroundStyle(ticked ? Color.green : Color.secondary)
                    .frame(width: 26)
            }
            .buttonStyle(.plain)
            .help("Reveal in Finder")
            .disabled(!exists)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 9)
        .background(ticked ? Color.green.opacity(0.12) : Color.clear)
        .clipShape(RoundedRectangle(cornerRadius: 7))
        .overlay(
            RoundedRectangle(cornerRadius: 7)
                .stroke(ticked ? Color.green : Color.clear, lineWidth: 1.5)
        )
        .animation(.easeInOut(duration: 0.12), value: ticked)
    }
}

// MARK: - Panel section wrapper

struct PanelSection<Content: View>: View {
    let title: String
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.subheadline.bold())
                .foregroundStyle(.secondary)
                .textCase(.uppercase)
                .tracking(0.4)
            content()
        }
    }
}

// MARK: - Tick + green row modifier

struct TickedRowStyle: ViewModifier {
    let ticked: Bool
    func body(content: Content) -> some View {
        content
            .background(ticked
                ? Color.green.opacity(0.12)
                : Color(.quaternaryLabelColor).opacity(0.25))
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .overlay(RoundedRectangle(cornerRadius: 8)
                .stroke(ticked ? Color.green : Color.clear, lineWidth: 1.5))
            .animation(.easeInOut(duration: 0.12), value: ticked)
    }
}

extension View {
    func tickedRowStyle(_ ticked: Bool) -> some View {
        modifier(TickedRowStyle(ticked: ticked))
    }
}

// MARK: - Copy-only row

struct CopyOnlyRow: View {
    let value: String
    @State private var ticked = false

    var body: some View {
        HStack(spacing: 8) {
            // Tick
            Button { ticked.toggle() } label: {
                Image(systemName: ticked ? "checkmark.circle.fill" : "circle")
                    .font(.body)
                    .foregroundStyle(ticked ? Color.green : Color.secondary)
                    .frame(width: 22)
            }
            .buttonStyle(.plain)
            .padding(.leading, 8)

            Text(value.isEmpty ? "—" : value)
                .font(.body)
                .textSelection(.enabled)
                .lineLimit(5)
                .foregroundStyle(ticked ? Color.green : Color.primary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.vertical, 10)

            if !value.isEmpty {
                Button {
                    NSPasteboard.general.clearContents()
                    NSPasteboard.general.setString(value, forType: .string)
                    ticked = true
                } label: {
                    Image(systemName: "doc.on.doc")
                        .font(.body)
                        .foregroundStyle(ticked ? Color.green : Color.secondary)
                        .frame(width: 34)
                }
                .buttonStyle(.plain)
                .padding(.trailing, 6)
            }
        }
        .tickedRowStyle(ticked)
    }
}

// MARK: - Link copy row

struct LinkCopyRow: View {
    let label: String
    let value: String
    @State private var ticked = false

    var body: some View {
        HStack(spacing: 8) {
            // Tick
            Button { ticked.toggle() } label: {
                Image(systemName: ticked ? "checkmark.circle.fill" : "circle")
                    .font(.body)
                    .foregroundStyle(ticked ? Color.green : Color.secondary)
                    .frame(width: 22)
            }
            .buttonStyle(.plain)
            .padding(.leading, 8)

            Text(label)
                .font(.subheadline)
                .foregroundStyle(ticked ? Color.green.opacity(0.8) : Color.secondary)
                .frame(width: 58, alignment: .leading)

            Text(value)
                .font(.body.monospaced())
                .textSelection(.enabled)
                .lineLimit(1)
                .truncationMode(.middle)
                .foregroundStyle(ticked ? Color.green : Color.primary)
                .frame(maxWidth: .infinity, alignment: .leading)

            Button {
                NSPasteboard.general.clearContents()
                NSPasteboard.general.setString(value, forType: .string)
                ticked = true
            } label: {
                Image(systemName: "doc.on.doc")
                    .font(.body)
                    .foregroundStyle(ticked ? Color.green : Color.secondary)
                    .frame(width: 34)
            }
            .buttonStyle(.plain)
            .padding(.trailing, 6)
        }
        .padding(.vertical, 8)
    }
}

// MARK: - File content copy row

struct FileContentCopyRow: View {
    let icon: String
    let label: String
    let fileURL: URL
    @State private var ticked = false

    private var exists: Bool { FileManager.default.fileExists(atPath: fileURL.path) }

    var body: some View {
        HStack(spacing: 8) {
            // Tick
            Button { ticked.toggle() } label: {
                Image(systemName: ticked ? "checkmark.circle.fill" : "circle")
                    .font(.body)
                    .foregroundStyle(ticked ? Color.green : Color.secondary)
                    .frame(width: 22)
            }
            .buttonStyle(.plain)
            .padding(.leading, 8)

            Image(systemName: icon)
                .font(.body)
                .foregroundStyle(ticked ? Color.green.opacity(0.8) : Color.secondary)
                .frame(width: 20)

            Text(label)
                .font(.body)
                .foregroundStyle(ticked ? Color.green : (exists ? Color.primary : Color.secondary))
                .frame(maxWidth: .infinity, alignment: .leading)

            Button {
                guard let content = try? String(contentsOf: fileURL, encoding: .utf8) else { return }
                NSPasteboard.general.clearContents()
                NSPasteboard.general.setString(content, forType: .string)
                ticked = true
            } label: {
                Image(systemName: "doc.on.doc")
                    .font(.body)
                    .foregroundStyle(ticked ? Color.green : Color.secondary)
                    .frame(width: 34)
            }
            .buttonStyle(.plain)
            .disabled(!exists)
            .padding(.trailing, 6)
        }
        .padding(.vertical, 10)
        .tickedRowStyle(ticked)
    }
}

// MARK: - Folder reveal row (gallery)

struct FolderRevealRow: View {
    let icon: String
    let label: String
    let folderURL: URL
    private var exists: Bool { FileManager.default.fileExists(atPath: folderURL.path) }

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .font(.body)
                .foregroundStyle(.secondary)
                .frame(width: 20)

            Text(label)
                .font(.body)
                .foregroundStyle(exists ? .primary : .secondary)
                .frame(maxWidth: .infinity, alignment: .leading)

            Button {
                NSWorkspace.shared.open(folderURL)
            } label: {
                Label("Reveal in Finder", systemImage: "folder.badge.magnifyingglass")
                    .font(.body)
            }
            .buttonStyle(.bordered)
            .disabled(!exists)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(Color(.quaternaryLabelColor).opacity(0.25))
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}
