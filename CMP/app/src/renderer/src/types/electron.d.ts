import type { ModrinthCategory, ModrinthLoader, ModrinthGameVersion, ModrinthLicense, ModrinthDonationPlatform } from '../../../shared/types'

export interface DefaultPaths {
  modcompilerRoot: string
  cmpDir: string
  bundleDraftsDir: string
  bundlePublishedDir: string
  modDevDir: string
  toBeUploadedDir: string
}

export interface ElectronAPI {
  // App paths
  appDefaultPaths: () => Promise<DefaultPaths>

  // Bundle operations
  bundleCreate: (path: string, name: string) => Promise<any>
  bundleOpen: (path: string) => Promise<any>
  bundleSave: (path: string, manifest: any) => Promise<void>
  bundleRecent: () => Promise<any[]>
  bundleDeleteFromIndex: (path: string) => Promise<void>
  bundleReadFileAsBase64: (path: string) => Promise<string>
  bundleCopyFile: (src: string, bundle: string, dest: string) => Promise<void>
  bundleCopyDir: (src: string, bundle: string, dest: string) => Promise<void>
  bundleScanDrafts: () => Promise<Array<{ path: string; name: string; lastModified: string; status: string }>>
  bundleMoveToPublished: (path: string) => Promise<{ success: boolean; newPath: string }>

  // Dialog
  dialogOpenFile: (options?: any) => Promise<string | null>
  dialogOpenDirectory: (options?: any) => Promise<string | null>
  dialogSaveFile: (options?: any) => Promise<string | null>

  // GitHub
  githubCheck: () => Promise<{ authenticated: boolean; error?: string; owner?: string }>
  githubCreate: (config: any) => Promise<any>
  githubPush: (config: any) => Promise<void>
  githubWiki: (config: any) => Promise<any>

  // Modrinth
  modrinthCreateProject: (config: any) => Promise<any>
  modrinthUploadVersion: (config: any) => Promise<any>
  modrinthUploadGallery: (config: any) => Promise<void>
  modrinthUpdateProject: (config: any) => Promise<void>
  modrinthGetProject: (config: any) => Promise<any>

  // Modrinth tags
  modrinthCategories: () => Promise<ModrinthCategory[]>
  modrinthLoaders: () => Promise<ModrinthLoader[]>
  modrinthGameVersions: () => Promise<ModrinthGameVersion[]>
  modrinthLicenses: () => Promise<ModrinthLicense[]>
  modrinthDonationPlatforms: () => Promise<ModrinthDonationPlatform[]>

  // Mod development
  modCompile: (sourcePath: string, modName: string) => Promise<{ success: boolean; jarPath: string | null; output: string }>
  modLaunchClient: (jarPath: string, instanceName: string) => Promise<{ success: boolean; message: string }>
  modListInstances: () => Promise<string[]>
  modDetectWorkspace: (sourcePath: string) => Promise<{ workspace: string; workspacePath: string; modName: string; modProperties: Record<string, string> | null }>
  modScanMods: (workspace: string) => Promise<Array<{ name: string; path: string; modid: string; version: string }>>
  modScanAllMods: () => Promise<Array<{ name: string; path: string; modid: string; version: string; workspace: string; loader: string }>>

  // Bundle file write (for icon editor)
  bundleWriteBase64File: (base64Data: string, bundlePath: string, relativePath: string) => Promise<{ success: boolean; destPath: string }>
}

declare global {
  interface Window {
    electronAPI: ElectronAPI
  }
}
