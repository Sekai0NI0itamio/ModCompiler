import SwiftUI

struct AllProjectsView: View {
    @ObservedObject var vm: DashboardViewModel

    @State private var showDownloads = true
    @State private var showViews     = true

    private let green  = Color(red: 0.2, green: 0.85, blue: 0.4)
    private let orange = Color(red: 0.95, green: 0.6, blue: 0.1)
    private let gray   = Color(red: 0.6, green: 0.6, blue: 0.6)

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                portfolioHeader
                if vm.isLoadingDetail || vm.isLoadingHistory {
                    loadingView
                } else {
                    if !vm.portfolioPoints.isEmpty { portfolioChart }
                    if let m = vm.portfolioMetrics { portfolioStats(m) }
                    investmentSection
                }
            }
            .padding(16)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(white: 0.08))
    }

    // MARK: - Header

    private var portfolioHeader: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 10)
                    .fill(LinearGradient(
                        colors: [Color(red: 0.1, green: 0.6, blue: 0.4), Color(red: 0.05, green: 0.4, blue: 0.7)],
                        startPoint: .topLeading, endPoint: .bottomTrailing))
                Image(systemName: "chart.bar.xaxis").font(.system(size: 22)).foregroundColor(.white)
            }
            .frame(width: 44, height: 44)

            VStack(alignment: .leading, spacing: 2) {
                Text("All Projects").font(.system(size: 18, weight: .bold)).foregroundColor(.white)
                Text("\(vm.projects.count) mods · Portfolio overview")
                    .font(.system(size: 11)).foregroundColor(.white.opacity(0.4))
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 4) {
                Button(action: { Task { await vm.refreshAllHistory() } }) {
                    Label("Refresh History", systemImage: "arrow.clockwise")
                        .font(.system(size: 10, weight: .medium)).foregroundColor(green)
                }
                .buttonStyle(.plain)
                Button(action: { Task {
                    await HistoryStore.shared.deleteAll()
                    await vm.downloadAllHistory()
                }}) {
                    Label("Full Re-download", systemImage: "arrow.triangle.2.circlepath")
                        .font(.system(size: 10)).foregroundColor(.white.opacity(0.4))
                }
                .buttonStyle(.plain)
            }
        }
    }

    // MARK: - Loading

    private var loadingView: some View {
        VStack(spacing: 12) {
            ProgressView(value: vm.historyProgress).progressViewStyle(.linear).tint(green)
            Text(vm.isLoadingHistory
                 ? "Downloading history… \(Int(vm.historyProgress * 100))%"
                 : "Loading analytics…")
                .font(.system(size: 12)).foregroundColor(.white.opacity(0.4))
        }
        .padding(.vertical, 30)
    }

    // MARK: - Chart

    private var portfolioChart: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Portfolio Lifetime Analytics")
                .font(.system(size: 13, weight: .semibold)).foregroundColor(.white.opacity(0.5))
            HStack(spacing: 14) {
                ChartLegendToggle(label: "Downloads", color: green, isOn: $showDownloads)
                ChartLegendToggle(label: "Views",     color: gray,  isOn: $showViews)
                Spacer()
                Text("\(vm.portfolioPoints.count) days").font(.system(size: 10)).foregroundColor(.white.opacity(0.3))
            }
            AnalyticsChartView(points: vm.portfolioPoints, showDownloads: showDownloads, showViews: showViews)
                .frame(height: 220)
                .background(RoundedRectangle(cornerRadius: 10).fill(Color(white: 0.07)))
        }
    }

    // MARK: - Portfolio stats

    private func portfolioStats(_ m: BusinessMetrics) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Portfolio Metrics")
                .font(.system(size: 13, weight: .semibold)).foregroundColor(.white.opacity(0.5))
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()),
                                GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                statCard("Total Downloads",  value: m.totalDownloads,           format: .number,  color: green)
                statCard("Total Views",      value: m.totalViews,               format: .number,  color: gray)
                statCard("Conversion Rate",  value: m.viewToDownloadConversion, format: .percent, color: conversionColor(m.viewToDownloadConversion))
                statCard("Today Downloads",  value: m.todayDownloads,           format: .number,  color: .blue)
                statCard("7d Velocity",      value: m.downloadVelocity7d,       format: .number,  color: green)
                statCard("MoM DL Growth",    value: m.downloadGrowthRate,       format: .percent, color: growthColor(m.downloadGrowthRate))
                statCard("MoM View Growth",  value: m.viewGrowthRate,           format: .percent, color: growthColor(m.viewGrowthRate))
                statCard("30d Velocity",     value: m.downloadVelocity30d,      format: .number,  color: green.opacity(0.7))
            }
        }
    }

    private func statCard(_ title: String, value: Double, format: StatFormat, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.system(size: 9, weight: .medium)).foregroundColor(.white.opacity(0.4))
            Text(formatStat(value, format: format))
                .font(.system(size: 16, weight: .bold, design: .rounded)).foregroundColor(color)
        }
        .padding(10).frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 8).fill(Color(white: 0.1)))
    }

    // MARK: - Investment section

    private var investmentSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text("Investment Recommendations")
                    .font(.system(size: 13, weight: .semibold)).foregroundColor(.white.opacity(0.5))
                Spacer()
                Text("Ranked by conversion rate · velocity · coverage")
                    .font(.system(size: 10)).foregroundColor(.white.opacity(0.25))
            }
            if vm.recommendations.isEmpty {
                Text("Load project details to generate recommendations")
                    .font(.system(size: 11)).foregroundColor(.white.opacity(0.3)).padding(.vertical, 20)
            } else {
                ForEach(Array(vm.recommendations.prefix(5).enumerated()), id: \.element.id) { rank, rec in
                    InvestmentCard(rec: rec, rank: rank + 1)
                }
            }
        }
    }

    // MARK: - Helpers

    enum StatFormat { case number, percent }

    private func formatStat(_ v: Double, format: StatFormat) -> String {
        switch format {
        case .number:
            if v >= 1_000_000 { return String(format: "%.1fM", v / 1_000_000) }
            if v >= 1_000     { return String(format: "%.1fk", v / 1_000) }
            return String(format: "%.0f", v)
        case .percent: return String(format: "%.1f%%", v)
        }
    }

    private func growthColor(_ v: Double) -> Color {
        v > 5 ? Color(red: 0.2, green: 0.85, blue: 0.4)
              : v > 0 ? Color(red: 0.95, green: 0.6, blue: 0.1)
              : Color(red: 0.9, green: 0.25, blue: 0.25)
    }

    private func conversionColor(_ v: Double) -> Color {
        v >= 20 ? Color(red: 0.2, green: 0.85, blue: 0.4)
                : v >= 8 ? Color(red: 0.95, green: 0.6, blue: 0.1)
                : Color(red: 0.9, green: 0.25, blue: 0.25)
    }
}

