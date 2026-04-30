import SwiftUI

struct DetailSidebar: View {
    @ObservedObject var vm: DashboardViewModel

    @State private var showDownloads = true
    @State private var showViews     = true
    @State private var showRevenue   = true

    var body: some View {
        Group {
            if let project = vm.selectedProject {
                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        headerSection(project)
                        if vm.isLoadingDetail {
                            loadingSection
                        } else {
                            if let cov = vm.coverage {
                                coverageSection(cov)
                            }
                            if !vm.analyticsPoints.isEmpty {
                                chartSection
                            }
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
            } else {
                emptyState
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(white: 0.08))
    }

    // MARK: - Header

    private func headerSection(_ p: ModrinthProject) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                AsyncIconView(url: p.iconURL, size: 44)
                VStack(alignment: .leading, spacing: 2) {
                    Text(p.title)
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                    Text(p.slug)
                        .font(.system(size: 11, design: .monospaced))
                        .foregroundColor(.white.opacity(0.4))
                }
                Spacer()
                Link("Open on Modrinth ↗", destination: URL(string: "https://modrinth.com/mod/\(p.slug)")!)
                    .font(.system(size: 11))
                    .foregroundColor(Color(red: 0.1, green: 0.8, blue: 0.5))
            }
            Text(p.description)
                .font(.system(size: 12))
                .foregroundColor(.white.opacity(0.6))
                .lineLimit(3)

            HStack(spacing: 16) {
                statBadge("↓ \(formatNum(p.downloads))", color: .green)
                statBadge("♥ \(p.followers)", color: .pink)
                statBadge(p.status.capitalized, color: p.status == "approved" ? .green : .orange)
            }
        }
    }

    // MARK: - Coverage

    private func coverageSection(_ cov: CoverageInfo) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionHeader("Version Coverage")

            // Big percentage
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                Text(String(format: "%.0f%%", cov.percentage))
                    .font(.system(size: 36, weight: .black, design: .rounded))
                    .foregroundColor(coverageColor(cov.percentage))
                Text("of \(cov.total) targets")
                    .font(.system(size: 12))
                    .foregroundColor(.white.opacity(0.4))
            }

            // Progress bar
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 4).fill(Color.white.opacity(0.08)).frame(height: 8)
                    RoundedRectangle(cornerRadius: 4)
                        .fill(coverageColor(cov.percentage))
                        .frame(width: geo.size.width * CGFloat(cov.percentage / 100), height: 8)
                }
            }
            .frame(height: 8)

            // Three lists
            HStack(alignment: .top, spacing: 12) {
                versionList(title: "✅ Supported (\(cov.supported.count))",
                            targets: cov.supported.sorted { sortKey($0) < sortKey($1) },
                            color: Color(red: 0.2, green: 0.85, blue: 0.4))
                versionList(title: "⚠️ Ghost (\(cov.ghost.count))",
                            targets: cov.ghost.sorted { sortKey($0) < sortKey($1) },
                            color: Color(red: 0.95, green: 0.6, blue: 0.1))
                versionList(title: "❌ Missing (\(cov.missing.count))",
                            targets: cov.missing.sorted { sortKey($0) < sortKey($1) },
                            color: Color(red: 0.9, green: 0.25, blue: 0.25))
            }
        }
    }

    private func versionList(title: String, targets: [ManifestTarget], color: Color) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.system(size: 10, weight: .semibold))
                .foregroundColor(color)
            if targets.isEmpty {
                Text("None")
                    .font(.system(size: 10))
                    .foregroundColor(.white.opacity(0.25))
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
            sectionHeader("Lifetime Analytics")

            // Legend toggles
            HStack(spacing: 14) {
                ChartLegendToggle(label: "Downloads", color: Color(red: 0.2, green: 0.8, blue: 0.4), isOn: $showDownloads)
                ChartLegendToggle(label: "Views",     color: Color(red: 0.6, green: 0.6, blue: 0.6), isOn: $showViews)
                ChartLegendToggle(label: "Revenue",   color: Color(red: 0.6, green: 0.3, blue: 0.9), isOn: $showRevenue)
                Spacer()
                Text("\(vm.analyticsPoints.count) data points")
                    .font(.system(size: 10))
                    .foregroundColor(.white.opacity(0.3))
            }

            AnalyticsChartView(
                points: vm.analyticsPoints,
                showDownloads: showDownloads,
                showViews: showViews,
                showRevenue: showRevenue
            )
            .frame(height: 200)
            .background(RoundedRectangle(cornerRadius: 10).fill(Color(white: 0.07)))
        }
    }

    // MARK: - Loading

    private var loadingSection: some View {
        HStack {
            Spacer()
            VStack(spacing: 10) {
                ProgressView()
                    .scaleEffect(0.8)
                Text("Loading analytics…")
                    .font(.system(size: 12))
                    .foregroundColor(.white.opacity(0.4))
            }
            Spacer()
        }
        .padding(.vertical, 40)
    }

    // MARK: - Empty state

    private var emptyState: some View {
        VStack(spacing: 12) {
            Text("←")
                .font(.system(size: 40))
                .foregroundColor(.white.opacity(0.15))
            Text("Select a mod to see details")
                .font(.system(size: 14))
                .foregroundColor(.white.opacity(0.3))
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - Helpers

    private func sectionHeader(_ title: String) -> some View {
        Text(title)
            .font(.system(size: 13, weight: .semibold))
            .foregroundColor(.white.opacity(0.5))
    }

    private func statBadge(_ text: String, color: Color) -> some View {
        Text(text)
            .font(.system(size: 10, weight: .medium))
            .foregroundColor(color)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(RoundedRectangle(cornerRadius: 6).fill(color.opacity(0.15)))
    }

    private func coverageColor(_ pct: Double) -> Color {
        if pct < 33 { return Color(red: 0.9, green: 0.25, blue: 0.25) }
        if pct < 66 { return Color(red: 0.95, green: 0.6, blue: 0.1) }
        return Color(red: 0.2, green: 0.85, blue: 0.4)
    }

    private func formatNum(_ n: Int) -> String {
        n >= 1000 ? String(format: "%.1fk", Double(n) / 1000) : "\(n)"
    }

    private func sortKey(_ t: ManifestTarget) -> String {
        // Pad version numbers for correct sort: 1.9 < 1.10 < 1.21
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
