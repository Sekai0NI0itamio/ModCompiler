import SwiftUI

struct ProjectListView: View {
    @ObservedObject var vm: DashboardViewModel

    var body: some View {
        VStack(spacing: 0) {
            // Toolbar
            HStack(spacing: 8) {
                Text("Mods")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(.white.opacity(0.7))
                Spacer()
                if vm.isLoadingList {
                    ProgressView().scaleEffect(0.6)
                }
                Button(action: {
                    Task { await vm.loadProjects() }
                }) {
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

            Divider().background(Color.white.opacity(0.08))

            if let err = vm.errorMessage {
                errorBanner(err)
            }

            if vm.projects.isEmpty && !vm.isLoadingList {
                emptyState
            } else {
                ScrollView {
                    LazyVStack(spacing: 1) {
                        ForEach(vm.projects) { project in
                            ProjectRow(
                                project: project,
                                coverage: vm.coveragePercent(for: project),
                                isSelected: vm.selectedProject?.id == project.id
                            )
                            .onTapGesture {
                                Task { await vm.loadDetail(for: project) }
                            }
                        }
                    }
                }
            }
        }
        .background(Color(white: 0.09))
    }

    private func errorBanner(_ msg: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundColor(.orange)
                .font(.system(size: 11))
            Text(msg)
                .font(.system(size: 11))
                .foregroundColor(.orange)
                .lineLimit(2)
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.orange.opacity(0.1))
    }

    private var emptyState: some View {
        VStack(spacing: 10) {
            Spacer()
            Image(systemName: "cube.box")
                .font(.system(size: 32))
                .foregroundColor(.white.opacity(0.15))
            Text("No mods loaded")
                .font(.system(size: 13))
                .foregroundColor(.white.opacity(0.3))
            Text("Press ↺ to refresh")
                .font(.system(size: 11))
                .foregroundColor(.white.opacity(0.2))
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - Project Row

struct ProjectRow: View {
    let project: ModrinthProject
    let coverage: Double
    let isSelected: Bool

    private let downloadColor = Color(red: 0.2, green: 0.85, blue: 0.4)

    var body: some View {
        HStack(spacing: 10) {
            AsyncIconView(url: project.iconURL, size: 36)

            VStack(alignment: .leading, spacing: 3) {
                Text(project.title)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(.white)
                    .lineLimit(1)

                HStack(spacing: 6) {
                    // Downloads
                    Text("↓ \(formatNum(project.downloads))")
                        .font(.system(size: 10, design: .monospaced))
                        .foregroundColor(downloadColor)

                    // Coverage pill
                    coveragePill
                }
            }

            Spacer()

            // Today indicator (placeholder — real value loaded in detail)
            VStack(alignment: .trailing, spacing: 2) {
                Text(String(format: "%.0f%%", coverage))
                    .font(.system(size: 11, weight: .bold, design: .rounded))
                    .foregroundColor(coverageColor(coverage))
                Text("coverage")
                    .font(.system(size: 8))
                    .foregroundColor(.white.opacity(0.3))
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(
            isSelected
                ? Color(white: 0.16)
                : Color.clear
        )
        .contentShape(Rectangle())
    }

    private var coveragePill: some View {
        let color = coverageColor(coverage)
        return Text(String(format: "%.0f%% covered", coverage))
            .font(.system(size: 9))
            .foregroundColor(color)
            .padding(.horizontal, 5)
            .padding(.vertical, 1)
            .background(RoundedRectangle(cornerRadius: 4).fill(color.opacity(0.15)))
    }

    private func coverageColor(_ pct: Double) -> Color {
        if pct < 33 { return Color(red: 0.9, green: 0.25, blue: 0.25) }
        if pct < 66 { return Color(red: 0.95, green: 0.6, blue: 0.1) }
        return Color(red: 0.2, green: 0.85, blue: 0.4)
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
                Image(nsImage: img)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
            } else {
                RoundedRectangle(cornerRadius: size * 0.22)
                    .fill(Color(white: 0.18))
                    .overlay(
                        Image(systemName: "cube.fill")
                            .font(.system(size: size * 0.4))
                            .foregroundColor(.white.opacity(0.2))
                    )
            }
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: size * 0.22))
        .task(id: url) {
            guard let url else { return }
            if let (data, _) = try? await URLSession.shared.data(from: url),
               let img = NSImage(data: data) {
                image = img
            }
        }
    }
}
