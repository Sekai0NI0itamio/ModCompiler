import { useState } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { ImagePlus } from 'lucide-react'
import type { Manifest } from '../../../../shared/types'

interface Props {
  manifest: Manifest
  updateManifest: (partial: Partial<Manifest>) => void
}

export function DescriptionSection({ manifest, updateManifest }: Props) {
  const [tab, setTab] = useState<'edit' | 'preview'>('edit')

  function insertImagePlaceholder() {
    const nextIndex = manifest.description.images.length
    const placeholder = `{{image:${nextIndex}}}`
    const newBody = manifest.description.body + (manifest.description.body ? '\n' : '') + placeholder
    updateManifest({
      description: {
        body: newBody,
        images: [...manifest.description.images, { index: nextIndex, file: `description_images/${nextIndex}.png`, caption: '' }],
      },
    })
  }

  return (
    <div className="bg-charcoal/50 rounded-xl border border-white/5 p-5">
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-sm font-semibold text-white">Description</h2>
        <div className="flex items-center gap-2">
          <button
            onClick={insertImagePlaceholder}
            className="flex items-center gap-1 px-2 py-1 text-xs text-white/40 hover:text-teal hover:bg-teal/10 rounded-md transition-all"
          >
            <ImagePlus size={12} />
            Insert Image
          </button>
          <div className="flex bg-surface rounded-md p-0.5">
            <button
              onClick={() => setTab('edit')}
              className={`px-2.5 py-1 text-xs rounded-md transition-all ${tab === 'edit' ? 'bg-white/10 text-white' : 'text-white/30'}`}
            >
              Edit
            </button>
            <button
              onClick={() => setTab('preview')}
              className={`px-2.5 py-1 text-xs rounded-md transition-all ${tab === 'preview' ? 'bg-white/10 text-white' : 'text-white/30'}`}
            >
              Preview
            </button>
          </div>
        </div>
      </div>

      {tab === 'edit' ? (
        <textarea
          value={manifest.description.body}
          onChange={(e) => updateManifest({ description: { ...manifest.description, body: e.target.value } })}
          className="w-full h-64 bg-surface border border-white/10 rounded-lg px-3 py-2 text-sm text-white font-mono resize-none focus:outline-none focus:border-teal/50 transition-colors"
          placeholder="Write your mod description in Markdown...&#10;&#10;Use {{image:0}} to insert image placeholders."
        />
      ) : (
        <div className="min-h-[256px] bg-surface border border-white/10 rounded-lg px-4 py-3 prose prose-invert prose-sm max-w-none">
          <ReactMarkdown remarkPlugins={[remarkGfm]}>
            {manifest.description.body.replace(/\{\{image:(\d+)\}\}/g, (_, idx) => {
              const img = manifest.description.images.find((i) => i.index === Number(idx))
              return img ? `*[Image ${idx}: ${img.caption || 'no caption'}]*` : `*[Image ${idx}]*`
            })}
          </ReactMarkdown>
        </div>
      )}

      {manifest.description.images.length > 0 && (
        <div className="mt-3 space-y-2">
          <p className="text-xs text-white/30">Image Placeholders</p>
          {manifest.description.images.map((img, i) => (
            <div key={i} className="flex items-center gap-2 bg-surface rounded-lg px-3 py-2">
              <span className="text-xs font-mono text-teal">{'{{image:' + img.index + '}}'}</span>
              <input
                value={img.caption}
                onChange={(e) => {
                  const newImages = [...manifest.description.images]
                  newImages[i] = { ...newImages[i], caption: e.target.value }
                  updateManifest({ description: { ...manifest.description, images: newImages } })
                }}
                className="flex-1 bg-transparent text-xs text-white/60 focus:outline-none"
                placeholder="Caption"
              />
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
