import SwiftUI

struct MetricsView: View {
    let metrics: BusinessMetrics
    let bestDownloads: Double
    let avgDownloads: Double

    private let green  = Color(red: 0.2, green: 0.85, blue: 0.4)
    private let orange = Color(red: 0.95, green: 0.6, blue: 0.1)
    private let red    = Color(red: 0.9, green: 0.25, blue: 0.25)

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Analytics & Growth")
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(.white.opacity(0.5))

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                kpiCard(
                    title: "Conversion Rate",
                    value: metrics.viewToDownloadConversion,
                    format: .percent,
                    explanation: "% of page views that result in a download. This is the primary quality signal — high conversion means your page convinces visitors. Industry benchmark: 15–30%.",
                    higherIsBetter: true,
                    benchmark: 20.0
                )
                kpiCard(
                    title: "Recent Conversion (30d)",
                    value: metrics.recentConversionRate,
                    format: .percent,
                    explanation: "Conversion rate over the last 30 days. More recent than lifetime — shows current page effectiveness.",
                    higherIsBetter: true,
                    benchmark: 20.0
                )
                kpiCard(
                    title: "MoM Download Growth",
                    value: metrics.downloadGrowthRate,
                    format: .percent,
                    explanation: "Month-over-month download growth. Positive = accelerating, negative = declining.",
                    higherIsBetter: true,
                    benchmark: 0.0
                )
                kpiCard(
                    title: "MoM View Growth",
                    value: metrics.viewGrowthRate,
                    format: .percent,
                    explanation: "Month-over-month page view growth. Growing views = growing awareness.",
                    higherIsBetter: true,
                    benchmark: 0.0
                )
                kpiCard(
                    title: "7d Download Velocity",
                    value: metrics.downloadVelocity7d,
                    format: .number,
                    explanation: "Average daily downloads over the last 7 days. Your current momentum.",
                    higherIsBetter: true,
                    benchmark: avgDownloads / 30
                )
                kpiCard(
                    title: "30d Download Velocity",
                    value: metrics.downloadVelocity30d,
                    format: .number,
                    explanation: "Average daily downloads over the last 30 days. Smoothed trend.",
                    higherIsBetter: true,
                    benchmark: avgDownloads / 30
                )
                kpiCard(
                    title: "Retention Rate",
                    value: metrics.downloadRetentionRate,
                    format: .percent,
                    explanation: "Current 7d velocity as % of peak. 100% = still at peak. <30% = audience has moved on.",
                    higherIsBetter: true,
                    benchmark: 50.0
                )
                kpiCard(
                    title: "CAGR",
                    value: metrics.cagr,
                    format: .percent,
                    explanation: "Compound Annual Growth Rate from first 30d to last 30d. Positive = growing over time.",
                    higherIsBetter: true,
                    benchmark: 0.0
                )
            }

            // Summary bar
            HStack(spacing: 16) {
                summaryTile("Today DL",    value: metrics.todayDownloads,           color: green,  format: .number)
                summaryTile("Today Views", value: metrics.todayViews,               color: .gray,  format: .number)
                summaryTile("Total DL",    value: metrics.totalDownloads,           color: .blue,  format: .number)
                summaryTile("Total Views", value: metrics.totalViews,               color: .gray,  format: .number)
                summaryTile("Conversion",  value: metrics.viewToDownloadConversion, color: orange, format: .percent)
            }
            .padding(.top, 4)
        }
    }

    private func kpiCard(title: String, value: Double, format: ValueFormat,
                         explanation: String, higherIsBetter: Bool, benchmark: Double) -> some View {
        let color = kpiColor(value: value, benchmark: benchmark, higherIsBetter: higherIsBetter)
        return VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.system(size: 10, weight: .medium)).foregroundColor(.white.opacity(0.5))
            Text(formatValue(value, format: format))
                .font(.system(size: 18, weight: .bold, design: .rounded)).foregroundColor(color)
            Text(explanation)
                .font(.system(size: 9)).foregroundColor(.white.opacity(0.35))
                .lineLimit(3).fixedSize(horizontal: false, vertical: true)
        }
        .padding(10).frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 10).fill(Color(white: 0.1))
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(color.opacity(0.25), lineWidth: 1)))
    }

    private func summaryTile(_ label: String, value: Double, color: Color, format: ValueFormat) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.system(size: 9)).foregroundColor(.white.opacity(0.4))
            Text(formatValue(value, format: format))
                .font(.system(size: 13, weight: .semibold, design: .rounded)).foregroundColor(color)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    enum ValueFormat { case percent, number }

    private func formatValue(_ v: Double, format: ValueFormat) -> String {
        switch format {
        case .percent: return String(format: "%.1f%%", v)
        case .number:
            if v >= 1_000_000 { return String(format: "%.1fM", v / 1_000_000) }
            if v >= 1_000     { return String(format: "%.1fk", v / 1_000) }
            return String(format: "%.0f", v)
        }
    }

    /// Colour relative to a benchmark value: red below, orange near, green above
    private func kpiColor(value: Double, benchmark: Double, higherIsBetter: Bool) -> Color {
        if benchmark == 0 {
            // No benchmark — just use sign
            if higherIsBetter {
                return value > 5 ? green : value > 0 ? orange : red
            } else {
                return value < 5 ? green : value < 20 ? orange : red
            }
        }
        let ratio = value / benchmark
        if higherIsBetter {
            if ratio >= 1.2 { return green }
            if ratio >= 0.7 { return orange }
            return red
        } else {
            if ratio <= 0.8 { return green }
            if ratio <= 1.3 { return orange }
            return red
        }
    }
}
