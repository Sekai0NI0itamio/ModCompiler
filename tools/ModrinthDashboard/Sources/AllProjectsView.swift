import SwiftUI

struct AllProjectsView: View {
    @ObservedObject var vm: DashboardViewModel

    @State private var showDownloads = true
    @State private var showViews     = true
    @State private var showRevenue   = true

    private let green  = Color(red: 0.2, green: 0.85, blue: 0.4)
    private let purple = Color(red: 0.6, green: 0.3, blue: 0.9)
    private let gray   = Color(red: 0.6, green: 0.6, blue: 0.6)

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                portfolioHeader
                if vm.isLoadingDetail || vm.isLoadingHistory {
                    loadingView
                } else {
                    if !vm.portfolioPoints.isEmpty {
                        portfolioChart
                    }
                    if let m = vm.portfolioMetrics {
                        portfolioStats(m)
                    }
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
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                ZStack {
                    RoundedRectangle(cornerRadius: 10)
                        .fill(LinearGradient(
                            colors: [Color(red: 0.1, green: 0.6, blue: 0.4), Color(red: 0.05, green: 0.4, blue: 0.7)],
                            startPoint: .topLeading, endPoint: .bottomTrailing
                        ))
                    Image(systemName: "chart.bar.xaxis")
                        .font(.system(size: 22)).foregroundColor(.white)
                }
                .frame(width: 44, height: 44)

                VStack(alignment: .leading, spacing: 2) {
                    Text("All Projects")
                        .font(.system(size: 18, weight: .bold)).foregroundColor(.white)
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

            // Real payout balance from Modrinth payouts API
            if let bal = vm.payoutBalance {
                payoutBalanceCard(bal)
            }
        }
    }

