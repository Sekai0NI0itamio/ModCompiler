import SwiftUI

struct ProjectListView: View {
    @ObservedObject var vm: DashboardViewModel

    var body: some View {
        VStack(spacing: 0) {
            toolbar
            Divider().background(Color.white.opacity(0.08))

            if let err = vm.errorMessage { errorBanner(err) }

            if vm.isLoadingHistory {
                historyProgressBar
            }

            ScrollView {
                LazyVStack(spacing: 1) {
                    // "All Projects" pinned row
                    AllProjectsRow(isSelected: vm.selection == .allProjects)
                        .onTapGesture { Task { await vm.loadAllProjects() } }

                    Divider().background(Color.white.opacity(0.06)).padding(.vertical, 2)

                    if vm.projects.isEmpty && !vm.isLoadingList {
                        emptyState
                    } else {
                        ForEach(vm.projects) { project in
                            ProjectRow(
                                project: project,
                                coverage: vm.coveragePercent(for: project),
                                aestheticsScore: vm.aestheticsByProject[project.id]?.score,
                                isSelected: vm.selection == .project(project.id)
                            )
                            .onTapGesture { Task { await vm.loadDetail(for: project) } }
                            .contextMenu {
                                Button("Refresh History") {
                                    Task { await vm.downloadHistory(for: project, forceFullRefetch: false) }
                                }
                                Button("Re-download Full History") {
                                    Task { await vm.downloadHistory(for: project, forceFullRefetch: true) }
                                }
                                Divider()
                                Button("Open on Modrinth") {
                                    NSWorkspace.shared.open(
                                        URL(string: "https://modrinth.com/mod/\(project.slug)")!
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        .background(Color(white: 0.09))
    }

    // MARK: - Toolbar

    private var toolbar: some View {
        HStack(spacing: 8) {
            Text("Mods")
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(.white.opacity(0.7))
            Spacer()
            if vm.isLoadingList {
                ProgressView().scaleEffect(0.6)
            }
            Button(action: { Task { await vm.loadProjects() } }) {
                Image(systemName: "arrow.clockwise")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(.white.opacity(0.6))
            }
            .buttonStyle(.plain)
            .help("Refresh mod list")

            Button(action: { vm.showSettings = true }) {
                Image(systemName: "gearshape")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(.white.opacity(0.6))
            }
            .buttonStyle(.plain)
            .help("Settings")
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(Color(white: 0.1))
    }

    // MARK: - History progress

    private var historyProgressBar: some View {
        VStack(spacing: 4) {
            ProgressView(value: vm.historyProgress)
                .progressViewStyle(.linear)
                .tint(Color(red: 0.2, green: 0.85, blue: 0.4))
                .padding(.horizontal, 12)
            Text("Downloading history \(Int(vm.historyProgress * 100))%")
                .font(.system(size: 9))
                .foregroundColor(.white.opacity(0.4))
        }
        .padding(.vertical, 6)
        .background(Color(white: 0.1))
    }

    private func errorBanner(_ msg: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundColor(.orange).font(.system(size: 11))
            Text(msg)
                .font(.system(size: 11)).foregroundColor(.orange).lineLimit(2)
        }
        .padding(10).frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.orange.opacity(0.1))
    }

    private var emptyState: some View {
        VStack(spacing: 10) {
            Image(systemName: "cube.box")
                .font(.system(size: 32)).foregroundColor(.white.opacity(0.15))
            Text("No mods loaded")
                .font(.system(size: 13)).foregroundColor(.white.opacity(0.3))
            Text("Press ↺ to refresh")
                .font(.system(size: 11)).foregroundColor(.white.opacity(0.2))
        }
        .frame(maxWidth: .infinity).padding(.vertical, 30)
    }
}

// MARK: - All Projects Row

struct AllProjectsRow: View {
    let isSelected: Bool

    var body: some View {
        HStack(spacing: 10) {
            ZStack {
                RoundedRectangle(cornerRadius: 8)
                    .fill(LinearGradient(
                        colors: [Color(red: 0.1, green: 0.6, blue: 0.4), Color(red: 0.05, green: 0.4, blue: 0.7)],
                        startPoint: .topLeading, endPoint: .bottomTrailing
                    ))
                Image(systemName: "chart.bar.xaxis")
                    .font(.system(size: 16))
                    .foregroundColor(.white)
            }
            .frame(width: 36, height: 36)

            VStack(alignment: .leading, spacing: 2) {
                Text("All Projects")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(.white)
                Text("Portfolio · Analytics · Invest")
                    .font(.system(size: 9))
                    .foregroundColor(.white.opacity(0.4))
            }
            Spacer()
            Image(systemName: "chevron.right")
                .font(.system(size: 9))
                .foregroundColor(.white.opacity(0.2))
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(isSelected ? Color(white: 0.16) : Color.clear)
        .contentShape(Rectangle())
    }
}

// MARK: - Project Row

struct ProjectRow: View {
    let project: ModrinthProject
    let coverage: Double
    let aestheticsScore: Double?
    let isSelected: Bool

    private let green = Color(red: 0.2, green: 0.85, blue: 0.4)

    var body: some View {
        HStack(spacing: 10) {
            AsyncIconView(url: project.iconURL, size: 36)

            VStack(alignment: .leading, spacing: 3) {
                Text(project.title)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(.white)
                    .lineLimit(1)
                HStack(spacing: 5) {
                    Text("↓ \(formatNum(project.downloads))")
                        .font(.system(size: 10, design: .monospaced))
                        .foregroundColor(green)
                    coveragePill
                }
            }

            Spacer()

            VStack(alignment: .trailing, spacing: 2) {
                Text(String(format: "%.0f%%", coverage))
                    .font(.system(size: 11, weight: .bold, design: .rounded))
                    .foregroundColor(coverageColor(coverage))
                if let aes = aestheticsScore {
                    Text(String(format: "%.0f%%", aes))
                        .font(.system(size: 9, design: .rounded))
                        .foregroundColor(aestheticsColor(aes))
                }
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(isSelected ? Color(white: 0.16) : Color.clear)
        .contentShape(Rectangle())
    }

    private var coveragePill: some View {
        Text(String(format: "%.0f%%", coverage))
            .font(.system(size: 9))
            .foregroundColor(coverageColor(coverage))
            .padding(.horizontal, 4).padding(.vertical, 1)
            .background(RoundedRectangle(cornerRadius: 3).fill(coverageColor(coverage).opacity(0.15)))
    }

    private func coverageColor(_ p: Double) -> Color {
        p < 33 ? Color(red: 0.9, green: 0.25, blue: 0.25)
               : p < 66 ? Color(red: 0.95, green: 0.6, blue: 0.1)
               : Color(red: 0.2, green: 0.85, blue: 0.4)
    }

    private func aestheticsColor(_ p: Double) -> Color {
        p < 40 ? Color(red: 0.9, green: 0.25, blue: 0.25)
               : p < 70 ? Color(red: 0.95, green: 0.6, blue: 0.1)
               : Color(red: 0.2, green: 0.85, blue: 0.4)
    }

    private func formatNum(_ n: Int) -> String {
        n >= 1000 ? String(format: "%.1fk", Double(n) / 1000) : "\(n)"
    }
}

// MARK: - Async icon

struct AsyncIconView: View {
    let url: URL?
    let size: CGFloat
    @State private var image: NSImage?

    var body: some View {
        Group {
            if let img = image {
                Image(nsImage: img).resizable().aspectRatio(contentMode: .fill)
            } else {
                RoundedRectangle(cornerRadius: size * 0.22)
                    .fill(Color(white: 0.18))
                    .overlay(Image(systemName: "cube.fill")
                        .font(.system(size: size * 0.4))
                        .foregroundColor(.white.opacity(0.2)))
            }
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: size * 0.22))
        .task(id: url) {
            guard let url else { return }
            if let (data, _) = try? await URLSession.shared.data(from: url),
               let img = NSImage(data: data) { image = img }
        }
    }
}
