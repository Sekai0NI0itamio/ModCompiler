import { useState, useEffect } from 'react'
import { useStore } from '../../store/useStore'
import type { Manifest, ProjectType, DonationPlatform, ModrinthCategory, ModrinthLicense, ModrinthDonationPlatform } from '../../../../shared/types'

const PROJECT_TYPES: { value: ProjectType; label: string }[] = [
  { value: 'mod', label: 'Mod' },
  { value: 'modpack', label: 'Modpack' },
  { value: 'resourcepack', label: 'Resource Pack' },
  { value: 'shader', label: 'Shader' },
  { value: 'plugin', label: 'Plugin' },
  { value: 'datapack', label: 'Data Pack' },
  { value: 'minecraft_java_server', label: 'Server' },
]

const DONATION_PLATFORMS: { value: DonationPlatform; label: string }[] = [
  { value: 'patreon', label: 'Patreon' },
  { value: 'bmac', label: 'Buy Me a Coffee' },
  { value: 'paypal', label: 'PayPal' },
  { value: 'github', label: 'GitHub Sponsors' },
  { value: 'ko-fi', label: 'Ko-fi' },
  { value: 'other', label: 'Other' },
]

interface Props {
  manifest: Manifest
  updateManifest: (partial: Partial<Manifest>) => void
}

