"use strict";
const electron = require("electron");
electron.contextBridge.exposeInMainWorld("electronAPI", {
  // App paths
  appDefaultPaths: () => electron.ipcRenderer.invoke("app:default-paths"),
  // Bundle operations
  bundleCreate: (path, name) => electron.ipcRenderer.invoke("bundle:create", path, name),
  bundleOpen: (path) => electron.ipcRenderer.invoke("bundle:open", path),
  bundleSave: (path, manifest) => electron.ipcRenderer.invoke("bundle:save", path, manifest),
  bundleRecent: () => electron.ipcRenderer.invoke("bundle:recent"),
  bundleDeleteFromIndex: (path) => electron.ipcRenderer.invoke("bundle:delete-from-index", path),
  bundleReadFileAsBase64: (path) => electron.ipcRenderer.invoke("bundle:read-file-as-base64", path),
  bundleCopyFile: (src, bundle, dest) => electron.ipcRenderer.invoke("bundle:copy-file-to-bundle", src, bundle, dest),
  bundleCopyDir: (src, bundle, dest) => electron.ipcRenderer.invoke("bundle:copy-dir-to-bundle", src, bundle, dest),
  bundleScanDrafts: () => electron.ipcRenderer.invoke("bundle:scan-drafts"),
  bundleMoveToPublished: (path) => electron.ipcRenderer.invoke("bundle:move-to-published", path),
  // Dialog
  dialogOpenFile: (options) => electron.ipcRenderer.invoke("dialog:open-file", options),
  dialogOpenDirectory: (options) => electron.ipcRenderer.invoke("dialog:open-directory", options),
  dialogSaveFile: (options) => electron.ipcRenderer.invoke("dialog:save-file", options),
  // GitHub
  githubCheck: () => electron.ipcRenderer.invoke("publish:github-check"),
  githubCreate: (config) => electron.ipcRenderer.invoke("publish:github-create", config),
  githubPush: (config) => electron.ipcRenderer.invoke("publish:github-push", config),
  githubWiki: (config) => electron.ipcRenderer.invoke("publish:github-wiki", config),
  // Modrinth
  modrinthCreateProject: (config) => electron.ipcRenderer.invoke("publish:modrinth-create-project", config),
  modrinthUploadVersion: (config) => electron.ipcRenderer.invoke("publish:modrinth-upload-version", config),
  modrinthUploadGallery: (config) => electron.ipcRenderer.invoke("publish:modrinth-upload-gallery", config),
  modrinthUpdateProject: (config) => electron.ipcRenderer.invoke("publish:modrinth-update-project", config),
  modrinthGetProject: (config) => electron.ipcRenderer.invoke("publish:modrinth-get-project", config),
  // Modrinth tags
  modrinthCategories: () => electron.ipcRenderer.invoke("modrinth:categories"),
  modrinthLoaders: () => electron.ipcRenderer.invoke("modrinth:loaders"),
  modrinthGameVersions: () => electron.ipcRenderer.invoke("modrinth:game-versions"),
  modrinthLicenses: () => electron.ipcRenderer.invoke("modrinth:licenses"),
  modrinthDonationPlatforms: () => electron.ipcRenderer.invoke("modrinth:donation-platforms"),
  // Mod development
  modCompile: (sourcePath, modName) => electron.ipcRenderer.invoke("mod:compile", { sourcePath, modName }),
  modLaunchClient: (jarPath, instanceName) => electron.ipcRenderer.invoke("mod:launch-client", { jarPath, instanceName }),
  modListInstances: () => electron.ipcRenderer.invoke("mod:list-instances"),
  modDetectWorkspace: (sourcePath) => electron.ipcRenderer.invoke("mod:detect-workspace", { sourcePath }),
  modScanMods: (workspace) => electron.ipcRenderer.invoke("mod:scan-mods", { workspace }),
  modScanAllMods: () => electron.ipcRenderer.invoke("mod:scan-all-mods"),
  // Bundle file write (for icon editor)
  bundleWriteBase64File: (base64Data, bundlePath, relativePath) => electron.ipcRenderer.invoke("bundle:write-base64-file", base64Data, bundlePath, relativePath)
});
