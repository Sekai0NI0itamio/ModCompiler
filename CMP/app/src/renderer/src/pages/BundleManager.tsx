import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Plus, FolderOpen, Trash2, Rocket, Package, RefreshCw, ArrowRight } from 'lucide-react'
import { useStore } from '../store/useStore'
import { useDefaultPaths } from '../hooks/useDefaultPaths'

interface ScannedBundle {
  path: string
  name: string
  lastModified: string
  status: string
}

export function BundleManager() {
  const navigate = useNavigate()
  const { setCurrentBundlePath, setManifest } = useStore()
  const defaultPaths = useDefaultPaths()
  const [bundles, setBundles] = useState<ScannedBundle[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    loadBundles()
  }, [])

  async function loadBundles() {
    try {
      setLoading(true)
      const scanned = await window.electronAPI.bundleScanDrafts()
      setBundles(scanned || [])
    } catch (err) {
      console.error('Failed to load bundles:', err)
    } finally {
      setLoading(false)
    }
  }

  async function handleCreate() {
    try {
      const path = await window.electronAPI.dialogSaveFile({
        title: 'Create New Bundle',
        defaultPath: `${defaultPaths.bundleDraftsDir}/my-mod`,
        filters: [{ name: 'CMP Bundle', extensions: ['cmp'] }],
      })
      if (!path) return

      const name = path.split('/').pop()?.replace('.cmp', '') || 'New Mod'
      const result = await window.electronAPI.bundleCreate(path, name)
      setCurrentBundlePath(result.path)
      setManifest(result.manifest)
      navigate('/editor')
    } catch (err) {
      console.error('Failed to create bundle:', err)
    }
  }

  async function handleOpen() {
    try {
      const path = await window.electronAPI.dialogOpenDirectory({
        title: 'Open Bundle Directory',
        defaultPath: defaultPaths.cmpDir,
      })
      if (!path) return

      const result = await window.electronAPI.bundleOpen(path)
      setCurrentBundlePath(result.path)
      setManifest(result.manifest)
      navigate('/editor')
    } catch (err) {
      console.error('Failed to open bundle:', err)
    }
  }

  async function handleOpenBundle(bundle: ScannedBundle) {
    try {
      const result = await window.electronAPI.bundleOpen(bundle.path)
      setCurrentBundlePath(result.path)
      setManifest(result.manifest)
      navigate('/editor')
    } catch (err) {
      console.error('Failed to open bundle:', err)
    }
  }

  async function handleMoveToPublished(bundle: ScannedBundle) {
    try {
      await window.electronAPI.bundleMoveToPublished(bundle.path)
      loadBundles()
    } catch (err) {
      console.error('Failed to move bundle to published:', err)
    }
  }

  const drafts = bundles.filter((b) => b.status === 'draft')
  const published = bundles.filter((b) => b.status === 'published')

  return (
    <div className="h-full p-8 animate-fade-in">
      <div className="max-w-4xl mx-auto">
        {/* Header */}
        <div className="mb-8">
          <h1 className="text-2xl font-bold text-white mb-1">Center Mod Publishment</h1>
          <p className="text-white/40 text-sm">Create, edit, and publish Minecraft mods to GitHub & Modrinth</p>
        </div>

        {/* Action Buttons */}
        <div className="flex gap-3 mb-8">
          <button
            onClick={handleCreate}
            className="flex items-center gap-2 px-5 py-2.5 bg-teal text-charcoal rounded-lg font-semibold text-sm hover:bg-teal/90 transition-all glow-teal"
          >
            <Plus size={16} />
            New Bundle
          </button>
          <button
            onClick={handleOpen}
            className="flex items-center gap-2 px-5 py-2.5 bg-white/5 text-white/70 rounded-lg font-medium text-sm hover:bg-white/10 hover:text-white transition-all"
          >
            <FolderOpen size={16} />
            Open Existing
          </button>
          <button
            onClick={loadBundles}
            className="flex items-center gap-2 px-5 py-2.5 bg-white/5 text-white/70 rounded-lg font-medium text-sm hover:bg-white/10 hover:text-white transition-all"
          >
            <RefreshCw size={16} />
            Refresh
          </button>
        </div>

        {/* Drafts Section */}
        <div className="mb-8">
          <h2 className="text-sm font-semibold text-white/50 uppercase tracking-wider mb-4">Drafts</h2>

          {loading ? (
            <div className="text-white/30 text-sm">Loading...</div>
          ) : drafts.length === 0 ? (
            <div className="bg-charcoal/50 rounded-xl border border-white/5 p-8 text-center">
              <Package size={32} className="mx-auto text-white/10 mb-3" />
              <p className="text-white/30 text-sm mb-1">No draft bundles</p>
              <p className="text-white/20 text-xs">Create a new bundle to get started</p>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              {drafts.map((bundle) => (
                <div
                  key={bundle.path}
                  className="bg-charcoal/50 rounded-xl border border-white/5 p-4 hover:border-teal/20 transition-all group cursor-pointer"
                  onClick={() => handleOpenBundle(bundle)}
                >
                  <div className="flex items-start justify-between">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <h3 className="font-semibold text-white text-sm truncate">{bundle.name}</h3>
                        <span className="px-2 py-0.5 text-xs font-medium rounded-full bg-amber-500/20 text-amber-400">
                          Draft
                        </span>
                      </div>
                      <p className="text-white/30 text-xs mt-1 truncate">{bundle.path}</p>
                      <p className="text-white/20 text-xs mt-1">
                        {new Date(bundle.lastModified).toLocaleDateString()}
                      </p>
                    </div>
                    <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity ml-2">
                      <button
                        onClick={(e) => { e.stopPropagation(); handleMoveToPublished(bundle) }}
                        className="p-1.5 rounded-md hover:bg-white/10 text-white/40 hover:text-teal transition-all"
                        title="Move to Published"
                      >
                        <ArrowRight size={14} />
                      </button>
                      <button
                        onClick={(e) => { e.stopPropagation(); handleOpenBundle(bundle) }}
                        className="p-1.5 rounded-md hover:bg-white/10 text-white/40 hover:text-teal transition-all"
                        title="Edit"
                      >
                        <FolderOpen size={14} />
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Published Section */}
        <div>
          <h2 className="text-sm font-semibold text-white/50 uppercase tracking-wider mb-4">Published</h2>

          {loading ? (
            <div className="text-white/30 text-sm">Loading...</div>
          ) : published.length === 0 ? (
            <div className="bg-charcoal/50 rounded-xl border border-white/5 p-8 text-center">
              <Rocket size={32} className="mx-auto text-white/10 mb-3" />
              <p className="text-white/30 text-sm mb-1">No published bundles</p>
              <p className="text-white/20 text-xs">Move a draft to published when it's ready</p>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              {published.map((bundle) => (
                <div
                  key={bundle.path}
                  className="bg-charcoal/50 rounded-xl border border-white/5 p-4 hover:border-teal/20 transition-all group cursor-pointer"
                  onClick={() => handleOpenBundle(bundle)}
                >
                  <div className="flex items-start justify-between">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <h3 className="font-semibold text-white text-sm truncate">{bundle.name}</h3>
                        <span className="px-2 py-0.5 text-xs font-medium rounded-full bg-emerald-500/20 text-emerald-400">
                          Published
                        </span>
                      </div>
                      <p className="text-white/30 text-xs mt-1 truncate">{bundle.path}</p>
                      <p className="text-white/20 text-xs mt-1">
                        {new Date(bundle.lastModified).toLocaleDateString()}
                      </p>
                    </div>
                    <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity ml-2">
                      <button
                        onClick={(e) => { e.stopPropagation(); handleOpenBundle(bundle) }}
                        className="p-1.5 rounded-md hover:bg-white/10 text-white/40 hover:text-teal transition-all"
                        title="Edit"
                      >
                        <FolderOpen size={14} />
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
