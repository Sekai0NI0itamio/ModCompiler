import SwiftUI

struct SettingsView: View {
    @ObservedObject var vm: DashboardViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var draftUsername: String = ""
    @State private var draftToken: String = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            Text("Settings")
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(.white)

            // Username
            VStack(alignment: .leading, spacing: 6) {
                Text("Modrinth Username")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(.white.opacity(0.5))
                TextField("e.g. Itamio", text: $draftUsername)
                    .textFieldStyle(.plain)
                    .font(.system(size: 13))
                    .foregroundColor(.white)
                    .padding(8)
                    .background(RoundedRectangle(cornerRadius: 8).fill(Color(white: 0.15)))
            }

            // Token
            VStack(alignment: .leading, spacing: 6) {
                Text("Modrinth API Token (PAT)")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(.white.opacity(0.5))
                SecureField("mrp_…", text: $draftToken)
                    .textFieldStyle(.plain)
                    .font(.system(size: 13, design: .monospaced))
                    .foregroundColor(.white)
                    .padding(8)
                    .background(RoundedRectangle(cornerRadius: 8).fill(Color(white: 0.15)))
                Text("Required for analytics (downloads/views/revenue over time). Generate at modrinth.com/settings/pats — needs ANALYTICS_READ and PAYOUTS_READ scopes.")
                    .font(.system(size: 10))
                    .foregroundColor(.white.opacity(0.35))
                    .fixedSize(horizontal: false, vertical: true)
            }

            Spacer()

            HStack {
                Button("Cancel") { dismiss() }
                    .buttonStyle(.plain)
                    .foregroundColor(.white.opacity(0.5))
                Spacer()
                Button("Save & Refresh") {
                    vm.username = draftUsername.isEmpty ? "Itamio" : draftUsername
                    vm.apiToken = draftToken
                    dismiss()
                    Task { await vm.loadProjects() }
                }
                .buttonStyle(.plain)
                .foregroundColor(Color(red: 0.1, green: 0.8, blue: 0.5))
                .font(.system(size: 13, weight: .semibold))
            }
        }
        .padding(24)
        .frame(width: 400, height: 340)
        .background(Color(white: 0.1))
        .onAppear {
            draftUsername = vm.username
            draftToken    = vm.apiToken
        }
    }
}
