import { ipcMain, app } from 'electron'
import { readFileSync, writeFileSync, mkdirSync, existsSync, readdirSync, statSync, rmSync, renameSync } from 'fs'
import { join, resolve, basename, extname } from 'path'
import { createReadStream, createWriteStream } from 'fs'
import { randomUUID } from 'crypto'
import type { Manifest } from '../../shared/types'

const APP_SUPPORT = app.getPath('appData')
const CMP_DIR = join(APP_SUPPORT, 'CMP')
const BUNDLES_INDEX = join(CMP_DIR, 'bundles.json')
const TMP_DIR = join(CMP_DIR, 'tmp')

// Resolve ModCompiler root robustly:
// Walk up from __dirname, collect all dirs that contain CMP/BundleDrafts,
// then prefer the one named "ModCompiler"
function resolveModcompilerRoot(): string {
  let dir = __dirname
  let bestMatch = ''
  for (let i = 0; i < 10; i++) {
    if (existsSync(join(dir, 'CMP', 'BundleDrafts'))) {
      if (basename(dir) === 'ModCompiler') {
        return dir // Perfect match
      }
      if (!bestMatch) bestMatch = dir // Remember first fallback
    }
    const parent = join(dir, '..')
    if (parent === dir) break
    dir = parent
  }
  return bestMatch || process.cwd()
}

const MODCOMPILER_ROOT = resolveModcompilerRoot()
const BUNDLE_DRAFTS_DIR = join(MODCOMPILER_ROOT, 'CMP', 'BundleDrafts')
const BUNDLE_PUBLISHED_DIR = join(MODCOMPILER_ROOT, 'CMP', 'BundlePublished')

function ensureDirs(): void {
  if (!existsSync(CMP_DIR)) mkdirSync(CMP_DIR, { recursive: true })
  if (!existsSync(TMP_DIR)) mkdirSync(TMP_DIR, { recursive: true })
}

function getBundlesIndex(): Array<{ path: string; name: string; lastModified: string }> {
  ensureDirs()
  if (!existsSync(BUNDLES_INDEX)) return []
  return JSON.parse(readFileSync(BUNDLES_INDEX, 'utf-8'))
}

function saveBundlesIndex(index: Array<{ path: string; name: string; lastModified: string }>): void {
  ensureDirs()
  writeFileSync(BUNDLES_INDEX, JSON.stringify(index, null, 2), 'utf-8')
}

function createEmptyManifest(name: string): Manifest {
  const slug = name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')
  return {
    cmp_version: 1,
    mod_info: {
      name,
      slug,
      summary: '',
      project_type: 'mod',
      categories: [],
      additional_categories: [],
      license_id: 'MIT',
      license_url: '',
      donation_urls: [],
    },
    version_info: {
      mod_version: '1.0.0',
      loaders: ['fabric'],
      client_side: 'required',
      server_side: 'optional',
      minecraft_versions: [],
      version_type: 'release',
      changelog: '',
      dependencies: [],
      featured: false,
    },
    description: {
      body: '',
      images: [],
    },
    icon: '',
    gallery: [],
    files: {
      jar: '',
      source: '',
    },
    links: {
      issues_url: '',
      source_url: '',
      wiki_url: '',
      discord_url: '',
    },
    publishing: {
      modrinth_project_id: '',
      github_owner: '',
      github_repo_name: '',
      requested_status: '',
    },
  }
}

// Simple .cmp (zip) read/write using Node's built-in zlib + a manual zip approach
// For production, we use archiver-like approach with the zip format
// Since we can't add archiver as a dep easily, we'll use a simpler approach:
// .cmp files are directories (for now in dev), and we'll add zip support later

