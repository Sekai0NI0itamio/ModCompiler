import { useStore } from '../../store/useStore'
import { Upload, X, Star, Image as ImageIcon } from 'lucide-react'
import { useDefaultPaths } from '../../hooks/useDefaultPaths'
import type { Manifest, GalleryImage } from '../../../../shared/types'

interface Props {
  manifest: Manifest
  updateManifest: (partial: Partial<Manifest>) => void
}

export function IconGallerySection({ manifest, updateManifest }: Props) {
  const { iconPreview, setIconPreview, galleryPreviews, addGalleryPreview, removeGalleryPreview, currentBundlePath } = useStore()
  const defaultPaths = useDefaultPaths()

  async function handleIconUpload() {
    const path = await window.electronAPI.dialogOpenFile({
      title: 'Select Icon',
      defaultPath: defaultPaths.modDevDir,
      filters: [{ name: 'Images', extensions: ['png', 'jpg', 'jpeg', 'svg', 'webp'] }],
    })
    if (!path) return

    // Copy to bundle
    if (currentBundlePath) {
      await window.electronAPI.bundleCopyFile(path, currentBundlePath, 'icon.png')
    }

    // Read as base64 for preview
    const base64 = await window.electronAPI.bundleReadFileAsBase64(path)
    setIconPreview(base64)
    updateManifest({ icon: 'icon.png' })
  }

  async function handleGalleryAdd() {
    const path = await window.electronAPI.dialogOpenFile({
      title: 'Select Gallery Image',
      defaultPath: defaultPaths.modDevDir,
      filters: [{ name: 'Images', extensions: ['png', 'jpg', 'jpeg', 'svg', 'webp'] }],
    })
    if (!path) return

    const index = manifest.gallery.length
    const filename = `gallery/${index}.png`

    if (currentBundlePath) {
      await window.electronAPI.bundleCopyFile(path, currentBundlePath, filename)
    }

    const base64 = await window.electronAPI.bundleReadFileAsBase64(path)
    addGalleryPreview({ index, data: base64, file: filename })

    const newImage: GalleryImage = {
      index,
      file: filename,
      featured: index === 0,
      title: '',
      description: '',
    }
    updateManifest({ gallery: [...manifest.gallery, newImage] })
  }

  function removeGalleryImage(index: number) {
    removeGalleryPreview(index)
    updateManifest({ gallery: manifest.gallery.filter((img) => img.index !== index) })
  }

  function updateGalleryImage(index: number, partial: Partial<GalleryImage>) {
    const newGallery = manifest.gallery.map((img) =>
      img.index === index ? { ...img, ...partial } : img
    )
    updateManifest({ gallery: newGallery })
  }

  return (
    <div className="bg-charcoal/50 rounded-xl border border-white/5 p-5">
      <h2 className="text-sm font-semibold text-white mb-4">Icon & Gallery</h2>

      {/* Icon */}
      <div className="mb-5">
        <label className="block text-xs text-white/40 mb-1.5">Project Icon</label>
        <div className="flex items-center gap-4">
          <div
            onClick={handleIconUpload}
            className="w-20 h-20 rounded-xl bg-surface border-2 border-dashed border-white/10 flex items-center justify-center cursor-pointer hover:border-teal/30 transition-all overflow-hidden"
          >
            {iconPreview ? (
              <img src={`data:image/png;base64,${iconPreview}`} className="w-full h-full object-cover" />
            ) : (
              <Upload size={20} className="text-white/20" />
            )}
          </div>
          <div className="text-xs text-white/30">
            <p>Click to upload</p>
            <p>Recommended: 400x400 PNG</p>
          </div>
        </div>
      </div>

      {/* Gallery */}
      <div>
        <div className="flex items-center justify-between mb-2">
          <label className="text-xs text-white/40">Gallery Images</label>
          <button
            onClick={handleGalleryAdd}
            className="flex items-center gap-1 px-2 py-1 text-xs text-teal hover:bg-teal/10 rounded-md transition-all"
          >
            <ImageIcon size={12} />
            Add Image
          </button>
        </div>

        {manifest.gallery.length === 0 ? (
          <div className="text-xs text-white/20 py-4 text-center">No gallery images yet</div>
        ) : (
          <div className="grid grid-cols-2 gap-3">
            {manifest.gallery.map((img) => (
              <div key={img.index} className="bg-surface rounded-lg border border-white/5 p-3">
                <div className="flex items-start justify-between mb-2">
                  <div className="w-16 h-16 rounded-md bg-charcoal overflow-hidden">
                    {galleryPreviews.find((p) => p.index === img.index) && (
                      <img
                        src={`data:image/png;base64,${galleryPreviews.find((p) => p.index === img.index)?.data}`}
                        className="w-full h-full object-cover"
                      />
                    )}
                  </div>
                  <div className="flex gap-1">
                    <button
                      onClick={() => updateGalleryImage(img.index, { featured: !img.featured })}
                      className={`p-1 rounded ${img.featured ? 'text-yellow-400' : 'text-white/20 hover:text-yellow-400/50'}`}
                    >
                      <Star size={14} />
                    </button>
                    <button
                      onClick={() => removeGalleryImage(img.index)}
                      className="p-1 rounded text-white/20 hover:text-coral"
                    >
                      <X size={14} />
                    </button>
                  </div>
                </div>
                <input
                  value={img.title}
                  onChange={(e) => updateGalleryImage(img.index, { title: e.target.value })}
                  className="w-full bg-transparent text-xs text-white/70 mb-1 focus:outline-none"
                  placeholder="Title"
                />
                <input
                  value={img.description}
                  onChange={(e) => updateGalleryImage(img.index, { description: e.target.value })}
                  className="w-full bg-transparent text-xs text-white/40 focus:outline-none"
                  placeholder="Description"
                />
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
