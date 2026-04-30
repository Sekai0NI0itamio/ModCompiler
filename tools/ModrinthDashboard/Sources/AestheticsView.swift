import SwiftUI

struct AestheticsView: View {
    let score: AestheticsScore
    let project: ModrinthProject

    private let green  = Color(red: 0.2, green: 0.85, blue: 0.4)
    private let orange = Color(red: 0.95, green: 0.6, blue: 0.1)
    private let red    = Color(red: 0.9, green: 0.25, blue: 0.25)

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // Header
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text("Page Quality")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(.white.opacity(0.5))
                Spacer()
                Text(String(format: "%.0f%%", score.score))
                    .font(.system(size: 22, weight: .black, design: .rounded))
                    .foregroundColor(scoreColor(score.score))
                Text("aesthetics")
                    .font(.system(size: 10))
                    .foregroundColor(.white.opacity(0.3))
            }

            // Progress bar
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 4).fill(Color.white.opacity(0.08)).frame(height: 6)
                    RoundedRectangle(cornerRadius: 4)
                        .fill(scoreColor(score.score))
                        .frame(width: geo.size.width * CGFloat(score.score / 100), height: 6)
                }
            }
            .frame(height: 6)

            // Checklist
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 6) {
                ForEach(score.checks, id: \.name) { check in
                    checkRow(check)
                }
            }

            // Missing items callout
            if !score.missing.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Missing (\(score.missing.count)) — add these to reach 100%:")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundColor(orange)
                    ForEach(score.missing, id: \.name) { check in
                        HStack(spacing: 6) {
                            Image(systemName: "plus.circle")
                                .font(.system(size: 9))
                                .foregroundColor(orange)
                            VStack(alignment: .leading, spacing: 1) {
                                Text(check.name)
                                    .font(.system(size: 10, weight: .medium))
                                    .foregroundColor(orange)
                                Text(check.tip)
                                    .font(.system(size: 9))
                                    .foregroundColor(.white.opacity(0.4))
                            }
                        }
                    }
                }
                .padding(10)
                .background(RoundedRectangle(cornerRadius: 8).fill(orange.opacity(0.08)))
            }
        }
    }

    private func checkRow(_ check: AestheticsScore.Check) -> some View {
        HStack(spacing: 6) {
            Image(systemName: check.passed ? "checkmark.circle.fill" : "xmark.circle")
                .font(.system(size: 11))
                .foregroundColor(check.passed ? green : red.opacity(0.6))
            Text(check.name)
                .font(.system(size: 10))
                .foregroundColor(check.passed ? .white.opacity(0.75) : .white.opacity(0.35))
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func scoreColor(_ pct: Double) -> Color {
        if pct < 40 { return red }
        if pct < 70 { return orange }
        return green
    }
}
