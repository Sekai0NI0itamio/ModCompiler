import SwiftUI

struct SettingsView: View {
    @ObservedObject var vm: DashboardViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var draftUsername: String = ""
    @State private var draftToken:    String = ""
    @State private var saved = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {

            // Title bar
            HStack {
                Text("Settings")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(.white)
                Spacer()
                Button(action: { dismiss() }) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 16))
                        .foregroundColor(.white.opacity(0.3))
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 24)
            .padding(.top, 20)
            .padding(.bottom, 16)

            Divider().background(Color.white.opacity(0.08))

            ScrollView {
                VStack(alignment: .leading, spacing: 20) {

                    // Username
                    field(
                        label: "Modrinth Username",
                        hint: "The username whose mods are shown in the list.",
                        content: {
                            TextField("e.g. Itamio", text: $draftUsername)
                                .styledInput()
                        }
                    )

                    // Token
                    field(
                        label: "API Token (Personal Access Token)",
                        hint: "Required for real analytics data (downloads/views/revenue per day).\nGenerate at modrinth.com/settings/pats — enable ANALYTICS_READ and PAYOUTS_READ scopes.\nWithout a token, analytics are estimated from total download count.",
                        content: {
                            SecureField("mrp_…", text: $draftToken)
                                .styledInput()
                        }
                    )

                    // Token format note
                    HStack(spacing: 6) {
                        Image(systemName: "info.circle")
                            .font(.system(size: 10))
                            .foregroundColor(.blue.opacity(0.7))
                        Text("Token is stored in ~/Library/Preferences/com.itamio.ModrinthDashboard.plist")
                            .font(.system(size: 10))
                            .foregroundColor(.white.opacity(0.3))
                    }

                    if saved {
                        HStack(spacing: 6) {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(.green)
                                .font(.system(size: 12))
                            Text("Saved! Refreshing mod list…")
                                .font(.system(size: 12))
                                .foregroundColor(.green)
                        }
                        .transition(.opacity)
                    }
                }
                .padding(24)
            }

            Divider().background(Color.white.opacity(0.08))

            // Action buttons
            HStack(spacing: 12) {
                Button("Cancel") {
                    dismiss()
                }
                .buttonStyle(SecondaryButtonStyle())

                Spacer()

                Button("Save & Refresh") {
                    saveAndRefresh()
                }
                .buttonStyle(PrimaryButtonStyle())
                .keyboardShortcut(.return, modifiers: .command)
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 16)
        }
        .frame(width: 440)
        .background(Color(white: 0.1))
        .onAppear {
            draftUsername = vm.settings.username
            draftToken    = vm.settings.apiToken
        }
    }

    // MARK: - Save

    private func saveAndRefresh() {
        let u = draftUsername.trimmingCharacters(in: .whitespacesAndNewlines)
        let t = draftToken.trimmingCharacters(in: .whitespacesAndNewlines)

        // Persist to UserDefaults (~/Library/Preferences/)
        vm.settings.save(username: u.isEmpty ? "Itamio" : u, token: t)

        withAnimation { saved = true }

        // Dismiss and reload after a short delay so user sees the confirmation
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
            dismiss()
            Task { await vm.loadProjects() }
        }
    }

    // MARK: - Field builder

    @ViewBuilder
    private func field<Content: View>(
        label: String,
        hint: String,
        @ViewBuilder content: () -> Content
    ) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .font(.system(size: 11, weight: .semibold))
                .foregroundColor(.white.opacity(0.6))
            content()
            Text(hint)
                .font(.system(size: 10))
                .foregroundColor(.white.opacity(0.35))
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

// MARK: - Input style

extension View {
    func styledInput() -> some View {
        self
            .textFieldStyle(.plain)
            .font(.system(size: 13))
            .foregroundColor(.white)
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .background(
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color(white: 0.16))
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Color.white.opacity(0.1), lineWidth: 1)
                    )
            )
    }
}

// MARK: - Button styles

struct PrimaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 13, weight: .semibold))
            .foregroundColor(.black)
            .padding(.horizontal, 18)
            .padding(.vertical, 8)
            .background(
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color(red: 0.1, green: 0.85, blue: 0.5).opacity(configuration.isPressed ? 0.7 : 1))
            )
    }
}

struct SecondaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 13))
            .foregroundColor(.white.opacity(0.6))
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background(
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color(white: 0.18).opacity(configuration.isPressed ? 0.5 : 1))
            )
    }
}
