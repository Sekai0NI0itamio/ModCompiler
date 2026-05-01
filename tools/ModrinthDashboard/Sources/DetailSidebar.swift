import SwiftUI

struct DetailSidebar: View {
    @ObservedObject var vm: DashboardViewModel
    @State private var showDownloads = true
    @State private var showViews     = true

    var body: some View {
        Group {
            if vm.selection == .allProjects {
                AllProjectsView(vm: vm)
            } else if let project = vm.selectedProject {
                projectDetail(project)
            } else {
                emptyState
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(white: 0.08))
    }

    // MARK: - Project detail

    private func projectDetail(_ p: ModrinthProject) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                headerSection(p)
                if vm.isLoadingDetail {
                    loadingSection
                } else {
                    if let err = vm.detailError { errorBanner(err) }
                    if let cov = vm.coverage    { coverageSection(cov) }
                    if let aes = vm.aesthetics  { AestheticsView(score: aes, project: p) }
                    if !vm.analyticsPoints.isEmpty { chartSection }
                    if let m = vm.metrics {
                        MetricsView(
                            metrics: m,
                            bestDownloads: vm.bestModDownloads,
                            avgDownloads: vm.avgModDownloads
                        )
                    }
                }
            }
            .padding(16)
        }
    }

    // MARK: - Header

    private func headerSection(_ p: ModrinthProject) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                AsyncIconView(url: p.iconURL, size: 44)
                VStack(alignment: .leading, spacing: 2) {
                    Text(p.title)
                        .font(.system(size: 18, weight: .bold)).foregroundColor(.white)
                    Text(p.slug)
                        .font(.system(size: 11, design: .monospaced)).foregroundColor(.white.opacity(0.4))
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 4) {
                    Link("Open on Modrinth ↗",
                         destination: URL(string: "https://modrinth.com/mod/\(p.slug)")!)
                        .font(.system(size: 11))
                        .foregroundColor(Color(red: 0.1, green: 0.8, blue: 0.5))
                    // Per-mod history refresh
                    Button(action: {
                        Task { await vm.downloadHistory(for: p, forceFullRefetch: false) }
                    }) {
                        Label("Refresh History", systemImage: "arrow.clockwise")
                            .font(.system(size: 9))
                            .foregroundColor(.white.opacity(0.35))
                    }
                    .buttonStyle(.plain)
                    .help("Fetch last 2 days of analytics for this mod")
                }
            }
            Text(p.description)
                .font(.system(size: 12)).foregroundColor(.white.opacity(0.6)).lineLimit(3)
            HStack(spacing: 12) {
                statBadge("↓ \(formatNum(p.downloads))", color: .green)
                statBadge("♥ \(p.followers)", color: .pink)
                statBadge(p.status.capitalized, color: p.status == "approved" ? .green : .orange)
                if let pub = p.publishedDate {
                    statBadge("Since \(yearString(pub))", color: .gray)
                }
            }
        }
    }

    // MARK: - Coverage

    private func coverageSection(_ cov: CoverageInfo) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Version Coverage")
                .font(.system(size: 13, weight: .semibold)).foregroundColor(.white.opacity(0.5))

            HStack(alignment: .firstTextBaseline, spacing: 4) {
                Text(String(format: "%.0f%%", cov.percentage))
                    .font(.system(size: 36, weight: .black, design: .rounded))
                    .foregroundColor(coverageColor(cov.percentage))
                Text("of \(cov.total) targets")
                    .font(.system(size: 12)).foregroundColor(.white.opacity(0.4))
            }

            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 4).fill(Color.white.opacity(0.08)).frame(height: 8)
                    RoundedRectangle(cornerRadius: 4)
                        .fill(coverageColor(cov.percentage))
                        .frame(width: geo.size.width * CGFloat(cov.percentage / 100), height: 8)
                }
            }
            .frame(height: 8)

            HStack(alignment: .top, spacing: 12) {
                versionList("✅ Supported (\(cov.supported.count))",
                            cov.supported.sorted { sortKey($0) < sortKey($1) },
                            Color(red: 0.2, green: 0.85, blue: 0.4))
                versionList("⚠️ Ghost (\(cov.ghost.count))",
                            cov.ghost.sorted { sortKey($0) < sortKey($1) },
                            Color(red: 0.95, green: 0.6, blue: 0.1))
                versionList("❌ Missing (\(cov.missing.count))",
                            cov.missing.sorted { sortKey($0) < sortKey($1) },
                            Color(red: 0.9, green: 0.25, blue: 0.25))
            }
        }
    }

    private func versionList(_ title: String, _ targets: [ManifestTarget], _ color: Color) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.system(size: 10, weight: .semibold)).foregroundColor(color)
            if targets.isEmpty {
                Text("None").font(.system(size: 10)).foregroundColor(.white.opacity(0.25))
            } else {
                ForEach(targets, id: \.self) { t in
                    Text("\(t.version) · \(t.loader)")
                        .font(.system(size: 9, design: .monospaced))
                        .foregroundColor(color.opacity(0.8))
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: - Chart

    private var chartSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Lifetime Analytics")
                .font(.system(size: 13, weight: .semibold)).foregroundColor(.white.opacity(0.5))
            HStack(spacing: 14) {
                ChartLegendToggle(label: "Downloads", color: Color(red: 0.2, green: 0.8, blue: 0.4), isOn: $showDownloads)
                ChartLegendToggle(label: "Views",     color: Color(red: 0.6, green: 0.6, blue: 0.6), isOn: $showViews)
                Spacer()
                Text("\(vm.analyticsPoints.count) data points")
                    .font(.system(size: 10)).foregroundColor(.white.opacity(0.3))
            }
            AnalyticsChartView(
                points: vm.analyticsPoints,
                showDownloads: showDownloads,
                showViews: showViews
            )
            .frame(height: 200)
            .background(RoundedRectangle(cornerRadius: 10).fill(Color(white: 0.07)))
        }
    }

    // MARK: - Misc

    private var loadingSection: some View {
        HStack { Spacer()
            VStack(spacing: 10) {
                ProgressView().scaleEffect(0.8)
                Text("Loading…").font(.system(size: 12)).foregroundColor(.white.opacity(0.4))
            }
            Spacer()
        }.padding(.vertical, 40)
    }

    private var emptyState: some View {
        VStack(spacing: 12) {
            Text("←").font(.system(size: 40)).foregroundColor(.white.opacity(0.15))
            Text("Select a mod to see details")
                .font(.system(size: 14)).foregroundColor(.white.opacity(0.3))
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func errorBanner(_ msg: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: "exclamationmark.triangle.fill").foregroundColor(.orange).font(.system(size: 11))
            VStack(alignment: .leading, spacing: 2) {
                Text(msg).font(.system(size: 11)).foregroundColor(.orange)
                Text("Analytics shown are estimated from total download count.")
                    .font(.system(size: 10)).foregroundColor(.orange.opacity(0.6))
            }
        }
        .padding(10).frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 8).fill(Color.orange.opacity(0.1)))
    }

    private func statBadge(_ text: String, color: Color) -> some View {
        Text(text).font(.system(size: 10, weight: .medium)).foregroundColor(color)
            .padding(.horizontal, 8).padding(.vertical, 3)
            .background(RoundedRectangle(cornerRadius: 6).fill(color.opacity(0.15)))
    }

    private func coverageColor(_ p: Double) -> Color {
        p < 33 ? Color(red: 0.9, green: 0.25, blue: 0.25)
               : p < 66 ? Color(red: 0.95, green: 0.6, blue: 0.1)
               : Color(red: 0.2, green: 0.85, blue: 0.4)
    }

    private func formatNum(_ n: Int) -> String {
        n >= 1000 ? String(format: "%.1fk", Double(n) / 1000) : "\(n)"
    }

    private func yearString(_ d: Date) -> String {
        let f = DateFormatter(); f.dateFormat = "yyyy"; return f.string(from: d)
    }

    private func sortKey(_ t: ManifestTarget) -> String {
        let parts = t.version.split(separator: ".").map { String($0).leftPad(toLength: 4, with: "0") }
        return parts.joined(separator: ".") + t.loader
    }
}

extension String {
    func leftPad(toLength len: Int, with char: Character) -> String {
        guard count < len else { return self }
        return String(repeating: char, count: len - count) + self
    }
}
