import { useState, useEffect } from 'react'
import { useStore } from '../../store/useStore'
import { File, Hammer, Play, Loader2, Check, AlertCircle, ChevronDown, RefreshCw, Box } from 'lucide-react'
import type { Manifest } from '../../../../shared/types'

interface DetectedMod {
  name: string
  path: string
  modid: string
  version: string
  workspace: string
  loader: string
}

interface Props {
  manifest: Manifest
  updateManifest: (partial: Partial<Manifest>) => void
}

export function FilesSection({ manifest, updateManifest }: Props) {
  const { jarPath, setJarPath, sourcePath, setSourcePath, currentBundlePath } = useStore()

  const [detectedMods, setDetectedMods] = useState<DetectedMod[]>([])
  const [loadingMods, setLoadingMods] = useState(true)
  const [compiling, setCompiling] = useState(false)
  const [compileResult, setCompileResult] = useState<{ success: boolean; message: string } | null>(null)
  const [launching, setLaunching] = useState(false)
  const [launchResult, setLaunchResult] = useState<{ success: boolean; message: string } | null>(null)
  const [instances, setInstances] = useState<string[]>([])
  const [selectedInstance, setSelectedInstance] = useState<string>('')
  const [showInstancePicker, setShowInstancePicker] = useState(false)
  const [workspaceInfo, setWorkspaceInfo] = useState<{ workspace: string; modName: string } | null>(null)

  // Auto-detect mods on mount
  useEffect(() => {
    scanMods()
  }, [])

  async function scanMods() {
    setLoadingMods(true)
    try {
      const mods = await window.electronAPI.modScanAllMods()
      setDetectedMods(mods)
    } catch {
      setDetectedMods([])
    } finally {
      setLoadingMods(false)
    }
  }

  // When source path changes, detect workspace
  useEffect(() => {
    if (!sourcePath) {
      setWorkspaceInfo(null)
      return
    }
    async function detect() {
      try {
        const info = await window.electronAPI.modDetectWorkspace(sourcePath)
        setWorkspaceInfo({ workspace: info.workspace, modName: info.modName })
      } catch {
        setWorkspaceInfo(null)
      }
    }
    detect()
  }, [sourcePath])

  async function handleModSelect(mod: DetectedMod) {
    setSourcePath(mod.path)
    setWorkspaceInfo({ workspace: mod.workspace, modName: mod.name })

    if (currentBundlePath) {
      await window.electronAPI.bundleCopyDir(mod.path, currentBundlePath, 'source')
    }

    updateManifest({ files: { ...manifest.files, source: 'source/' } })
  }

  async function handleJarSelect() {
    const defaultPaths = await window.electronAPI.dialogGetDefaultPaths()
    const path = await window.electronAPI.dialogOpenFile({
      title: 'Select Mod Jar',
      defaultPath: defaultPaths.modDevDir,
      filters: [{ name: 'Jar Files', extensions: ['jar'] }],
    })
    if (!path) return

    setJarPath(path)
    const jarName = path.split('/').pop() || 'mod.jar'

    if (currentBundlePath) {
      await window.electronAPI.bundleCopyFile(path, currentBundlePath, `jar/${jarName}`)
    }

    updateManifest({ files: { ...manifest.files, jar: `jar/${jarName}` } })
  }

  async function handleCompile() {
    if (!sourcePath) return
    setCompiling(true)
    setCompileResult(null)

    try {
      const info = await window.electronAPI.modDetectWorkspace(sourcePath)
      const modName = info.modName || sourcePath.split('/').pop() || ''

      const result = await window.electronAPI.modCompile(sourcePath, modName)

      if (result.success && result.jarPath) {
        setJarPath(result.jarPath)
        const jarName = result.jarPath.split('/').pop() || 'mod.jar'

        if (currentBundlePath) {
          await window.electronAPI.bundleCopyFile(result.jarPath, currentBundlePath, `jar/${jarName}`)
        }

        updateManifest({ files: { ...manifest.files, jar: `jar/${jarName}` } })
        setCompileResult({ success: true, message: `Built: ${jarName}` })
      } else {
        setCompileResult({ success: false, message: 'Build failed. Check output.' })
      }
    } catch (err: any) {
      setCompileResult({ success: false, message: err.message || 'Compile error' })
    } finally {
      setCompiling(false)
    }
  }

  async function handleLaunch() {
    if (!jarPath) return

    setLaunching(true)
    setLaunchResult(null)

    try {
      let inst = instances
      if (inst.length === 0) {
        inst = await window.electronAPI.modListInstances()
        setInstances(inst)
      }

      let instanceName = selectedInstance
      if (!instanceName && workspaceInfo) {
        if (workspaceInfo.workspace === '1.12.2-forge') {
          instanceName = inst.find(i => i.includes('1.12.2')) || inst[0] || ''
        } else if (workspaceInfo.workspace === '1.21.1-fabric') {
          instanceName = inst.find(i => i.includes('1.20') || i.includes('1.21')) || inst[0] || ''
        } else {
          instanceName = inst[0] || ''
        }
      }

      if (!instanceName) {
        setLaunchResult({ success: false, message: 'No Prism Launcher instance found' })
        return
      }

      const result = await window.electronAPI.modLaunchClient(jarPath, instanceName)
      setLaunchResult(result)
    } catch (err: any) {
      setLaunchResult({ success: false, message: err.message || 'Launch error' })
    } finally {
      setLaunching(false)
    }
  }

  async function handleLoadInstances() {
    const inst = await window.electronAPI.modListInstances()
    setInstances(inst)
    setShowInstancePicker(true)
  }

  const selectedMod = detectedMods.find(m => m.path === sourcePath)

  return (
    <div className="bg-charcoal/50 rounded-xl border border-white/5 p-5">
      <h2 className="text-sm font-semibold text-white mb-3">Files</h2>
      <div className="space-y-3">

        {/* Detected Mods */}
        <div>
          <div className="flex items-center justify-between mb-1.5">
            <label className="block text-xs text-white/40">Source Code</label>
            <button onClick={scanMods} className="p-0.5 text-white/30 hover:text-teal transition-colors" title="Refresh">
              <RefreshCw size={12} className={loadingMods ? 'animate-spin' : ''} />
            </button>
          </div>

          {loadingMods ? (
            <p className="text-xs text-white/20 py-2">Scanning for mods...</p>
          ) : detectedMods.length === 0 ? (
            <p className="text-xs text-white/20 py-2">No mods detected in Mod Development</p>
          ) : (
            <div className="space-y-1 max-h-40 overflow-y-auto">
              {detectedMods.map((mod) => (
                <button
                  key={mod.path}
                  onClick={() => handleModSelect(mod)}
                  className={`w-full text-left px-3 py-2 rounded-lg transition-all ${
                    sourcePath === mod.path
                      ? 'bg-teal/10 border border-teal/30'
                      : 'bg-surface border border-white/5 hover:border-white/15'
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <Box size={14} className={sourcePath === mod.path ? 'text-teal' : 'text-white/20'} />
                      <span className={`text-xs font-medium ${sourcePath === mod.path ? 'text-teal' : 'text-white/60'}`}>
                        {mod.name}
                      </span>
                    </div>
                    <div className="flex items-center gap-2">
                      <span className="text-[10px] px-1.5 py-0.5 rounded bg-white/5 text-white/30 font-mono">
                        {mod.loader}
                      </span>
                      {mod.version && (
                        <span className="text-[10px] px-1.5 py-0.5 rounded bg-white/5 text-white/30 font-mono">
                          v{mod.version}
                        </span>
                      )}
                    </div>
                  </div>
                  <p className="text-[10px] text-white/20 mt-0.5 ml-[22px]">{mod.workspace}</p>
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Jar selector */}
        <div>
          <label className="block text-xs text-white/40 mb-1.5">Version Jar</label>
          <div className="flex items-center gap-2">
            <button
              onClick={handleJarSelect}
              className="flex-1 flex items-center gap-2 px-3 py-2 bg-surface border border-white/10 rounded-lg text-xs hover:border-teal/30 transition-all"
            >
              <File size={14} className={jarPath ? 'text-teal' : 'text-white/20'} />
              <span className={jarPath ? 'text-white/60 font-mono truncate' : 'text-white/30'}>
                {jarPath ? jarPath.split('/').pop() : 'Select .jar file'}
              </span>
            </button>
          </div>
        </div>

        {/* Action buttons */}
        <div className="flex gap-2">
          <button
            onClick={handleCompile}
            disabled={!sourcePath || compiling}
            className={`flex-1 flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg text-xs font-medium transition-all ${
              compiling
                ? 'bg-teal/20 text-teal/60 cursor-wait'
                : sourcePath
                  ? 'bg-teal/10 text-teal hover:bg-teal/20 border border-teal/20'
                  : 'bg-white/5 text-white/20 cursor-not-allowed border border-white/5'
            }`}
          >
            {compiling ? (
              <><Loader2 size={13} className="animate-spin" /> Compiling...</>
            ) : (
              <><Hammer size={13} /> Compile</>
            )}
          </button>

          <div className="relative flex-1">
            <button
              onClick={handleLaunch}
              disabled={!jarPath || launching}
              className={`w-full flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg text-xs font-medium transition-all ${
                launching
                  ? 'bg-indigo-500/20 text-indigo-400/60 cursor-wait'
                  : jarPath
                    ? 'bg-indigo-500/10 text-indigo-400 hover:bg-indigo-500/20 border border-indigo-500/20'
                    : 'bg-white/5 text-white/20 cursor-not-allowed border border-white/5'
              }`}
            >
              {launching ? (
                <><Loader2 size={13} className="animate-spin" /> Launching...</>
              ) : (
                <><Play size={13} /> Launch Client</>
              )}
            </button>
            {jarPath && instances.length > 0 && (
              <button
                onClick={handleLoadInstances}
                className="absolute right-1.5 top-1/2 -translate-y-1/2 p-0.5 text-white/30 hover:text-white/60"
              >
                <ChevronDown size={12} />
              </button>
            )}
          </div>
        </div>

        {/* Instance picker */}
        {showInstancePicker && instances.length > 0 && (
          <div className="bg-surface rounded-lg border border-white/10 p-2 max-h-28 overflow-y-auto">
            <p className="text-[10px] text-white/30 mb-1 px-1">Select Prism Launcher instance:</p>
            {instances.map((inst) => (
              <button
                key={inst}
                onClick={() => { setSelectedInstance(inst); setShowInstancePicker(false) }}
                className={`w-full text-left px-2 py-1 rounded text-xs transition-all ${
                  selectedInstance === inst
                    ? 'bg-teal/10 text-teal'
                    : 'text-white/50 hover:bg-white/5 hover:text-white/70'
                }`}
              >
                {inst}
              </button>
            ))}
          </div>
        )}

        {/* Results */}
        {compileResult && (
          <div className={`flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs ${
            compileResult.success ? 'bg-green-500/10 text-green-400' : 'bg-red-500/10 text-red-400'
          }`}>
            {compileResult.success ? <Check size={12} /> : <AlertCircle size={12} />}
            {compileResult.message}
          </div>
        )}
        {launchResult && (
          <div className={`flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs ${
            launchResult.success ? 'bg-green-500/10 text-green-400' : 'bg-red-500/10 text-red-400'
          }`}>
            {launchResult.success ? <Check size={12} /> : <AlertCircle size={12} />}
            {launchResult.message}
          </div>
        )}
      </div>
    </div>
  )
}
