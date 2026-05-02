import Foundation

struct BundleIndex: Codable {
    var title: String
    var slug: String
    var projectId: String
    var description: String
    var clientSide: String
    var serverSide: String
    var license: String
    var categories: [String]
    var loaders: [String]
    var gameVersions: [String]
    var downloads: Int
    var sourceUrl: String
    var issuesUrl: String
    var wikiUrl: String
    var discordUrl: String
    var modrinthUrl: String
    var iconUrl: String
    var iconFile: String
    var gallery: [String]
    var versions: [VersionEntry]
    var totalVersionsOnModrinth: Int
    var versionsInBundle: Int

    enum CodingKeys: String, CodingKey {
        case title, slug, description, license, categories, loaders, downloads, versions, gallery
        case projectId      = "project_id"
        case clientSide     = "client_side"
        case serverSide     = "server_side"
        case gameVersions   = "game_versions"
        case sourceUrl      = "source_url"
        case issuesUrl      = "issues_url"
        case wikiUrl        = "wiki_url"
        case discordUrl     = "discord_url"
        case modrinthUrl    = "modrinth_url"
        case iconUrl        = "icon_url"
        case iconFile       = "icon_file"
        case totalVersionsOnModrinth = "total_versions_on_modrinth"
        case versionsInBundle        = "versions_in_bundle"
    }
}

struct VersionEntry: Codable, Identifiable {
    var id: String { versionId }
    var index: Int
    var folder: String
    var versionId: String
    var versionNumber: String
    var displayName: String
    var versionType: String
    var loaders: [String]
    var gameVersions: [String]
    var published: String
    var jar: String
    var jarSize: Int
    var changelog: String

    enum CodingKeys: String, CodingKey {
        case index, folder, loaders, published, jar, changelog
        case versionId     = "version_id"
        case versionNumber = "version_number"
        case displayName   = "display_name"
        case versionType   = "version_type"
        case gameVersions  = "game_versions"
        case jarSize       = "jar_size"
    }
}

enum FetchState {
    case idle
    case running(message: String)
    case done(bundleDir: URL, index: BundleIndex)
    case failed(String)
}
