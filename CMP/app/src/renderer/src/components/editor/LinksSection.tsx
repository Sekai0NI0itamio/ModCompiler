import type { Manifest } from '../../../../shared/types'

const REQUESTED_STATUSES = [
  { value: '', label: 'None' },
  { value: 'approved', label: 'Approved' },
  { value: 'archived', label: 'Archived' },
  { value: 'unlisted', label: 'Unlisted' },
  { value: 'private', label: 'Private' },
  { value: 'draft', label: 'Draft' },
]

interface Props {
  manifest: Manifest
  updateManifest: (partial: Partial<Manifest>) => void
}

export function LinksSection({ manifest, updateManifest }: Props) {
  function updateLinks(partial: Partial<typeof manifest.links>) {
    updateManifest({ links: { ...manifest.links, ...partial } })
  }

  function updatePublishing(partial: Partial<typeof manifest.publishing>) {
    updateManifest({ publishing: { ...manifest.publishing, ...partial } })
  }

  // GitHub owner/repo are auto-derived from the slug during publishing.
  // The github-publisher uses manifest.publishing.github_owner and github_repo_name,
  // but these are auto-set from the slug and gh auth status at publish time.
  // No manual input needed.

  return (
    <div className="bg-charcoal/50 rounded-xl border border-white/5 p-5">
      <h2 className="text-sm font-semibold text-white mb-4">Links & Publishing</h2>
      <div className="space-y-4">
        {/* Row 1: Modrinth ID + Requested Status */}
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-xs text-white/40 mb-1.5">Modrinth Project ID</label>
            <input
              value={manifest.publishing.modrinth_project_id}
              onChange={(e) => updatePublishing({ modrinth_project_id: e.target.value })}
              className="w-full bg-surface border border-white/10 rounded-lg px-3 py-2 text-sm text-white font-mono focus:outline-none focus:border-teal/50 transition-colors"
              placeholder="Auto-created if empty"
            />
          </div>
          <div>
            <label className="block text-xs text-white/40 mb-1.5">Requested Status</label>
            <select
              value={manifest.publishing.requested_status}
              onChange={(e) => updatePublishing({ requested_status: e.target.value as any })}
              className="w-full bg-surface border border-white/10 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-teal/50 transition-colors"
            >
              {REQUESTED_STATUSES.map((s) => (
                <option key={s.value} value={s.value}>{s.label}</option>
              ))}
            </select>
          </div>
        </div>

        {/* GitHub info — read-only, auto-derived */}
        <div className="px-3 py-2 bg-surface/50 rounded-lg border border-white/5">
          <p className="text-xs text-white/30">
            GitHub repo will be auto-created as <span className="text-white/50 font-mono">{manifest.publishing.github_owner || '&lt;gh-username&gt;'}/{manifest.publishing.github_repo_name || manifest.mod_info.slug}</span> at publish time
          </p>
        </div>

        {/* Row 2: Links — 4 columns */}
        <div className="grid grid-cols-4 gap-3">
          <div>
            <label className="block text-xs text-white/40 mb-1.5">Discord</label>
            <input
              value={manifest.links.discord_url}
              onChange={(e) => updateLinks({ discord_url: e.target.value })}
              className="w-full bg-surface border border-white/10 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-teal/50 transition-colors"
              placeholder="https://discord.gg/..."
            />
          </div>
          <div>
            <label className="block text-xs text-white/40 mb-1.5">Source</label>
            <input
              value={manifest.links.source_url}
              onChange={(e) => updateLinks({ source_url: e.target.value })}
              className="w-full bg-surface border border-white/10 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-teal/50 transition-colors"
              placeholder="Auto-filled after publish"
            />
          </div>
          <div>
            <label className="block text-xs text-white/40 mb-1.5">Issues</label>
            <input
              value={manifest.links.issues_url}
              onChange={(e) => updateLinks({ issues_url: e.target.value })}
              className="w-full bg-surface border border-white/10 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-teal/50 transition-colors"
              placeholder="Auto-filled after publish"
            />
          </div>
          <div>
            <label className="block text-xs text-white/40 mb-1.5">Wiki</label>
            <input
              value={manifest.links.wiki_url}
              onChange={(e) => updateLinks({ wiki_url: e.target.value })}
              className="w-full bg-surface border border-white/10 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-teal/50 transition-colors"
              placeholder="Auto-filled after publish"
            />
          </div>
        </div>
      </div>
    </div>
  )
}
