import { ipcMain } from 'electron'
import { existsSync, readdirSync, readFileSync, rmSync, copyFileSync, mkdirSync } from 'fs'
import { join, basename } from 'path'
import { execSync } from 'child_process'
import { homedir } from 'os'

const PRISM_INSTANCES_DIR = join(homedir(), 'Library/Application Support/PrismLauncher/instances')
const JAVA8_HOME = '/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home'

function resolveModcompilerRoot(): string {
  let dir = __dirname
  let bestMatch = ''
  for (let i = 0; i < 10; i++) {
    if (existsSync(join(dir, 'CMP', 'BundleDrafts'))) {
      if (basename(dir) === 'ModCompiler') return dir
      if (!bestMatch) bestMatch = dir
    }
    const parent = join(dir, '..')
    if (parent === dir) break
    dir = parent
  }
  return bestMatch || process.cwd()
}

const MODCOMPILER_ROOT = resolveModcompilerRoot()
const MOD_DEV_DIR = join(MODCOMPILER_ROOT, 'Mod Development')

function parseModProperties(filePath: string): Record<string, string> {
  const content = readFileSync(filePath, 'utf-8')
  const props: Record<string, string> = {}
  for (const line of content.split('\n')) {
    const eqIndex = line.indexOf('=')
    if (eqIndex === -1) continue
    const key = line.slice(0, eqIndex).trim()
    const value = line.slice(eqIndex + 1).trim()
    if (key) props[key] = value
  }
  return props
}

function detectWorkspace(sourcePath: string): { workspace: string; workspacePath: string } | null {
  if (sourcePath.includes('1.12.2-forge')) {
    return { workspace: '1.12.2-forge', workspacePath: join(MOD_DEV_DIR, '1.12.2-forge') }
  }
  if (sourcePath.includes('1.21.1-fabric')) {
    return { workspace: '1.21.1-fabric', workspacePath: join(MOD_DEV_DIR, '1.21.1-fabric') }
  }
  return null
}

