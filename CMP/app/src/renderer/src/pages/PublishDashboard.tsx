import { useState, useRef, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Rocket, CheckCircle2, XCircle, AlertCircle, Loader2, ArrowRight } from 'lucide-react'
import { useStore } from '../store/useStore'
import type { PublishStep, PublishLogEntry } from '../../../shared/types'

const STEPS: { key: PublishStep; label: string }[] = [
  { key: 'github-check', label: 'Check GitHub Auth' },
  { key: 'github-create', label: 'Create GitHub Repo' },
  { key: 'github-push', label: 'Push Source Code' },
  { key: 'github-wiki', label: 'Create Wiki & Issues' },
  { key: 'modrinth-create', label: 'Create Modrinth Project' },
  { key: 'modrinth-upload', label: 'Upload Jar Version' },
  { key: 'modrinth-gallery', label: 'Upload Gallery Images' },
  { key: 'modrinth-update', label: 'Update Project Links' },
]

export function PublishDashboard() {
  const navigate = useNavigate()
  const {
    manifest, currentBundlePath, jarPath, sourcePath,
    publishStep, setPublishStep, publishLogs, addPublishLog, clearPublishLogs,
    modrinthToken, setModrinthToken,
  } = useStore()
  const logRef = useRef<HTMLDivElement>(null)
  const [publishing, setPublishing] = useState(false)

  useEffect(() => {
    if (logRef.current) {
      logRef.current.scrollTop = logRef.current.scrollHeight
    }
  }, [publishLogs])

  useEffect(() => {
    if (!manifest) {
      navigate('/')
    }
  }, [manifest, navigate])

  if (!manifest) {
    return null
  }

  function log(step: PublishStep, status: PublishLogEntry['status'], message: string) {
    addPublishLog({ timestamp: new Date().toISOString(), step, status, message })
  }

  async function handlePublish() {
    if (!manifest || !currentBundlePath) return
    setPublishing(true)
    clearPublishLogs()

    try {
      // Step 1: Check GitHub auth
      setPublishStep('github-check')
      log('github-check', 'info', 'Checking GitHub authentication...')
      const ghCheck = await window.electronAPI.githubCheck()
      if (!ghCheck.authenticated) {
        log('github-check', 'error', ghCheck.error)
        setPublishStep('error')
        setPublishing(false)
        return
      }
      log('github-check', 'success', `GitHub authenticated as ${ghCheck.owner}`)

      // Auto-derive owner and repo
      const ghOwner = ghCheck.owner || manifest.publishing.github_owner
      const ghRepo = manifest.publishing.github_repo_name || manifest.mod_info.slug

      // Step 2: Create GitHub repo
      setPublishStep('github-create')
      log('github-create', 'info', `Creating repository ${ghOwner}/${ghRepo}...`)
      const ghResult = await window.electronAPI.githubCreate({
        owner: ghOwner,
        repo: ghRepo,
        sourceDir: sourcePath,
        modName: manifest.mod_info.name,
        modSummary: manifest.mod_info.summary,
      })
      log('github-create', 'success', `Repository created: ${ghResult.repoUrl}`)

      // Step 3: Push source
      setPublishStep('github-push')
      log('github-push', 'info', 'Pushing source code to GitHub...')
      await window.electronAPI.githubPush({
        owner: ghOwner,
        repo: ghRepo,
        sourceDir: sourcePath,
        modName: manifest.mod_info.name,
        modSummary: manifest.mod_info.summary,
      })
      log('github-push', 'success', 'Source code pushed')

      // Step 4: Create wiki
      setPublishStep('github-wiki')
      log('github-wiki', 'info', 'Creating wiki and issue templates...')
      const wikiResult = await window.electronAPI.githubWiki({
        owner: ghOwner,
        repo: ghRepo,
        modName: manifest.mod_info.name,
      })
      log('github-wiki', 'success', wikiResult.created ? 'Wiki created' : 'Wiki skipped')

      // Step 5: Create Modrinth project
      setPublishStep('modrinth-create')
      log('modrinth-create', 'info', 'Creating Modrinth project...')
      const iconPath = manifest.icon ? `${currentBundlePath}/${manifest.icon}` : ''
      const mrResult = await window.electronAPI.modrinthCreateProject({
        token: modrinthToken,
        manifest,
        iconPath,
      })
      log('modrinth-create', 'success', `Project created: ${mrResult.slug} (${mrResult.projectId})`)

      // Step 6: Upload version
      setPublishStep('modrinth-upload')
      log('modrinth-upload', 'info', 'Uploading jar as version...')
      const jarFilePath = jarPath || (manifest.files.jar ? `${currentBundlePath}/${manifest.files.jar}` : '')
      const versionResult = await window.electronAPI.modrinthUploadVersion({
        token: modrinthToken,
        projectId: mrResult.projectId,
        manifest,
        jarPath: jarFilePath,
      })
      log('modrinth-upload', 'success', `Version uploaded: ${versionResult.versionId}`)

      // Step 7: Upload gallery
      setPublishStep('modrinth-gallery')
      log('modrinth-gallery', 'info', `Uploading ${manifest.gallery.length} gallery images...`)
      const galleryItems = manifest.gallery.map((img) => ({
        imagePath: `${currentBundlePath}/${img.file}`,
        featured: img.featured,
        title: img.title,
        description: img.description,
      }))
      await window.electronAPI.modrinthUploadGallery({
        token: modrinthToken,
        projectRef: mrResult.projectId,
        gallery: galleryItems,
      })
      log('modrinth-gallery', 'success', 'Gallery images uploaded')

      // Step 8: Update project with links and description
      setPublishStep('modrinth-update')
      log('modrinth-update', 'info', 'Updating project description and links...')
      await window.electronAPI.modrinthUpdateProject({
        token: modrinthToken,
        projectRef: mrResult.projectId,
        description: manifest.description.body,
        links: {
          source_url: ghResult.repoUrl,
          issues_url: ghResult.issuesUrl,
          wiki_url: ghResult.wikiUrl,
          discord_url: manifest.links.discord_url,
        },
        manifest,
      })
      log('modrinth-update', 'success', 'Project updated with GitHub links')

      setPublishStep('complete')
      log('complete', 'success', 'Publish complete!')
    } catch (err: any) {
      log(publishStep, 'error', err.message || String(err))
      setPublishStep('error')
    } finally {
      setPublishing(false)
    }
  }

  function getStepStatus(step: PublishStep): 'pending' | 'active' | 'done' | 'error' {
    const stepIndex = STEPS.findIndex((s) => s.key === step)
    const currentIndex = STEPS.findIndex((s) => s.key === publishStep)
    if (publishStep === 'error') {
      return step === STEPS.find((s) => publishLogs.some((l) => l.step === s.key && l.status === 'error'))?.key ? 'error' : stepIndex < currentIndex ? 'done' : 'pending'
    }
    if (publishStep === 'complete') return 'done'
    if (stepIndex === currentIndex) return 'active'
    if (stepIndex < currentIndex) return 'done'
    return 'pending'
  }

  return (
    <div className="h-full flex flex-col animate-fade-in">
      {/* Header */}
      <div className="px-6 py-4 bg-charcoal/50 border-b border-white/5">
        <h1 className="text-lg font-bold text-white">Publish Dashboard</h1>
        <p className="text-white/40 text-xs mt-0.5">{manifest.mod_info.name} v{manifest.version_info.mod_version}</p>
      </div>

      <div className="flex-1 overflow-auto p-6">
        <div className="max-w-3xl mx-auto space-y-6">
          {/* Token Input */}
          <div className="bg-charcoal/50 rounded-xl border border-white/5 p-5">
            <h2 className="text-sm font-semibold text-white mb-3">Modrinth Token</h2>
            <input
              type="password"
              value={modrinthToken}
              onChange={(e) => setModrinthToken(e.target.value)}
              placeholder="Enter your Modrinth API token"
              className="w-full bg-surface border border-white/10 rounded-lg px-3 py-2 text-sm text-white placeholder-white/20 focus:outline-none focus:border-teal/50 transition-colors"
            />
            <p className="text-white/20 text-xs mt-2">Get your token from modrinth.com/settings/account</p>
          </div>

          {/* Steps Progress */}
          <div className="bg-charcoal/50 rounded-xl border border-white/5 p-5">
            <h2 className="text-sm font-semibold text-white mb-4">Publish Steps</h2>
            <div className="space-y-2">
              {STEPS.map((step, i) => {
                const status = getStepStatus(step.key)
                return (
                  <div key={step.key} className="flex items-center gap-3">
                    <div className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-mono ${
                      status === 'done' ? 'bg-teal/20 text-teal' :
                      status === 'active' ? 'bg-teal/10 text-teal animate-pulse' :
                      status === 'error' ? 'bg-coral/20 text-coral' :
                      'bg-white/5 text-white/20'
                    }`}>
                      {status === 'done' ? <CheckCircle2 size={14} /> :
                       status === 'error' ? <XCircle size={14} /> :
                       status === 'active' ? <Loader2 size={14} className="animate-spin" /> :
                       <span>{i + 1}</span>}
                    </div>
                    <span className={`text-sm ${
                      status === 'done' ? 'text-teal' :
                      status === 'active' ? 'text-white' :
                      status === 'error' ? 'text-coral' :
                      'text-white/30'
                    }`}>
                      {step.label}
                    </span>
                    {i < STEPS.length - 1 && (
                      <div className={`ml-3 flex-1 h-px ${status === 'done' ? 'bg-teal/30' : 'bg-white/5'}`} />
                    )}
                  </div>
                )
              })}
            </div>
          </div>

          {/* Publish Button */}
          <button
            onClick={handlePublish}
            disabled={publishing || !modrinthToken}
            className={`w-full flex items-center justify-center gap-2 px-6 py-3 rounded-xl font-semibold text-sm transition-all ${
              publishing || !modrinthToken
                ? 'bg-white/5 text-white/20 cursor-not-allowed'
                : 'bg-teal text-charcoal hover:bg-teal/90 glow-teal'
            }`}
          >
            {publishing ? (
              <>
                <Loader2 size={16} className="animate-spin" />
                Publishing...
              </>
            ) : (
              <>
                <Rocket size={16} />
                Publish to GitHub & Modrinth
              </>
            )}
          </button>

          {/* Log */}
          {publishLogs.length > 0 && (
            <div className="bg-charcoal/50 rounded-xl border border-white/5 p-5">
              <h2 className="text-sm font-semibold text-white mb-3">Publish Log</h2>
              <div ref={logRef} className="max-h-64 overflow-auto font-mono text-xs space-y-1">
                {publishLogs.map((entry, i) => (
                  <div key={i} className={`${
                    entry.status === 'success' ? 'text-teal' :
                    entry.status === 'error' ? 'text-coral' :
                    entry.status === 'warning' ? 'text-yellow-400' :
                    'text-white/40'
                  }`}>
                    <span className="text-white/20">{new Date(entry.timestamp).toLocaleTimeString()}</span>{' '}
                    {entry.message}
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