// MARK: - Investment Card

struct InvestmentCard: View {
    let rec: InvestmentRecommendation
    let rank: Int

    private let green  = Color(red: 0.2, green: 0.85, blue: 0.4)
    private let orange = Color(red: 0.95, green: 0.6, blue: 0.1)
    private let purple = Color(red: 0.6, green: 0.3, blue: 0.9)

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            // Header
            HStack(spacing: 10) {
                ZStack {
                    Circle().fill(rankColor.opacity(0.2)).frame(width: 28, height: 28)
                    Text("#\(rank)").font(.system(size: 10, weight: .black)).foregroundColor(rankColor)
                }
                AsyncIconView(url: rec.project.iconURL, size: 32)
                VStack(alignment: .leading, spacing: 1) {
                    Text(rec.project.title).font(.system(size: 13, weight: .semibold)).foregroundColor(.white)
                    Text(rec.reason).font(.system(size: 10)).foregroundColor(.white.opacity(0.5)).lineLimit(2)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 1) {
                    Text(String(format: "%.0f", rec.score))
                        .font(.system(size: 20, weight: .black, design: .rounded)).foregroundColor(rankColor)
                    Text("score").font(.system(size: 8)).foregroundColor(.white.opacity(0.3))
                }
            }

            // Conversion rate highlight — the primary signal
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 1) {
                    Text("Conversion Rate").font(.system(size: 8)).foregroundColor(.white.opacity(0.35))
                    Text(String(format: "%.1f%%", rec.conversionRate))
                        .font(.system(size: 16, weight: .black, design: .rounded))
                        .foregroundColor(conversionColor(rec.conversionRate))
                }
                VStack(alignment: .leading, spacing: 1) {
                    Text("views → downloads").font(.system(size: 8)).foregroundColor(.white.opacity(0.25))
                    Text(conversionLabel(rec.conversionRate))
                        .font(.system(size: 9, weight: .semibold))
                        .foregroundColor(conversionColor(rec.conversionRate))
                }
                Spacer()
                // Missing versions badge
                if rec.missingVersions > 0 {
                    VStack(alignment: .trailing, spacing: 1) {
                        Text("\(rec.missingVersions)").font(.system(size: 14, weight: .black, design: .rounded)).foregroundColor(orange)
                        Text("missing versions").font(.system(size: 8)).foregroundColor(.white.opacity(0.3))
                    }
                }
            }
            .padding(8)
            .background(RoundedRectangle(cornerRadius: 6).fill(Color(white: 0.07)))

            // Score bars
            HStack(spacing: 8) {
                scorePill("Conversion", value: rec.conversionScore,  color: conversionColor(rec.conversionRate))
                scorePill("Velocity",   value: rec.velocityScore,    color: green)
                scorePill("Coverage ↑", value: rec.coverageGapScore, color: orange)
                scorePill("Page ↑",     value: 100 - rec.aestheticsScore, color: purple)
            }

            // Actions
            if !rec.actions.isEmpty {
                VStack(alignment: .leading, spacing: 3) {
                    Text("Recommended actions:")
                        .font(.system(size: 9, weight: .semibold)).foregroundColor(.white.opacity(0.35))
                    ForEach(rec.actions.prefix(4), id: \.self) { action in
                        HStack(spacing: 5) {
                            Image(systemName: "arrow.right.circle.fill")
                                .font(.system(size: 9)).foregroundColor(rankColor.opacity(0.7))
                            Text(action).font(.system(size: 10)).foregroundColor(.white.opacity(0.65))
                        }
                    }
                }
            }
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 12).fill(Color(white: 0.1))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(rankColor.opacity(0.2), lineWidth: 1)))
    }

    private func conversionColor(_ v: Double) -> Color {
        v >= 20 ? Color(red: 0.2, green: 0.85, blue: 0.4)
                : v >= 8 ? Color(red: 0.95, green: 0.6, blue: 0.1)
                : Color(red: 0.9, green: 0.25, blue: 0.25)
    }

    private func conversionLabel(_ v: Double) -> String {
        if v >= 30 { return "🔥 Excellent" }
        if v >= 20 { return "✅ Strong" }
        if v >= 10 { return "📈 Average" }
        if v >= 5  { return "⚠️ Below avg" }
        return "❌ Low"
    }

    private var rankColor: Color {
        switch rank {
        case 1: return Color(red: 1.0, green: 0.84, blue: 0.0)
        case 2: return Color(red: 0.75, green: 0.75, blue: 0.75)
        case 3: return Color(red: 0.8, green: 0.5, blue: 0.2)
        default: return Color(red: 0.2, green: 0.85, blue: 0.4)
        }
    }

    private func scorePill(_ label: String, value: Double, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.system(size: 8)).foregroundColor(.white.opacity(0.35))
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 2).fill(Color.white.opacity(0.06)).frame(height: 4)
                    RoundedRectangle(cornerRadius: 2).fill(color)
                        .frame(width: geo.size.width * CGFloat(min(value, 100) / 100), height: 4)
                }
            }
            .frame(height: 4)
            Text(String(format: "%.0f%%", min(value, 100)))
                .font(.system(size: 8, design: .monospaced)).foregroundColor(color)
        }
        .frame(maxWidth: .infinity)
    }
}
