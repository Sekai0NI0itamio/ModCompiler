import { contextBridge, ipcRenderer } from 'electron'

contextBridge.exposeInMainWorld('electronAPI', {
  // App paths
  appDefaultPaths: () => ipcRenderer.invoke('app:default-paths'),

  // Bundle operations
  bundleCreate: (path: string, name: string) => ipcRenderer.invoke('bundle:create', path, name),
  bundleOpen: (path: string) => ipcRenderer.invoke('bundle:open', path),
  bundleSave: (path: string, manifest: any) => ipcRenderer.invoke('bundle:save', path, manifest),
  bundleRecent: () => ipcRenderer.invoke('bundle:recent'),
  bundleDeleteFromIndex: (path: string) => ipcRenderer.invoke('bundle:delete-from-index', path),
  bundleReadFileAsBase64: (path: string) => ipcRenderer.invoke('bundle:read-file-as-base64', path),
  bundleCopyFile: (src: string, bundle: string, dest: string) => ipcRenderer.invoke('bundle:copy-file-to-bundle', src, bundle, dest),
  bundleCopyDir: (src: string, bundle: string, dest: string) => ipcRenderer.invoke('bundle:copy-dir-to-bundle', src, bundle, dest),
  bundleScanDrafts: () => ipcRenderer.invoke('bundle:scan-drafts'),
  bundleMoveToPublished: (path: string) => ipcRenderer.invoke('bundle:move-to-published', path),

  // Dialog
  dialogOpenFile: (options?: any) => ipcRenderer.invoke('dialog:open-file', options),
  dialogOpenDirectory: (options?: any) => ipcRenderer.invoke('dialog:open-directory', options),
  dialogSaveFile: (options?: any) => ipcRenderer.invoke('dialog:save-file', options),

  // GitHub
  githubCheck: () => ipcRenderer.invoke('publish:github-check'),
  githubCreate: (config: any) => ipcRenderer.invoke('publish:github-create', config),
  githubPush: (config: any) => ipcRenderer.invoke('publish:github-push', config),
  githubWiki: (config: any) => ipcRenderer.invoke('publish:github-wiki', config),

  // Modrinth
  modrinthCreateProject: (config: any) => ipcRenderer.invoke('publish:modrinth-create-project', config),
  modrinthUploadVersion: (config: any) => ipcRenderer.invoke('publish:modrinth-upload-version', config),
  modrinthUploadGallery: (config: any) => ipcRenderer.invoke('publish:modrinth-upload-gallery', config),
  modrinthUpdateProject: (config: any) => ipcRenderer.invoke('publish:modrinth-update-project', config),
  modrinthGetProject: (config: any) => ipcRenderer.invoke('publish:modrinth-get-project', config),

  // Modrinth tags
  modrinthCategories: () => ipcRenderer.invoke('modrinth:categories'),
  modrinthLoaders: () => ipcRenderer.invoke('modrinth:loaders'),
  modrinthGameVersions: () => ipcRenderer.invoke('modrinth:game-versions'),
  modrinthLicenses: () => ipcRenderer.invoke('modrinth:licenses'),
  modrinthDonationPlatforms: () => ipcRenderer.invoke('modrinth:donation-platforms'),

  // Mod development
  modCompile: (sourcePath: string, modName: string) => ipcRenderer.invoke('mod:compile', { sourcePath, modName }),
  modLaunchClient: (jarPath: string, instanceName: string) => ipcRenderer.invoke('mod:launch-client', { jarPath, instanceName }),
  modListInstances: () => ipcRenderer.invoke('mod:list-instances'),
  modDetectWorkspace: (sourcePath: string) => ipcRenderer.invoke('mod:detect-workspace', { sourcePath }),
  modScanMods: (workspace: string) => ipcRenderer.invoke('mod:scan-mods', { workspace }),
  modScanAllMods: () => ipcRenderer.invoke('mod:scan-all-mods'),

  // Bundle file write (for icon editor)
  bundleWriteBase64File: (base64Data: string, bundlePath: string, relativePath: string) => ipcRenderer.invoke('bundle:write-base64-file', base64Data, bundlePath, relativePath),
})
