import SwiftUI

struct AnalyticsChartView: View {
    let points: [AnalyticsPoint]
    let showDownloads: Bool
    let showViews: Bool

    @State private var hoverIndex: Int? = nil
    @State private var hoverLocation: CGPoint = .zero

    private let downloadColor = Color(red: 0.2, green: 0.8, blue: 0.4)
    private let viewColor     = Color(red: 0.6, green: 0.6, blue: 0.6)

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .topLeading) {
                gridLines(in: geo.size)
                Canvas { ctx, size in
                    drawLine(ctx: ctx, size: size, keyPath: \.downloads, color: downloadColor, visible: showDownloads)
                    drawLine(ctx: ctx, size: size, keyPath: \.views,     color: viewColor,     visible: showViews)
                }
                if let idx = hoverIndex, idx < points.count {
                    tooltipView(for: points[idx], at: hoverLocation, in: geo.size)
                }
                Color.clear
                    .contentShape(Rectangle())
                    .onContinuousHover { phase in
                        switch phase {
                        case .active(let loc):
                            hoverLocation = loc
                            hoverIndex = indexAt(x: loc.x, width: geo.size.width)
                        case .ended:
                            hoverIndex = nil
                        }
                    }
            }
        }
    }

    private func gridLines(in size: CGSize) -> some View {
        Canvas { ctx, sz in
            for i in 0...4 {
                let y = sz.height * CGFloat(i) / 4.0
                var path = Path()
                path.move(to: CGPoint(x: 0, y: y))
                path.addLine(to: CGPoint(x: sz.width, y: y))
                ctx.stroke(path, with: .color(Color.white.opacity(0.06)), lineWidth: 1)
            }
        }
    }

    private func drawLine(ctx: GraphicsContext, size: CGSize, keyPath: KeyPath<AnalyticsPoint, Double>, color: Color, visible: Bool) {
        guard visible, points.count > 1 else { return }
        let maxVal = points.map { $0[keyPath: keyPath] }.max() ?? 1
        guard maxVal > 0 else { return }
        let w = size.width, h = size.height, pad: CGFloat = 8
        var path = Path()
        for (i, pt) in points.enumerated() {
            let x = w * CGFloat(i) / CGFloat(points.count - 1)
            let y = h - pad - (h - 2 * pad) * CGFloat(pt[keyPath: keyPath] / maxVal)
            if i == 0 { path.move(to: CGPoint(x: x, y: y)) }
            else       { path.addLine(to: CGPoint(x: x, y: y)) }
        }
        var fill = path
        fill.addLine(to: CGPoint(x: w, y: h))
        fill.addLine(to: CGPoint(x: 0, y: h))
        fill.closeSubpath()
        ctx.fill(fill, with: .color(color.opacity(0.12)))
        ctx.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
    }

    private func indexAt(x: CGFloat, width: CGFloat) -> Int? {
        guard points.count > 1, width > 0 else { return nil }
        let idx = Int((x / width * CGFloat(points.count - 1)).rounded())
        return max(0, min(idx, points.count - 1))
    }

    private func tooltipView(for pt: AnalyticsPoint, at loc: CGPoint, in size: CGSize) -> some View {
        let df = DateFormatter(); df.dateStyle = .medium; df.timeStyle = .none
        var lines: [(String, Color)] = []
        if showDownloads { lines.append(("↓ \(Int(pt.downloads)) downloads", downloadColor)) }
        if showViews     { lines.append(("👁 \(Int(pt.views)) views", viewColor)) }
        if pt.views > 0  {
            let conv = pt.downloads / pt.views * 100
            lines.append((String(format: "⚡ %.1f%% conversion", conv), Color(red: 0.95, green: 0.6, blue: 0.1)))
        }
        let tipW: CGFloat = 170, tipH: CGFloat = CGFloat(lines.count * 20 + 28)
        var tipX = loc.x + 12, tipY = loc.y - tipH / 2
        if tipX + tipW > size.width { tipX = loc.x - tipW - 8 }
        if tipY < 0 { tipY = 4 }
        if tipY + tipH > size.height { tipY = size.height - tipH - 4 }
        return VStack(alignment: .leading, spacing: 3) {
            Text(df.string(from: pt.date))
                .font(.system(size: 10, weight: .semibold)).foregroundColor(.white.opacity(0.7))
            ForEach(Array(lines.enumerated()), id: \.offset) { _, pair in
                Text(pair.0).font(.system(size: 11, weight: .medium, design: .monospaced)).foregroundColor(pair.1)
            }
        }
        .padding(8)
        .background(RoundedRectangle(cornerRadius: 8).fill(Color(white: 0.12)).shadow(color: .black.opacity(0.5), radius: 8))
        .frame(width: tipW)
        .position(x: tipX + tipW / 2, y: tipY + tipH / 2)
    }
}

struct ChartLegendToggle: View {
    let label: String
    let color: Color
    @Binding var isOn: Bool
    var body: some View {
        Button(action: { isOn.toggle() }) {
            HStack(spacing: 5) {
                RoundedRectangle(cornerRadius: 2)
                    .fill(isOn ? color : Color.gray.opacity(0.3))
                    .frame(width: 14, height: 3)
                Text(label).font(.system(size: 11)).foregroundColor(isOn ? .white.opacity(0.85) : .gray)
            }
        }
        .buttonStyle(.plain)
    }
}
