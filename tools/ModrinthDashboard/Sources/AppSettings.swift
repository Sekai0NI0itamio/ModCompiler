import Foundation

// MARK: - Persistent settings
// Stored in ~/Library/Preferences/com.itamio.ModrinthDashboard.plist
// via UserDefaults — the standard macOS app preferences location.

final class AppSettings: ObservableObject {
    static let shared = AppSettings()

    private let defaults = UserDefaults.standard
    private let keyUsername = "modrinth_username"
    private let keyToken    = "modrinth_api_token"

    @Published var username: String {
        didSet { defaults.set(username, forKey: keyUsername) }
    }

    @Published var apiToken: String {
        didSet {
            // Store token in UserDefaults.
            // For production use you'd use Keychain, but UserDefaults is
            // sufficient for a local developer tool.
            defaults.set(apiToken, forKey: keyToken)
        }
    }

    private init() {
        username = defaults.string(forKey: "modrinth_username") ?? "Itamio"
        apiToken = defaults.string(forKey: "modrinth_api_token") ?? ""
    }

    func save(username u: String, token t: String) {
        username = u.trimmingCharacters(in: .whitespacesAndNewlines)
        apiToken = t.trimmingCharacters(in: .whitespacesAndNewlines)
        defaults.synchronize()
    }
}
