import { useState, useEffect } from 'react'
import type { Manifest, SideType, ModrinthLoader, ModrinthGameVersion, VersionDependency } from '../../../../shared/types'

const SIDE_OPTIONS: SideType[] = ['required', 'optional', 'unsupported', 'unknown']
const VERSION_TYPES = ['release', 'beta', 'alpha'] as const
const DEPENDENCY_TYPES = ['required', 'optional', 'incompatible', 'embedded'] as const

interface Props {
  manifest: Manifest
  updateManifest: (partial: Partial<Manifest>) => void
}

export function VersionInfoSection({ manifest, updateManifest }: Props) {
  const [loaders, setLoaders] = useState<ModrinthLoader[]>([])
  const [gameVersions, setGameVersions] = useState<ModrinthGameVersion[]>([])
  const [loading, setLoading] = useState(true)
  const [showSnapshots, setShowSnapshots] = useState(false)

  useEffect(() => {
    async function fetchTags() {
      try {
        const [ldrs, vers] = await Promise.all([
          window.electronAPI.modrinthLoaders(),
          window.electronAPI.modrinthGameVersions(),
        ])
        setLoaders(ldrs)
        setGameVersions(vers)
      } catch (err) {
        console.error('Failed to fetch Modrinth tags:', err)
      } finally {
        setLoading(false)
      }
    }
    fetchTags()
  }, [])

  function updateVersion(partial: Partial<typeof manifest.version_info>) {
    updateManifest({ version_info: { ...manifest.version_info, ...partial } })
  }

  const loadersList = manifest.version_info.loaders || []
  const mcVersions = manifest.version_info.minecraft_versions || []
  const dependencies = manifest.version_info.dependencies || []

  function toggleLoader(loader: string) {
    const current = loadersList
    const updated = current.includes(loader)
      ? current.filter((l) => l !== loader)
      : [...current, loader]
    updateVersion({ loaders: updated })
  }

  function toggleMcVersion(ver: string) {
    const versions = mcVersions.includes(ver)
      ? mcVersions.filter((v) => v !== ver)
      : [...mcVersions, ver]
    updateVersion({ minecraft_versions: versions })
  }

  function addDependency() {
    updateVersion({
      dependencies: [
        ...dependencies,
        { project_id: '', version_id: '', file_name: '', dependency_type: 'optional' },
      ],
    })
  }

  function removeDependency(index: number) {
    updateVersion({
      dependencies: dependencies.filter((_, i) => i !== index),
    })
  }

  function updateDependency(index: number, field: keyof VersionDependency, value: string) {
    const updated = [...dependencies]
    updated[index] = { ...updated[index], [field]: value }
    updateVersion({ dependencies: updated })
  }

  const filteredLoaders = loaders.filter(
    (l) => l.supported_project_types.includes(manifest.mod_info.project_type)
  )

  const filteredGameVersions = gameVersions.filter(
    (v) => showSnapshots || v.version_type === 'release'
  )

  return (
    <div className="bg-charcoal/50 rounded-xl border border-white/5 p-5">
      <h2 className="text-sm font-semibold text-white mb-4">Version Info</h2>
      <div className="space-y-3">
        {/* Row 1: Version + Type + Featured */}
        <div className="grid grid-cols-4 gap-3">
          <div>
            <label className="block text-xs text-white/40 mb-1">Mod Version</label>
            <input
              value={manifest.version_info.mod_version}
              onChange={(e) => updateVersion({ mod_version: e.target.value })}
              className="w-full bg-surface border border-white/10 rounded-lg px-3 py-1.5 text-sm text-white font-mono focus:outline-none focus:border-teal/50 transition-colors"
              placeholder="1.0.0"
            />
          </div>
          <div>
            <label className="block text-xs text-white/40 mb-1">Version Type</label>
            <div className="flex gap-1">
              {VERSION_TYPES.map((vt) => (
                <button
                  key={vt}
                  onClick={() => updateVersion({ version_type: vt })}
                  className={`flex-1 px-2 py-1.5 rounded-lg text-xs font-medium transition-all ${
                    manifest.version_info.version_type === vt
                      ? 'bg-teal/15 text-teal border border-teal/30'
                      : 'bg-white/5 text-white/30 border border-white/5 hover:text-white/50'
                  }`}
                >
                  {vt.charAt(0).toUpperCase() + vt.slice(1)}
                </button>
              ))}
            </div>
          </div>
          <div>
            <label className="block text-xs text-white/40 mb-1">Client Side</label>
            <select
              value={manifest.version_info.client_side}
              onChange={(e) => updateVersion({ client_side: e.target.value as SideType })}
              className="w-full bg-surface border border-white/10 rounded-lg px-3 py-1.5 text-sm text-white focus:outline-none focus:border-teal/50 transition-colors"
            >
              {SIDE_OPTIONS.map((o) => <option key={o} value={o}>{o}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-xs text-white/40 mb-1">Server Side</label>
            <select
              value={manifest.version_info.server_side}
              onChange={(e) => updateVersion({ server_side: e.target.value as SideType })}
              className="w-full bg-surface border border-white/10 rounded-lg px-3 py-1.5 text-sm text-white focus:outline-none focus:border-teal/50 transition-colors"
            >
              {SIDE_OPTIONS.map((o) => <option key={o} value={o}>{o}</option>)}
            </select>
          </div>
        </div>

        {/* Row 2: Loaders */}
        <div>
          <label className="block text-xs text-white/40 mb-1">Loaders</label>
          {loading ? (
            <p className="text-xs text-white/20">Loading...</p>
          ) : (
            <div className="flex flex-wrap gap-1">
              {filteredLoaders.map((loader) => (
                <button
                  key={loader.name}
                  onClick={() => toggleLoader(loader.name)}
                  className={`px-2.5 py-1 rounded-md text-xs font-medium transition-all ${
                    manifest.version_info.loaders?.includes(loader.name)
                      ? 'bg-teal/15 text-teal border border-teal/30'
                      : 'bg-white/5 text-white/30 border border-white/5 hover:text-white/50'
                  }`}
                >
                  {loader.name.charAt(0).toUpperCase() + loader.name.slice(1)}
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Row 3: MC Versions */}
        <div>
          <div className="flex items-center justify-between mb-1">
            <label className="block text-xs text-white/40">Minecraft Versions</label>
            <label className="flex items-center gap-1.5 text-xs text-white/30 cursor-pointer">
              <input type="checkbox" checked={showSnapshots} onChange={(e) => setShowSnapshots(e.target.checked)} className="accent-teal" />
              Snapshots
            </label>
          </div>
          {loading ? (
            <p className="text-xs text-white/20">Loading...</p>
          ) : (
            <div className="flex flex-wrap gap-1">
              {filteredGameVersions.map((gv) => (
                <button
                  key={gv.version}
                  onClick={() => toggleMcVersion(gv.version)}
                  className={`px-2 py-0.5 rounded text-[11px] font-mono font-medium transition-all ${
                    mcVersions.includes(gv.version)
                      ? 'bg-teal/15 text-teal border border-teal/30'
                      : 'bg-white/5 text-white/20 border border-white/5 hover:text-white/40'
                  }`}
                >
                  {gv.version}
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Row 4: Changelog + Dependencies */}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs text-white/40 mb-1">Changelog</label>
            <textarea
              value={manifest.version_info.changelog}
              onChange={(e) => updateVersion({ changelog: e.target.value })}
              className="w-full bg-surface border border-white/10 rounded-lg px-3 py-1.5 text-sm text-white focus:outline-none focus:border-teal/50 transition-colors min-h-[80px] resize-y"
              placeholder="What's new in this version..."
            />
          </div>
          <div>
            <div className="flex items-center justify-between mb-1">
              <label className="block text-xs text-white/40">Dependencies</label>
              <button onClick={addDependency} className="text-[11px] text-teal hover:text-teal/80 transition-colors">+ Add</button>
            </div>
            <div className="space-y-1.5">
              {dependencies.map((dep, i) => (
                <div key={i} className="flex gap-1.5 items-center">
                  <input
                    value={dep.project_id}
                    onChange={(e) => updateDependency(i, 'project_id', e.target.value)}
                    className="flex-1 bg-surface border border-white/10 rounded-lg px-2 py-1 text-[11px] text-white font-mono focus:outline-none focus:border-teal/50 transition-colors"
                    placeholder="Project ID"
                  />
                  <select
                    value={dep.dependency_type}
                    onChange={(e) => updateDependency(i, 'dependency_type', e.target.value)}
                    className="bg-surface border border-white/10 rounded-lg px-2 py-1 text-[11px] text-white focus:outline-none focus:border-teal/50 transition-colors"
                  >
                    {DEPENDENCY_TYPES.map((dt) => (
                      <option key={dt} value={dt}>{dt}</option>
                    ))}
                  </select>
                  <button onClick={() => removeDependency(i)} className="text-white/20 hover:text-coral text-xs transition-colors">✕</button>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Featured */}
        <div className="flex items-center gap-2">
          <input type="checkbox" checked={manifest.version_info.featured} onChange={(e) => updateVersion({ featured: e.target.checked })} className="accent-teal" />
          <label className="text-xs text-white/40">Featured version</label>
        </div>
      </div>
    </div>
  )
}
