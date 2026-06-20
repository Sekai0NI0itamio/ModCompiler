import { create } from 'zustand'
import type { Manifest, PublishLogEntry, PublishStep } from '../../../shared/types'

interface AppState {
  // Current bundle
  currentBundlePath: string | null
  manifest: Manifest | null
  setManifest: (manifest: Manifest) => void
  setCurrentBundlePath: (path: string | null) => void

  // Asset previews
  iconPreview: string | null
  setIconPreview: (data: string | null) => void
  galleryPreviews: Array<{ index: number; data: string; file: string }>
  setGalleryPreviews: (previews: Array<{ index: number; data: string; file: string }>) => void
  addGalleryPreview: (preview: { index: number; data: string; file: string }) => void
  removeGalleryPreview: (index: number) => void

  // File paths
  jarPath: string | null
  setJarPath: (path: string | null) => void
  sourcePath: string | null
  setSourcePath: (path: string | null) => void

  // Publish state
  publishStep: PublishStep
  setPublishStep: (step: PublishStep) => void
  publishLogs: PublishLogEntry[]
  addPublishLog: (entry: PublishLogEntry) => void
  clearPublishLogs: () => void

  // Modrinth token
  modrinthToken: string
  setModrinthToken: (token: string) => void

  // Dirty state
  isDirty: boolean
  setIsDirty: (dirty: boolean) => void
}

export const useStore = create<AppState>((set) => ({
  currentBundlePath: null,
  manifest: null,
  setManifest: (manifest) => set({ manifest, isDirty: true }),
  setCurrentBundlePath: (path) => set({ currentBundlePath: path }),

  iconPreview: null,
  setIconPreview: (data) => set({ iconPreview: data }),
  galleryPreviews: [],
  setGalleryPreviews: (previews) => set({ galleryPreviews: previews }),
  addGalleryPreview: (preview) => set((s) => ({ galleryPreviews: [...s.galleryPreviews, preview] })),
  removeGalleryPreview: (index) => set((s) => ({ galleryPreviews: s.galleryPreviews.filter((p) => p.index !== index) })),

  jarPath: null,
  setJarPath: (path) => set({ jarPath: path }),
  sourcePath: null,
  setSourcePath: (path) => set({ sourcePath: path }),

  publishStep: 'idle',
  setPublishStep: (step) => set({ publishStep: step }),
  publishLogs: [],
  addPublishLog: (entry) => set((s) => ({ publishLogs: [...s.publishLogs, entry] })),
  clearPublishLogs: () => set({ publishLogs: [] }),

  modrinthToken: '',
  setModrinthToken: (token) => set({ modrinthToken: token }),

  isDirty: false,
  setIsDirty: (dirty) => set({ isDirty: dirty }),
}))