export function ModInfoSection({ manifest, updateManifest }: Props) {
  const { setIsDirty } = useStore()

  const [categories, setCategories] = useState<ModrinthCategory[]>([])
  const [licenses, setLicenses] = useState<ModrinthLicense[]>([])
  const [donationPlatforms, setDonationPlatforms] = useState<ModrinthDonationPlatform[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    async function fetchTags() {
      try {
        const [cats, lics, plats] = await Promise.all([
          window.electronAPI.modrinthCategories(),
          window.electronAPI.modrinthLicenses(),
          window.electronAPI.modrinthDonationPlatforms(),
        ])
        setCategories(cats)
        setLicenses(lics)
        setDonationPlatforms(plats)
      } catch (err) {
        console.error('Failed to fetch Modrinth tags:', err)
      } finally {
        setLoading(false)
      }
    }
    fetchTags()
  }, [])

  function updateModInfo(partial: Partial<typeof manifest.mod_info>) {
    updateManifest({ mod_info: { ...manifest.mod_info, ...partial } })
    setIsDirty(true)
  }

  const donationUrls = manifest.mod_info.donation_urls || []
  const additionalCategories = manifest.mod_info.additional_categories || []
  const categoriesList = manifest.mod_info.categories || []

  function toggleCategory(cat: string) {
    const cats = categoriesList.includes(cat)
      ? categoriesList.filter((c) => c !== cat)
      : [...categoriesList, cat]
    updateModInfo({ categories: cats })
  }

  function toggleAdditionalCategory(cat: string) {
    const cats = additionalCategories.includes(cat)
      ? additionalCategories.filter((c) => c !== cat)
      : [...additionalCategories, cat]
    updateModInfo({ additional_categories: cats })
  }

  function addDonationUrl() {
    updateModInfo({
      donation_urls: [...donationUrls, { id: crypto.randomUUID(), platform: 'other', url: '' }],
    })
  }

  function removeDonationUrl(index: number) {
    updateModInfo({
      donation_urls: donationUrls.filter((_, i) => i !== index),
    })
  }

  function updateDonationUrl(index: number, field: 'platform' | 'url', value: string) {
    const updated = [...donationUrls]
    updated[index] = { ...updated[index], [field]: value }
    updateModInfo({ donation_urls: updated })
  }

  const filteredCategories = categories.filter(
    (c) => c.project_type === manifest.mod_info.project_type
  )

  return (
    <div className="bg-charcoal/50 rounded-xl border border-white/5 p-5">
      <h2 className="text-sm font-semibold text-white mb-4">Mod Info</h2>
      <div className="space-y-3">
        {/* Row 1: Name + Slug */}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs text-white/40 mb-1">Name</label>
            <input
              value={manifest.mod_info.name}
              onChange={(e) => {
                const name = e.target.value
                updateModInfo({
                  name,
                  slug: name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, ''),
                })
              }}
              className="w-full bg-surface border border-white/10 rounded-lg px-3 py-1.5 text-sm text-white focus:outline-none focus:border-teal/50 transition-colors"
              placeholder="My Cool Mod"
            />
          </div>
          <div>
            <label className="block text-xs text-white/40 mb-1">Slug</label>
            <input
              value={manifest.mod_info.slug}
              onChange={(e) => updateModInfo({ slug: e.target.value })}
              className="w-full bg-surface border border-white/10 rounded-lg px-3 py-1.5 text-sm text-white font-mono focus:outline-none focus:border-teal/50 transition-colors"
              placeholder="my-cool-mod"
            />
          </div>
        </div>

        {/* Row 2: Summary */}
        <div>
          <label className="block text-xs text-white/40 mb-1">Summary</label>
          <input
            value={manifest.mod_info.summary}
            onChange={(e) => updateModInfo({ summary: e.target.value })}
            className="w-full bg-surface border border-white/10 rounded-lg px-3 py-1.5 text-sm text-white focus:outline-none focus:border-teal/50 transition-colors"
            placeholder="A brief one-line description"
          />
        </div>

        {/* Row 3: Project Type + License */}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs text-white/40 mb-1">Project Type</label>
            <div className="flex flex-wrap gap-1">
              {PROJECT_TYPES.map((pt) => (
                <button
                  key={pt.value}
                  onClick={() => updateModInfo({ project_type: pt.value })}
                  className={`px-2 py-0.5 rounded text-[11px] font-medium transition-all ${
                    manifest.mod_info.project_type === pt.value
                      ? 'bg-teal/15 text-teal border border-teal/30'
                      : 'bg-white/5 text-white/30 border border-white/5 hover:text-white/50'
                  }`}
                >
                  {pt.label}
                </button>
              ))}
            </div>
          </div>
          <div>
            <label className="block text-xs text-white/40 mb-1">License</label>
            {loading ? (
              <p className="text-xs text-white/20">Loading...</p>
            ) : (
              <select
                value={manifest.mod_info.license_id}
                onChange={(e) => updateModInfo({ license_id: e.target.value })}
                className="w-full bg-surface border border-white/10 rounded-lg px-3 py-1.5 text-sm text-white focus:outline-none focus:border-teal/50 transition-colors"
              >
                {licenses.map((l) => (
                  <option key={l.short} value={l.short}>{l.short} — {l.name}</option>
                ))}
              </select>
            )}
          </div>
        </div>

        {/* Row 4: Categories */}
        <div>
          <label className="block text-xs text-white/40 mb-1">Categories</label>
          {loading ? (
            <p className="text-xs text-white/20">Loading...</p>
          ) : (
            <div className="flex flex-wrap gap-1">
              {filteredCategories.map((cat) => (
                <button
                  key={cat.name}
                  onClick={() => toggleCategory(cat.name)}
                  className={`px-2 py-0.5 rounded text-[11px] font-medium transition-all ${
                    categoriesList.includes(cat.name)
                      ? 'bg-teal/15 text-teal border border-teal/30'
                      : 'bg-white/5 text-white/30 border border-white/5 hover:text-white/50'
                  }`}
                >
                  {cat.name}
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Row 5: Donation Links */}
        <div>
          <div className="flex items-center justify-between mb-1">
            <label className="block text-xs text-white/40">Donation Links</label>
            <button onClick={addDonationUrl} className="text-[11px] text-teal hover:text-teal/80 transition-colors">+ Add</button>
          </div>
          <div className="space-y-1.5">
            {donationUrls.map((donation, i) => (
              <div key={donation.id} className="flex gap-2 items-center">
                <select
                  value={donation.platform}
                  onChange={(e) => updateDonationUrl(i, 'platform', e.target.value)}
                  className="bg-surface border border-white/10 rounded-lg px-2 py-1 text-xs text-white focus:outline-none focus:border-teal/50 transition-colors"
                >
                  {DONATION_PLATFORMS.map((p) => (
                    <option key={p.value} value={p.value}>{p.label}</option>
                  ))}
                </select>
                <input
                  value={donation.url}
                  onChange={(e) => updateDonationUrl(i, 'url', e.target.value)}
                  className="flex-1 bg-surface border border-white/10 rounded-lg px-3 py-1 text-xs text-white focus:outline-none focus:border-teal/50 transition-colors"
                  placeholder="https://"
                />
                <button onClick={() => removeDonationUrl(i)} className="text-white/20 hover:text-coral text-xs transition-colors">✕</button>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