    private func payoutBalanceCard(_ bal: PayoutBalance) -> some View {
        HStack(spacing: 0) {
            balanceTile("Available",  value: bal.available,         color: green,  note: "Ready to withdraw")
            Divider().background(Color.white.opacity(0.08)).frame(height: 40)
            balanceTile("Pending",    value: bal.pending,           color: .orange, note: "In 30-day hold")
            Divider().background(Color.white.opacity(0.08)).frame(height: 40)
            balanceTile("Withdrawn",  value: bal.withdrawnLifetime, color: gray,   note: "All time")
            Divider().background(Color.white.opacity(0.08)).frame(height: 40)
            balanceTile("Total Earned", value: bal.totalEarned,     color: purple, note: "Available + Pending + Withdrawn")
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 10)
                .fill(Color(white: 0.1))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(green.opacity(0.2), lineWidth: 1))
        )
    }

    private func balanceTile(_ label: String, value: Double, color: Color, note: String) -> some View {
        VStack(alignment: .center, spacing: 2) {
            Text(label)
                .font(.system(size: 9, weight: .medium)).foregroundColor(.white.opacity(0.4))
            Text(String(format: "$%.2f", value))
                .font(.system(size: 16, weight: .black, design: .rounded)).foregroundColor(color)
            Text(note)
                .font(.system(size: 8)).foregroundColor(.white.opacity(0.25))
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: - Loading

    private var loadingView: some View {
        VStack(spacing: 12) {
            ProgressView(value: vm.historyProgress)
                .progressViewStyle(.linear)
                .tint(green)
            Text(vm.isLoadingHistory
                 ? "Downloading history… \(Int(vm.historyProgress * 100))%"
                 : "Loading analytics…")
                .font(.system(size: 12))
                .foregroundColor(.white.opacity(0.4))
        }
        .padding(.vertical, 30)
    }

    // MARK: - Portfolio chart

    private var portfolioChart: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Portfolio Lifetime Analytics")
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(.white.opacity(0.5))

            HStack(spacing: 14) {
                ChartLegendToggle(label: "Downloads", color: green,  isOn: $showDownloads)
                ChartLegendToggle(label: "Views",     color: gray,   isOn: $showViews)
                ChartLegendToggle(label: "Revenue",   color: purple, isOn: $showRevenue)
                Spacer()
                Text("\(vm.portfolioPoints.count) days")
                    .font(.system(size: 10))
                    .foregroundColor(.white.opacity(0.3))
            }

            AnalyticsChartView(
                points: vm.portfolioPoints,
                showDownloads: showDownloads,
                showViews: showViews,
                showRevenue: showRevenue
            )
            .frame(height: 220)
            .background(RoundedRectangle(cornerRadius: 10).fill(Color(white: 0.07)))
        }
    }

    // MARK: - Portfolio stats

    private func portfolioStats(_ m: BusinessMetrics) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Portfolio Metrics")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(.white.opacity(0.5))
                Spacer()
                // Clarify that analytics revenue ≠ payout balance
                if vm.payoutBalance != nil {
                    Text("Revenue figures are Modrinth's internal accounting units — see balance above for actual USD")
                        .font(.system(size: 9))
                        .foregroundColor(.white.opacity(0.25))
                        .multilineTextAlignment(.trailing)
                        .frame(maxWidth: 220)
                }
            }

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                statCard("Total Downloads", value: m.totalDownloads,           format: .number,        color: green)
                statCard("Total Views",     value: m.totalViews,               format: .number,        color: gray)
                statCard("Today Downloads", value: m.todayDownloads,           format: .number,        color: .blue)
                statCard("7d Velocity",     value: m.downloadVelocity7d,       format: .number,        color: green)
                statCard("MoM Growth",      value: m.downloadGrowthRate,       format: .percent,       color: growthColor(m.downloadGrowthRate))
                statCard("Conversion",      value: m.viewToDownloadConversion, format: .percent,       color: .orange)
                statCard("30d Velocity",    value: m.downloadVelocity30d,      format: .number,        color: green.opacity(0.7))
                statCard("$/view (API)",    value: m.revenuePerView,           format: .microCurrency, color: purple)
            }
        }
    }

    private func statCard(_ title: String, value: Double, format: StatFormat, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.system(size: 9, weight: .medium))
                .foregroundColor(.white.opacity(0.4))
            Text(formatStat(value, format: format))
                .font(.system(size: 16, weight: .bold, design: .rounded))
                .foregroundColor(color)
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 8).fill(Color(white: 0.1)))
    }

    // MARK: - Investment section

    private var investmentSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text("Investment Recommendations")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(.white.opacity(0.5))
                Spacer()
                Text("Ranked by ROI potential")
                    .font(.system(size: 10))
                    .foregroundColor(.white.opacity(0.25))
            }

            if vm.recommendations.isEmpty {
                Text("Load project details to generate recommendations")
                    .font(.system(size: 11))
                    .foregroundColor(.white.opacity(0.3))
                    .padding(.vertical, 20)
            } else {
                ForEach(Array(vm.recommendations.prefix(5).enumerated()), id: \.element.id) { rank, rec in
                    InvestmentCard(rec: rec, rank: rank + 1)
                }
            }
        }
    }

    // MARK: - Helpers

    enum StatFormat { case number, percent, currency, microCurrency }

    private func formatStat(_ v: Double, format: StatFormat) -> String {
        switch format {
        case .number:
            if v >= 1_000_000 { return String(format: "%.1fM", v / 1_000_000) }
            if v >= 1_000     { return String(format: "%.1fk", v / 1_000) }
            return String(format: "%.0f", v)
        case .percent:       return String(format: "%.1f%%", v)
        case .currency:      return v == 0 ? "$0.00" : String(format: "$%.2f", v)
        case .microCurrency:
            if v == 0        { return "$0" }
            if v >= 0.01     { return String(format: "$%.4f", v) }
            if v >= 0.0001   { return String(format: "$%.5f", v) }
            return String(format: "$%.7f", v)
        }
    }

    private func growthColor(_ v: Double) -> Color {
        if v > 10  { return Color(red: 0.2, green: 0.85, blue: 0.4) }
        if v > 0   { return Color(red: 0.95, green: 0.6, blue: 0.1) }
        return Color(red: 0.9, green: 0.25, blue: 0.25)
    }
}

// MARK: - Investment Card

struct InvestmentCard: View {
    let rec: InvestmentRecommendation
    let rank: Int

    private let green  = Color(red: 0.2, green: 0.85, blue: 0.4)
    private let orange = Color(red: 0.95, green: 0.6, blue: 0.1)
    private let red    = Color(red: 0.9, green: 0.25, blue: 0.25)
    private let purple = Color(red: 0.6, green: 0.3, blue: 0.9)

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            // Header row
            HStack(spacing: 10) {
                ZStack {
                    Circle().fill(rankColor.opacity(0.2)).frame(width: 28, height: 28)
                    Text("#\(rank)")
                        .font(.system(size: 10, weight: .black)).foregroundColor(rankColor)
                }
                AsyncIconView(url: rec.project.iconURL, size: 32)
                VStack(alignment: .leading, spacing: 1) {
                    Text(rec.project.title)
                        .font(.system(size: 13, weight: .semibold)).foregroundColor(.white)
                    Text(rec.reason)
                        .font(.system(size: 10)).foregroundColor(.white.opacity(0.5)).lineLimit(2)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 1) {
                    Text(String(format: "%.0f", rec.score))
                        .font(.system(size: 20, weight: .black, design: .rounded)).foregroundColor(rankColor)
                    Text("score").font(.system(size: 8)).foregroundColor(.white.opacity(0.3))
                }
            }

