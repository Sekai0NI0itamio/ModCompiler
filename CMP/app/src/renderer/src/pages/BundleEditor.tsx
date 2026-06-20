import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Save, Eye, Rocket, Package, Info, FileCode, Image, Link2 } from 'lucide-react'
import { useStore } from '../store/useStore'
import { ModInfoSection } from '../components/editor/ModInfoSection'
import { VersionInfoSection } from '../components/editor/VersionInfoSection'
import { DescriptionSection } from '../components/editor/DescriptionSection'
import { IconGallerySection } from '../components/editor/IconGallerySection'
import { IconEditor } from '../components/editor/IconEditor'
import { FilesSection } from '../components/editor/FilesSection'
import { LinksSection } from '../components/editor/LinksSection'

export function BundleEditor() {
  const navigate = useNavigate()
  const {
    currentBundlePath, manifest, setManifest, isDirty, setIsDirty,
  } = useStore()
  const [showPreview, setShowPreview] = useState(false)

  useEffect(() => {
    if (!manifest) {
      navigate('/')
    }
  }, [manifest, navigate])

  if (!manifest) return null

  async function handleSave() {
    if (!currentBundlePath || !manifest) return
    try {
      await window.electronAPI.bundleSave(currentBundlePath, manifest)
      setIsDirty(false)
    } catch (err) {
      console.error('Failed to save:', err)
    }
  }

  function updateManifest(partial: Partial<typeof manifest>) {
    if (!manifest) return
    setManifest({ ...manifest, ...partial })
  }

  return (
    <div className="h-full flex flex-col animate-fade-in">
      {/* Toolbar */}
      <div className="flex items-center justify-between px-6 py-3 bg-charcoal/50 border-b border-white/5">
        <div className="flex items-center gap-3">
          <h1 className="text-sm font-semibold text-white">
            {manifest.mod_info.name || 'Untitled'}
          </h1>
          {isDirty && <span className="w-2 h-2 rounded-full bg-teal animate-pulse" />}
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={() => setShowPreview(!showPreview)}
            className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-white/50 hover:text-white/80 hover:bg-white/5 rounded-md transition-all"
          >
            <Eye size={14} />
            {showPreview ? 'Edit' : 'JSON'}
          </button>
          <button
            onClick={handleSave}
            className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium bg-teal/10 text-teal hover:bg-teal/20 rounded-md transition-all"
          >
            <Save size={14} />
            Save
          </button>
          <button
            onClick={() => navigate('/publish')}
            className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium bg-teal text-charcoal rounded-md hover:bg-teal/90 transition-all"
          >
            <Rocket size={14} />
            Publish
          </button>
        </div>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-auto p-6">
        {showPreview ? (
          <div className="max-w-3xl mx-auto">
            <div className="bg-charcoal/50 rounded-xl border border-white/5 p-4">
              <pre className="text-xs font-mono text-white/70 overflow-auto whitespace-pre-wrap">
                {JSON.stringify(manifest, null, 2)}
              </pre>
            </div>
          </div>
        ) : (
          <div className="max-w-6xl mx-auto space-y-6">

            {/* ── Section 1: Identity & Files ── */}
            <div>
              <div className="flex items-center gap-2 mb-3">
                <Package size={14} className="text-teal/60" />
                <h3 className="text-xs font-semibold text-white/50 uppercase tracking-wider">Identity & Files</h3>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <ModInfoSection manifest={manifest} updateManifest={updateManifest} />
                <FilesSection manifest={manifest} updateManifest={updateManifest} />
              </div>
            </div>

            {/* ── Section 2: Version & Compatibility ── */}
            <div>
              <div className="flex items-center gap-2 mb-3">
                <Info size={14} className="text-teal/60" />
                <h3 className="text-xs font-semibold text-white/50 uppercase tracking-wider">Version & Compatibility</h3>
              </div>
              <VersionInfoSection manifest={manifest} updateManifest={updateManifest} />
            </div>

            {/* ── Section 3: Description ── */}
            <div>
              <div className="flex items-center gap-2 mb-3">
                <FileCode size={14} className="text-teal/60" />
                <h3 className="text-xs font-semibold text-white/50 uppercase tracking-wider">Description</h3>
              </div>
              <DescriptionSection manifest={manifest} updateManifest={updateManifest} />
            </div>

            {/* ── Section 4: Visuals ── */}
            <div>
              <div className="flex items-center gap-2 mb-3">
                <Image size={14} className="text-teal/60" />
                <h3 className="text-xs font-semibold text-white/50 uppercase tracking-wider">Visuals</h3>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <IconEditor manifest={manifest} updateManifest={updateManifest} />
                <IconGallerySection manifest={manifest} updateManifest={updateManifest} />
              </div>
            </div>

            {/* ── Section 5: Links & Publishing ── */}
            <div>
              <div className="flex items-center gap-2 mb-3">
                <Link2 size={14} className="text-teal/60" />
                <h3 className="text-xs font-semibold text-white/50 uppercase tracking-wider">Links & Publishing</h3>
              </div>
              <LinksSection manifest={manifest} updateManifest={updateManifest} />
            </div>

          </div>
        )}
      </div>
    </div>
  )
}
