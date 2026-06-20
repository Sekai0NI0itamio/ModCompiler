import { useState, useEffect } from 'react'
import type { DefaultPaths } from '../types/electron'

const FALLBACK: DefaultPaths = {
  modcompilerRoot: '',
  cmpDir: '',
  bundleDraftsDir: '',
  bundlePublishedDir: '',
  modDevDir: '',
  toBeUploadedDir: '',
}

let cachedPaths: DefaultPaths | null = null

export function useDefaultPaths(): DefaultPaths {
  const [paths, setPaths] = useState<DefaultPaths>(cachedPaths || FALLBACK)

  useEffect(() => {
    if (cachedPaths) {
      setPaths(cachedPaths)
      return
    }
    window.electronAPI.appDefaultPaths().then((result) => {
      cachedPaths = result
      setPaths(result)
    }).catch(() => {})
  }, [])

  return paths
}