export function registerBundleHandlers(): void {
  ipcMain.handle('bundle:create', async (_event, filePath: string, name: string) => {
    ensureDirs()
    const manifest = createEmptyManifest(name)
    const manifestPath = join(filePath, 'manifest.json')

    if (!existsSync(filePath)) {
      mkdirSync(filePath, { recursive: true })
      mkdirSync(join(filePath, 'jar'), { recursive: true })
      mkdirSync(join(filePath, 'source'), { recursive: true })
      mkdirSync(join(filePath, 'gallery'), { recursive: true })
      mkdirSync(join(filePath, 'description_images'), { recursive: true })
    }

    writeFileSync(manifestPath, JSON.stringify(manifest, null, 2), 'utf-8')

    // Update index
    const index = getBundlesIndex()
    const existing = index.findIndex((b) => b.path === filePath)
    const entry = { path: filePath, name, lastModified: new Date().toISOString() }
    if (existing >= 0) {
      index[existing] = entry
    } else {
      index.push(entry)
    }
    saveBundlesIndex(index)

    return { path: filePath, manifest }
  })

  ipcMain.handle('bundle:open', async (_event, filePath: string) => {
    const manifestPath = join(filePath, 'manifest.json')
    if (!existsSync(manifestPath)) {
      throw new Error(`No manifest.json found at ${filePath}`)
    }
    const manifest: Manifest = JSON.parse(readFileSync(manifestPath, 'utf-8'))

    // Update index
    const index = getBundlesIndex()
    const existing = index.findIndex((b) => b.path === filePath)
    const entry = { path: filePath, name: manifest.mod_info.name, lastModified: new Date().toISOString() }
    if (existing >= 0) {
      index[existing] = entry
    } else {
      index.push(entry)
    }
    saveBundlesIndex(index)

    return { path: filePath, manifest }
  })

  ipcMain.handle('bundle:save', async (_event, filePath: string, manifest: Manifest) => {
    ensureDirs()
    const manifestPath = join(filePath, 'manifest.json')
    writeFileSync(manifestPath, JSON.stringify(manifest, null, 2), 'utf-8')

    // Update index
    const index = getBundlesIndex()
    const existing = index.findIndex((b) => b.path === filePath)
    const entry = { path: filePath, name: manifest.mod_info.name, lastModified: new Date().toISOString() }
    if (existing >= 0) {
      index[existing] = entry
    } else {
      index.push(entry)
    }
    saveBundlesIndex(index)

    return { success: true }
  })

  ipcMain.handle('bundle:recent', async () => {
    return getBundlesIndex()
  })

  ipcMain.handle('bundle:delete-from-index', async (_event, filePath: string) => {
    const index = getBundlesIndex()
    const filtered = index.filter((b) => b.path !== filePath)
    saveBundlesIndex(filtered)
    return { success: true }
  })

  ipcMain.handle('bundle:read-file-as-base64', async (_event, filePath: string) => {
    if (!existsSync(filePath)) return null
    const buffer = readFileSync(filePath)
    return buffer.toString('base64')
  })

  ipcMain.handle('bundle:copy-file-to-bundle', async (_event, sourcePath: string, bundlePath: string, relativeDest: string) => {
    const destPath = join(bundlePath, relativeDest)
    const destDir = join(destPath, '..')
    if (!existsSync(destDir)) mkdirSync(destDir, { recursive: true })
    const buffer = readFileSync(sourcePath)
    writeFileSync(destPath, buffer)
    return { success: true, destPath }
  })

  ipcMain.handle('bundle:copy-dir-to-bundle', async (_event, sourcePath: string, bundlePath: string, relativeDest: string) => {
    const destPath = join(bundlePath, relativeDest)
    copyDirRecursive(sourcePath, destPath)
    return { success: true, destPath }
  })

  ipcMain.handle('bundle:list-files', async (_event, dirPath: string) => {
    if (!existsSync(dirPath)) return []
    return listFilesRecursive(dirPath, dirPath)
  })

  ipcMain.handle('bundle:scan-drafts', async () => {
    console.log('[CMP] Scanning for bundles...')
    console.log('[CMP] MODCOMPILER_ROOT:', MODCOMPILER_ROOT)
    console.log('[CMP] BUNDLE_DRAFTS_DIR:', BUNDLE_DRAFTS_DIR, 'exists:', existsSync(BUNDLE_DRAFTS_DIR))
    console.log('[CMP] BUNDLE_PUBLISHED_DIR:', BUNDLE_PUBLISHED_DIR, 'exists:', existsSync(BUNDLE_PUBLISHED_DIR))

    const results: Array<{ path: string; name: string; lastModified: string; status: string }> = []

    // Scan BundleDrafts
    if (existsSync(BUNDLE_DRAFTS_DIR)) {
      const draftEntries = scanBundleDir(BUNDLE_DRAFTS_DIR, 'draft')
      results.push(...draftEntries)
    }

    // Scan BundlePublished
    if (existsSync(BUNDLE_PUBLISHED_DIR)) {
      const publishedEntries = scanBundleDir(BUNDLE_PUBLISHED_DIR, 'published')
      results.push(...publishedEntries)
    }

    return results
  })

  ipcMain.handle('bundle:move-to-published', async (_event, bundlePath: string) => {
    const bundleName = basename(bundlePath)
    const destPath = join(BUNDLE_PUBLISHED_DIR, bundleName)

    if (!existsSync(BUNDLE_PUBLISHED_DIR)) {
      mkdirSync(BUNDLE_PUBLISHED_DIR, { recursive: true })
    }

    if (existsSync(destPath)) {
      throw new Error(`A published bundle already exists at ${destPath}`)
    }

    renameSync(bundlePath, destPath)

    return { success: true, newPath: destPath }
  })

  ipcMain.handle('bundle:write-base64-file', async (_event, base64Data: string, bundlePath: string, relativePath: string) => {
    const destPath = join(bundlePath, relativePath)
    const destDir = join(destPath, '..')
    if (!existsSync(destDir)) mkdirSync(destDir, { recursive: true })
    const buffer = Buffer.from(base64Data, 'base64')
    writeFileSync(destPath, buffer)
    return { success: true, destPath }
  })
}

