import { ipcMain, dialog, BrowserWindow, app } from 'electron'
import { join, basename } from 'path'
import { existsSync, mkdirSync } from 'fs'

// Resolve ModCompiler root robustly (same logic as bundle-service)
function resolveModcompilerRoot(): string {
  let dir = __dirname
  let bestMatch = ''
  for (let i = 0; i < 10; i++) {
    if (existsSync(join(dir, 'CMP', 'BundleDrafts'))) {
      if (basename(dir) === 'ModCompiler') return dir
      if (!bestMatch) bestMatch = dir
    }
    const parent = join(dir, '..')
    if (parent === dir) break
    dir = parent
  }
  return bestMatch || process.cwd()
}

const MODCOMPILER_ROOT = resolveModcompilerRoot()
const CMP_DIR = join(MODCOMPILER_ROOT, 'CMP')
const BUNDLE_DRAFTS_DIR = join(CMP_DIR, 'BundleDrafts')
const BUNDLE_PUBLISHED_DIR = join(CMP_DIR, 'BundlePublished')
const MOD_DEV_DIR = join(MODCOMPILER_ROOT, 'Mod Development')
const TO_BE_UPLOADED_DIR = join(MODCOMPILER_ROOT, 'ToBeUploaded')

function ensureDir(dir: string): string {
  if (!existsSync(dir)) mkdirSync(dir, { recursive: true })
  return dir
}

export function registerDialogHandlers(): void {
  // Return default paths for the renderer to use
  ipcMain.handle('app:default-paths', () => ({
    modcompilerRoot: MODCOMPILER_ROOT,
    cmpDir: ensureDir(CMP_DIR),
    bundleDraftsDir: ensureDir(BUNDLE_DRAFTS_DIR),
    bundlePublishedDir: ensureDir(BUNDLE_PUBLISHED_DIR),
    modDevDir: existsSync(MOD_DEV_DIR) ? MOD_DEV_DIR : MODCOMPILER_ROOT,
    toBeUploadedDir: existsSync(TO_BE_UPLOADED_DIR) ? TO_BE_UPLOADED_DIR : MODCOMPILER_ROOT,
  }))

  ipcMain.handle('dialog:open-file', async (_event, options?: {
    title?: string
    defaultPath?: string
    filters?: Array<{ name: string; extensions: string[] }>
  }) => {
    const window = BrowserWindow.getFocusedWindow()
    const result = await dialog.showOpenDialog(window!, {
      title: options?.title || 'Select File',
      defaultPath: options?.defaultPath,
      properties: ['openFile'],
      filters: options?.filters || [{ name: 'All Files', extensions: ['*'] }],
    })
    if (result.canceled || result.filePaths.length === 0) return null
    return result.filePaths[0]
  })

  ipcMain.handle('dialog:open-directory', async (_event, options?: {
    title?: string
    defaultPath?: string
  }) => {
    const window = BrowserWindow.getFocusedWindow()
    const result = await dialog.showOpenDialog(window!, {
      title: options?.title || 'Select Directory',
      defaultPath: options?.defaultPath,
      properties: ['openDirectory'],
    })
    if (result.canceled || result.filePaths.length === 0) return null
    return result.filePaths[0]
  })

  ipcMain.handle('dialog:save-file', async (_event, options?: {
    title?: string
    defaultPath?: string
    filters?: Array<{ name: string; extensions: string[] }>
  }) => {
    const window = BrowserWindow.getFocusedWindow()
    const result = await dialog.showSaveDialog(window!, {
      title: options?.title || 'Save File',
      defaultPath: options?.defaultPath,
      filters: options?.filters || [{ name: 'CMP Bundle', extensions: ['cmp'] }],
    })
    if (result.canceled) return null
    return result.filePath
  })
}