            // Revenue per view — only show when we have real API revenue data
            if rec.hasRealRevenue {
                HStack(spacing: 12) {
                    revenueTag(label: "$/view",     value: rec.revenuePerView,     threshold: (low: 0.00001, high: 0.0001))
                    revenueTag(label: "$/download", value: rec.revenuePerDownload, threshold: (low: 0.0001,  high: 0.001))
                    Spacer()
                    if rec.revenuePerView > 0.0001 {
                        Text("💰 High-value traffic")
                            .font(.system(size: 9, weight: .semibold))
                            .foregroundColor(Color(red: 1.0, green: 0.84, blue: 0.0))
                    } else if rec.revenuePerView > 0.00001 {
                        Text("📈 Monetising")
                            .font(.system(size: 9, weight: .medium))
                            .foregroundColor(Color(red: 0.95, green: 0.6, blue: 0.1))
                    } else {
                        Text("💡 Low monetisation")
                            .font(.system(size: 9))
                            .foregroundColor(.white.opacity(0.4))
                    }
                }
                .padding(8)
                .background(RoundedRectangle(cornerRadius: 6).fill(Color(white: 0.07)))
            } else {
                // No revenue data — explain why
                HStack(spacing: 6) {
                    Image(systemName: "info.circle")
                        .font(.system(size: 9)).foregroundColor(.white.opacity(0.3))
                    Text(rec.project.isMonetized
                         ? "No revenue data yet — add API token with PAYOUTS_READ scope"
                         : "Not monetised — ranked by velocity and coverage only")
                        .font(.system(size: 9)).foregroundColor(.white.opacity(0.3))
                }
                .padding(8)
                .background(RoundedRectangle(cornerRadius: 6).fill(Color(white: 0.07)))
            }

            // Score bars
            HStack(spacing: 8) {
                if rec.hasRealRevenue {
                    scorePill("$/view", value: rec.revenueScore, color: Color(red: 0.9, green: 0.8, blue: 0.2))
                }
                scorePill("Velocity",   value: rec.velocityScore,                              color: green)
                scorePill("Coverage ↑", value: Double(rec.missingVersions) / 68.0 * 100,      color: orange)
                scorePill("Page ↑",     value: 100 - rec.aestheticsScore,                     color: purple)
            }

            // Action items
            if !rec.actions.isEmpty {
                VStack(alignment: .leading, spacing: 3) {
                    Text("Recommended actions:")
                        .font(.system(size: 9, weight: .semibold)).foregroundColor(.white.opacity(0.35))
                    ForEach(rec.actions.prefix(4), id: \.self) { action in
                        HStack(spacing: 5) {
                            Image(systemName: "arrow.right.circle.fill")
                                .font(.system(size: 9)).foregroundColor(rankColor.opacity(0.7))
                            Text(action)
                                .font(.system(size: 10)).foregroundColor(.white.opacity(0.65))
                        }
                    }
                }
            }
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color(white: 0.1))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(rankColor.opacity(0.2), lineWidth: 1))
        )
    }

    private func revenueTag(label: String, value: Double, threshold: (low: Double, high: Double)) -> some View {
        let color: Color = value >= threshold.high
            ? Color(red: 0.2, green: 0.85, blue: 0.4)
            : value >= threshold.low
                ? Color(red: 0.95, green: 0.6, blue: 0.1)
                : Color(red: 0.6, green: 0.6, blue: 0.6)

        let formatted: String
        if value == 0 { formatted = "$0" }
        else if value < 0.00001 { formatted = String(format: "$%.7f", value) }
        else if value < 0.001   { formatted = String(format: "$%.5f", value) }
        else                    { formatted = String(format: "$%.4f", value) }

        return VStack(alignment: .leading, spacing: 1) {
            Text(label).font(.system(size: 8)).foregroundColor(.white.opacity(0.35))
            Text(formatted)
                .font(.system(size: 11, weight: .bold, design: .monospaced))
                .foregroundColor(color)
        }
    }

    private var rankColor: Color {
        switch rank {
        case 1: return Color(red: 1.0, green: 0.84, blue: 0.0)   // gold
        case 2: return Color(red: 0.75, green: 0.75, blue: 0.75) // silver
        case 3: return Color(red: 0.8, green: 0.5, blue: 0.2)    // bronze
        default: return Color(red: 0.2, green: 0.85, blue: 0.4)
        }
    }

    private func scorePill(_ label: String, value: Double, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(.system(size: 8))
                .foregroundColor(.white.opacity(0.35))
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 2).fill(Color.white.opacity(0.06)).frame(height: 4)
                    RoundedRectangle(cornerRadius: 2)
                        .fill(color)
                        .frame(width: geo.size.width * CGFloat(min(value, 100) / 100), height: 4)
                }
            }
            .frame(height: 4)
            Text(String(format: "%.0f%%", min(value, 100)))
                .font(.system(size: 8, design: .monospaced))
                .foregroundColor(color)
        }
        .frame(maxWidth: .infinity)
    }
}
