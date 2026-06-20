import { ipcMain } from 'electron'
import type { ModrinthCategory, ModrinthLoader, ModrinthGameVersion, ModrinthLicense, ModrinthDonationPlatform } from '../../shared/types'

const MODRINTH_API = 'https://api.modrinth.com/v2'

interface CachedTags {
  categories: ModrinthCategory[] | null
  loaders: ModrinthLoader[] | null
  gameVersions: ModrinthGameVersion[] | null
  licenses: ModrinthLicense[] | null
  donationPlatforms: ModrinthDonationPlatform[] | null
}

const cache: CachedTags = {
  categories: null,
  loaders: null,
  gameVersions: null,
  licenses: null,
  donationPlatforms: null,
}

async function fetchFromModrinth(path: string): Promise<any> {
  const response = await fetch(`${MODRINTH_API}${path}`, {
    headers: { 'User-Agent': 'CMP/1.0 (Center Mod Publishment)' },
  })
  if (!response.ok) throw new Error(`Modrinth API error: ${response.status}`)
  return response.json()
}

export function registerModrinthTagHandlers(): void {
  ipcMain.handle('modrinth:categories', async () => {
    if (!cache.categories) {
      cache.categories = await fetchFromModrinth('/tag/category')
    }
    return cache.categories
  })

  ipcMain.handle('modrinth:loaders', async () => {
    if (!cache.loaders) {
      cache.loaders = await fetchFromModrinth('/tag/loader')
    }
    return cache.loaders
  })

  ipcMain.handle('modrinth:game-versions', async () => {
    if (!cache.gameVersions) {
      const all = await fetchFromModrinth('/tag/game_version')
      // Only cache release versions by default
      cache.gameVersions = all
    }
    return cache.gameVersions
  })

  ipcMain.handle('modrinth:licenses', async () => {
    if (!cache.licenses) {
      cache.licenses = await fetchFromModrinth('/tag/license')
    }
    return cache.licenses
  })

  ipcMain.handle('modrinth:donation-platforms', async () => {
    if (!cache.donationPlatforms) {
      cache.donationPlatforms = await fetchFromModrinth('/tag/donation_platform')
    }
    return cache.donationPlatforms
  })
}
