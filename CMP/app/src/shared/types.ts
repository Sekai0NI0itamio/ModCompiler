export interface Manifest {
  cmp_version: 1
  mod_info: ModInfo
  version_info: VersionInfo
  description: Description
  icon: string
  gallery: GalleryImage[]
  files: FilesConfig
  links: LinksConfig
  publishing: PublishingConfig
}

export interface ModInfo {
  name: string
  slug: string
  summary: string
  project_type: ProjectType
  categories: string[]
  additional_categories: string[]
  license_id: string
  license_url: string
  donation_urls: DonationURL[]
}

export type ProjectType = 'mod' | 'modpack' | 'resourcepack' | 'shader' | 'plugin' | 'datapack' | 'minecraft_java_server'

export interface DonationURL {
  id: string
  platform: DonationPlatform
  url: string
}

export type DonationPlatform = 'patreon' | 'bmac' | 'paypal' | 'github' | 'ko-fi' | 'other'

export interface VersionInfo {
  mod_version: string
  loaders: string[]
  client_side: SideType
  server_side: SideType
  minecraft_versions: string[]
  version_type: 'release' | 'beta' | 'alpha'
  changelog: string
  dependencies: VersionDependency[]
  featured: boolean
}

export type SideType = 'required' | 'optional' | 'unsupported' | 'unknown'

export interface VersionDependency {
  project_id: string
  version_id: string
  file_name: string
  dependency_type: 'required' | 'optional' | 'incompatible' | 'embedded'
}

export interface Description {
  body: string
  images: DescriptionImage[]
}

export interface DescriptionImage {
  index: number
  file: string
  caption: string
}

export interface GalleryImage {
  index: number
  file: string
  featured: boolean
  title: string
  description: string
}

export interface FilesConfig {
  jar: string
  source: string
}

export interface LinksConfig {
  issues_url: string
  source_url: string
  wiki_url: string
  discord_url: string
}

export interface PublishingConfig {
  modrinth_project_id: string
  github_owner: string
  github_repo_name: string
  requested_status: 'approved' | 'archived' | 'unlisted' | 'private' | 'draft' | ''
}

export interface BundleEntry {
  path: string
  name: string
  lastModified: string
}

export type PublishStep = 'idle' | 'github-check' | 'github-create' | 'github-push' | 'github-wiki' | 'modrinth-create' | 'modrinth-upload' | 'modrinth-gallery' | 'modrinth-update' | 'complete' | 'error'

export interface PublishLogEntry {
  timestamp: string
  step: PublishStep
  status: 'info' | 'success' | 'warning' | 'error'
  message: string
}

// Modrinth tag types (fetched from API)
export interface ModrinthCategory {
  name: string
  project_type: string
  header: string
  icon: string
}

export interface ModrinthLoader {
  name: string
  supported_project_types: string[]
  icon: string
}

export interface ModrinthGameVersion {
  version: string
  version_type: 'release' | 'snapshot' | 'alpha' | 'beta'
  date: string
  major: boolean
}

export interface ModrinthLicense {
  short: string
  name: string
}

export interface ModrinthDonationPlatform {
  short: string
  name: string
}
