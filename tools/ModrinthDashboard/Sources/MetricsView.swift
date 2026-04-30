import SwiftUI

// MARK: - Business Metrics Panel

struct MetricsView: View {
    let metrics: BusinessMetrics
    let bestDownloads: Double
    let avgDownloads: Double

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Analytics & Growth")
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(.white.opacity(0.5))
                .padding(.bottom, 2)

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                kpiCard(
                    title: "MoM Growth",
                    value: metrics.downloadGrowthRate,
                    format: .percent,
                    explanation: "Month-over-month download growth. Positive = accelerating, negative = declining.",
                    higherIsBetter: true
                )
                kpiCard(
                    title: "Conversion Rate",
                    value: metrics.viewToDownloadConversion,
                    format: .percent,
                    explanation: "% of page views that result in a download. Industry benchmark ~15–30%.",
                    higherIsBetter: true
                )
                kpiCard(
                    title: "7d Velocity",
                    value: metrics.downloadVelocity7d,
                    format: .number,
                    explanation: "Average daily downloads over the last 7 days. Your current momentum.",
                    higherIsBetter: true
                )
                kpiCard(
                    title: "30d Velocity",
                    value: metrics.downloadVelocity30d,
                    format: .number,
                    explanation: "Average daily downloads over the last 30 days. Smoothed trend.",
                    higherIsBetter: true
                )
                kpiCard(
                    title: "Retention Rate",
                    value: metrics.downloadRetentionRate,
                    format: .percent,
                    explanation: "Current 7d velocity as % of peak. 100% = still at peak. <30% = fading.",
                    higherIsBetter: true
                )
                kpiCard(
                    title: "CAGR",
                    value: metrics.cagr,
                    format: .percent,
                    explanation: "Compound Annual Growth Rate. Annualised growth from first 30d to last 30d.",
                    higherIsBetter: true
                )
                kpiCard(
                    title: "Rev / Download",
                    value: metrics.revenuePerDownload,
                    format: .currency,
                    explanation: "Average revenue earned per download. Higher = better monetisation.",
                    higherIsBetter: true
                )
                kpiCard(
                    title: "Peak Daily DL",
                    value: metrics.peakDailyDownloads,
                    format: .number,
                    explanation: "Highest single-day download count. Indicates viral/launch potential.",
                    higherIsBetter: true
                )
            }

            // Today's stats
            HStack(spacing: 16) {
                todayStat(label: "Today Downloads", value: metrics.todayDownloads, color: .green)
                todayStat(label: "Today Views",     value: metrics.todayViews,     color: .gray)
                todayStat(label: "Total Downloads", value: metrics.totalDownloads, color: .blue)
                todayStat(label: "Total Revenue",   value: metrics.totalRevenue,   color: Color(red: 0.6, green: 0.3, blue: 0.9))
            }
            .padding(.top, 4)
        }
    }

    // MARK: - KPI Card

    private func kpiCard(
        title: String,
        value: Double,
        format: ValueFormat,
        explanation: String,
        higherIsBetter: Bool
    ) -> some View {
        let score = relativeScore(value: value, higherIsBetter: higherIsBetter)
        let color = scoreColor(score)
        let formatted = formatValue(value, format: format)

        return VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.system(size: 10, weight: .medium))
                .foregroundColor(.white.opacity(0.5))
            Text(formatted)
                .font(.system(size: 18, weight: .bold, design: .rounded))
                .foregroundColor(color)
            Text(explanation)
                .font(.system(size: 9))
                .foregroundColor(.white.opacity(0.35))
                .lineLimit(3)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 10)
                .fill(Color(white: 0.1))
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(color.opacity(0.25), lineWidth: 1)
                )
        )
    }

    // MARK: - Today stat

    private func todayStat(label: String, value: Double, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(.system(size: 9))
                .foregroundColor(.white.opacity(0.4))
            Text(formatValue(value, format: value < 1 ? .currency : .number))
                .font(.system(size: 14, weight: .semibold, design: .rounded))
                .foregroundColor(color)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: - Scoring helpers

    enum ValueFormat { case percent, number, currency }

    private func formatValue(_ v: Double, format: ValueFormat) -> String {
        switch format {
        case .percent:  return String(format: "%.1f%%", v)
        case .number:   return v >= 1000 ? String(format: "%.1fk", v / 1000) : String(format: "%.0f", v)
        case .currency: return String(format: "$%.4f", v)
        }
    }

    /// Returns 0–1 score relative to portfolio average and best
    private func relativeScore(value: Double, higherIsBetter: Bool) -> Double {
        // Use avgModDownloads as the midpoint reference
        // Score 0.5 at average, 1.0 at best, 0.0 at 0
        guard bestDownloads > 0 else { return 0.5 }
        let normalised = value / bestDownloads
        return higherIsBetter ? min(normalised, 1.0) : max(0, 1.0 - normalised)
    }

    /// Red (0) → Orange (0.5) → Green (1)
    private func scoreColor(_ score: Double) -> Color {
        if score < 0.33 {
            return Color(red: 0.9, green: 0.25, blue: 0.25)   // red
        } else if score < 0.66 {
            return Color(red: 0.95, green: 0.6, blue: 0.1)    // orange
        } else {
            return Color(red: 0.2, green: 0.85, blue: 0.4)    // green
        }
    }
}
