import SwiftUI

struct ProgressOverlay: View {
    let message: String

    var body: some View {
        ZStack {
            Color.black.opacity(0.3)
                .ignoresSafeArea()

            VStack(spacing: 16) {
                ProgressView()
                    .scaleEffect(1.5)

                Text(message)
                    .font(.body)
                    .foregroundColor(.white)
            }
            .padding(24)
            .background(Color(NSColor.windowBackgroundColor))
            .cornerRadius(12)
            .shadow(radius: 10)
        }
    }
}

#Preview {
    ProgressOverlay(message: "Exporting mod...")
}