export function registerModDevHandlers(): void {
  ipcMain.handle('mod:compile', async (_event, { sourcePath, modName }: { sourcePath: string; modName: string }) => {
    try {
      const detected = detectWorkspace(sourcePath)
      if (!detected) {
        return { success: false, jarPath: null, output: `Could not detect workspace from sourcePath: ${sourcePath}` }
      }

      const { workspace, workspacePath } = detected
      if (!existsSync(workspacePath)) {
        return { success: false, jarPath: null, output: `Workspace directory not found: ${workspacePath}` }
      }

      const buildScript = join(workspacePath, 'build_mod.sh')
      if (!existsSync(buildScript)) {
        return { success: false, jarPath: null, output: `Build script not found: ${buildScript}` }
      }

      const javaHome = workspace === '1.12.2-forge' ? JAVA8_HOME : process.env.JAVA_HOME || ''
      const modDirName = modName || basename(sourcePath)

      let output: string
      try {
        output = execSync(`./build_mod.sh ${modDirName}`, {
          cwd: workspacePath,
          timeout: 600000,
          env: { ...process.env, JAVA_HOME: javaHome },
          encoding: 'utf-8',
          stdio: ['pipe', 'pipe', 'pipe'],
        })
      } catch (err: any) {
        const combined = (err.stdout || '') + (err.stderr || '')
        return { success: false, jarPath: null, output: combined || err.message }
      }

      const outputDir = join(workspacePath, 'output')
      if (!existsSync(outputDir)) {
        return { success: false, jarPath: null, output: output + '\nOutput directory not found after build.' }
      }

      const jars = readdirSync(outputDir).filter((f) => f.endsWith('.jar'))
      if (jars.length === 0) {
        return { success: false, jarPath: null, output: output + '\nNo JAR found in output directory after build.' }
      }

      const jarPath = join(outputDir, jars[jars.length - 1])
      return { success: true, jarPath, output }
    } catch (err: any) {
      return { success: false, jarPath: null, output: err.message || String(err) }
    }
  })

  ipcMain.handle('mod:launch-client', async (_event, { jarPath, instanceName }: { jarPath: string; instanceName: string }) => {
    try {
      if (!existsSync(jarPath)) {
        return { success: false, message: `JAR not found: ${jarPath}` }
      }

      const instanceDir = join(PRISM_INSTANCES_DIR, instanceName)
      if (!existsSync(instanceDir)) {
        return { success: false, message: `Prism Launcher instance not found: ${instanceDir}` }
      }

      const modsDir = join(instanceDir, 'minecraft', 'mods')

      if (existsSync(modsDir)) {
        const existingJars = readdirSync(modsDir).filter((f) => f.endsWith('.jar'))
        for (const jar of existingJars) {
          rmSync(join(modsDir, jar))
        }
      } else {
        mkdirSync(modsDir, { recursive: true })
      }

      const jarName = basename(jarPath)
      copyFileSync(jarPath, join(modsDir, jarName))

      try {
        execSync('open -a "Prism Launcher"', { timeout: 10000 })
      } catch {
        return { success: true, message: `JAR deployed to ${instanceName}, but failed to launch Prism Launcher. Please launch it manually.` }
      }

      return { success: true, message: `Deployed ${jarName} to ${instanceName} and launched Prism Launcher.` }
    } catch (err: any) {
      return { success: false, message: err.message || String(err) }
    }
  })

  ipcMain.handle('mod:list-instances', async () => {
    try {
      if (!existsSync(PRISM_INSTANCES_DIR)) return []
      return readdirSync(PRISM_INSTANCES_DIR, { withFileTypes: true })
        .filter((d) => d.isDirectory())
        .map((d) => d.name)
    } catch {
      return []
    }
  })

  ipcMain.handle('mod:detect-workspace', async (_event, { sourcePath }: { sourcePath: string }) => {
    const detected = detectWorkspace(sourcePath)
    if (!detected) {
      return { workspace: 'unknown', workspacePath: '', modName: basename(sourcePath), modProperties: null }
    }

    const modDirName = basename(sourcePath)
    const propsPath = join(sourcePath, 'mod.properties')
    const modProperties = existsSync(propsPath) ? parseModProperties(propsPath) : null

    return {
      workspace: detected.workspace,
      workspacePath: detected.workspacePath,
      modName: modDirName,
      modProperties,
    }
  })

  ipcMain.handle('mod:scan-mods', async (_event, { workspace }: { workspace: string }) => {
    const modsDir = join(MOD_DEV_DIR, workspace, 'mods')
    if (!existsSync(modsDir)) return []

    const results: Array<{ name: string; path: string; modid: string; version: string }> = []

    const entries = readdirSync(modsDir, { withFileTypes: true })
    for (const entry of entries) {
      if (!entry.isDirectory()) continue
      const modPath = join(modsDir, entry.name)
      const propsPath = join(modPath, 'mod.properties')
      if (!existsSync(propsPath)) continue

      const props = parseModProperties(propsPath)
      results.push({
        name: entry.name,
        path: modPath,
        modid: props.modid || '',
        version: props.version || '',
      })
    }

    return results
  })

  ipcMain.handle('mod:scan-all-mods', async () => {
    const workspaces: string[] = []
    if (existsSync(MOD_DEV_DIR)) {
      const entries = readdirSync(MOD_DEV_DIR, { withFileTypes: true })
      for (const entry of entries) {
        if (entry.isDirectory() && existsSync(join(MOD_DEV_DIR, entry.name, 'mods'))) {
          workspaces.push(entry.name)
        }
      }
    }

    const results: Array<{
      name: string
      path: string
      modid: string
      version: string
      workspace: string
      loader: string
    }> = []

    for (const ws of workspaces) {
      const modsDir = join(MOD_DEV_DIR, ws, 'mods')
      if (!existsSync(modsDir)) continue

      const loader = ws.includes('forge') ? 'Forge' : ws.includes('fabric') ? 'Fabric' : 'Unknown'

      const entries = readdirSync(modsDir, { withFileTypes: true })
      for (const entry of entries) {
        if (!entry.isDirectory()) continue
        const modPath = join(modsDir, entry.name)
        const propsPath = join(modPath, 'mod.properties')
        const props = existsSync(propsPath) ? parseModProperties(propsPath) : null

        results.push({
          name: entry.name,
          path: modPath,
          modid: props?.modid || '',
          version: props?.version || '',
          workspace: ws,
          loader,
        })
      }
    }

    return results
  })
}