function copyDirRecursive(src: string, dest: string): void {
  if (!existsSync(dest)) mkdirSync(dest, { recursive: true })
  const entries = readdirSync(src, { withFileTypes: true })
  for (const entry of entries) {
    const srcPath = join(src, entry.name)
    const destPath = join(dest, entry.name)
    if (entry.isDirectory()) {
      copyDirRecursive(srcPath, destPath)
    } else {
      const buffer = readFileSync(srcPath)
      writeFileSync(destPath, buffer)
    }
  }
}

function listFilesRecursive(dir: string, base: string): Array<{ path: string; name: string; isDir: boolean }> {
  const results: Array<{ path: string; name: string; isDir: boolean }> = []
  if (!existsSync(dir)) return results
  const entries = readdirSync(dir, { withFileTypes: true })
  for (const entry of entries) {
    const fullPath = join(dir, entry.name)
    const relPath = fullPath.slice(base.length + 1)
    if (entry.isDirectory()) {
      results.push({ path: relPath, name: entry.name, isDir: true })
      results.push(...listFilesRecursive(fullPath, base))
    } else {
      results.push({ path: relPath, name: entry.name, isDir: false })
    }
  }
  return results
}

function scanBundleDir(baseDir: string, status: string): Array<{ path: string; name: string; lastModified: string; status: string }> {
  const results: Array<{ path: string; name: string; lastModified: string; status: string }> = []
  if (!existsSync(baseDir)) return results

  const entries = readdirSync(baseDir, { withFileTypes: true })
  for (const entry of entries) {
    if (!entry.isDirectory()) continue
    const subDir = join(baseDir, entry.name)
    const manifestPath = join(subDir, 'manifest.json')
    if (!existsSync(manifestPath)) continue

    let name = entry.name
    try {
      const manifest: Manifest = JSON.parse(readFileSync(manifestPath, 'utf-8'))
      if (manifest.mod_info?.name) name = manifest.mod_info.name
    } catch { /* use directory name as fallback */ }

    const stat = statSync(subDir)
    results.push({
      path: subDir,
      name,
      lastModified: stat.mtime.toISOString(),
      status,
    })
  }
  return results
}
