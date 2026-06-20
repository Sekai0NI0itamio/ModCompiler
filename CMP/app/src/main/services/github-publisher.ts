import { ipcMain } from 'electron'
import { execFile } from 'child_process'
import { promisify } from 'util'
import { existsSync, readFileSync, writeFileSync, mkdirSync, rmSync } from 'fs'
import { join } from 'path'
import { randomUUID } from 'crypto'

const execFileAsync = promisify(execFile)

async function runGh(args: string[], cwd?: string): Promise<{ stdout: string; stderr: string }> {
  try {
    const result = await execFileAsync('gh', args, {
      cwd: cwd || process.cwd(),
      env: { ...process.env },
      timeout: 60000,
    })
    return result
  } catch (error: any) {
    throw new Error(`gh CLI failed: ${error.stderr || error.message}`)
  }
}

async function runGit(args: string[], cwd: string): Promise<{ stdout: string; stderr: string }> {
  try {
    const result = await execFileAsync('git', args, {
      cwd,
      env: { ...process.env },
      timeout: 60000,
    })
    return result
  } catch (error: any) {
    throw new Error(`git failed: ${error.stderr || error.message}`)
  }
}

export function registerGitHubHandlers(): void {
  ipcMain.handle('publish:github-check', async () => {
    try {
      const { stdout } = await runGh(['auth', 'status'])
      // Extract username from output like "Logged in to github.com as username"
      const match = stdout.match(/Logged in to github\.com account (\S+)/i) ||
                    stdout.match(/as (\S+)/i)
      const owner = match ? match[1] : ''
      return { authenticated: true, owner }
    } catch (error: any) {
      // Try to parse username from stderr too
      const stderr = error.stderr || ''
      const match = stderr.match(/Logged in to github\.com account (\S+)/i) ||
                    stderr.match(/as (\S+)/i)
      if (match) {
        return { authenticated: true, owner: match[1] }
      }
      return { authenticated: false, error: 'gh CLI not authenticated. Run `gh auth login` first.', owner: '' }
    }
  })

  ipcMain.handle('publish:github-create', async (_event, config: {
    owner: string
    repo: string
    sourceDir: string
    modName: string
    modSummary: string
  }) => {
    const { owner, repo, sourceDir, modName, modSummary } = config

    // Create public repository
    const { stdout } = await runGh([
      'repo', 'create', `${owner}/${repo}`,
      '--public',
      '--description', modSummary,
    ])

    const repoUrl = `https://github.com/${owner}/${repo}`
    const issuesUrl = `${repoUrl}/issues`
    let wikiUrl = `${repoUrl}/wiki`

    return { repoUrl, issuesUrl, wikiUrl }
  })

  ipcMain.handle('publish:github-push', async (_event, config: {
    owner: string
    repo: string
    sourceDir: string
    modName: string
    modSummary: string
  }) => {
    const { owner, repo, sourceDir, modName, modSummary } = config
    const tmpDir = join(process.cwd(), 'cmp-tmp-gh', randomUUID())

    try {
      // Clone the repo
      await runGit(['clone', `https://github.com/${owner}/${repo}.git`, tmpDir])

      // Copy source files
      const srcDest = join(tmpDir, 'src')
      if (existsSync(sourceDir)) {
        copyDirRecursive(sourceDir, srcDest)
      }

      // Generate README
      const readme = `# ${modName}\n\n${modSummary}\n\n## Installation\n\n1. Download the latest release\n2. Place the jar in your mods folder\n3. Enjoy!\n\n## Issues\n\nReport issues on the [Issues page](${`https://github.com/${owner}/${repo}/issues`}).\n`
      writeFileSync(join(tmpDir, 'README.md'), readme, 'utf-8')

      // Create issue templates
      const issueDir = join(tmpDir, '.github', 'ISSUE_TEMPLATE')
      mkdirSync(issueDir, { recursive: true })
      writeFileSync(join(issueDir, 'bug_report.md'), `---\nname: Bug Report\nabout: Report a bug\nlabels: bug\n---\n\n## Description\n\n## Steps to Reproduce\n\n1. \n\n## Expected Behavior\n\n## Actual Behavior\n\n## Mod Version\n\n## Minecraft Version\n`, 'utf-8')
      writeFileSync(join(issueDir, 'feature_request.md'), `---\nname: Feature Request\nabout: Suggest a feature\nlabels: enhancement\n---\n\n## Feature Description\n\n## Why\n\n## How\n`, 'utf-8')

      // Commit and push
      await runGit(['add', '.'], tmpDir)
      await runGit(['commit', '-m', 'Initial commit from CMP'], tmpDir)
      await runGit(['push', 'origin', 'main'], tmpDir)
    } finally {
      // Cleanup
      if (existsSync(tmpDir)) {
        rmSync(tmpDir, { recursive: true, force: true })
      }
    }

    return { success: true }
  })

  ipcMain.handle('publish:github-wiki', async (_event, config: {
    owner: string
    repo: string
    modName: string
  }) => {
    const { owner, repo, modName } = config
    const wikiUrl = `https://github.com/${owner}/${repo}/wiki`
    const tmpDir = join(process.cwd(), 'cmp-tmp-wiki', randomUUID())

    try {
      // Clone wiki
      try {
        await runGit(['clone', `${wikiUrl}.git`, tmpDir])
      } catch {
        // Wiki might not be enabled yet, that's okay
        return { wikiUrl, created: false }
      }

      // Create wiki pages
      writeFileSync(join(tmpDir, 'Home.md'), `# ${modName}\n\nWelcome to the ${modName} wiki.\n`, 'utf-8')
      writeFileSync(join(tmpDir, 'Installation.md'), `# Installation\n\n1. Download the latest release from Modrinth\n2. Place the jar in your mods folder\n3. Launch Minecraft\n`, 'utf-8')
      writeFileSync(join(tmpDir, 'Troubleshooting.md'), `# Troubleshooting\n\n## Common Issues\n\nIf you encounter issues, please report them on the [Issues page](https://github.com/${owner}/${repo}/issues).\n`, 'utf-8')

      await runGit(['add', '.'], tmpDir)
      await runGit(['commit', '-m', 'Add wiki pages from CMP'], tmpDir)
      await runGit(['push', 'origin', 'master'], tmpDir)
    } finally {
      if (existsSync(tmpDir)) {
        rmSync(tmpDir, { recursive: true, force: true })
      }
    }

    return { wikiUrl, created: true }
  })
}

function copyDirRecursive(src: string, dest: string): void {
  if (!existsSync(dest)) mkdirSync(dest, { recursive: true })
  const entries = require('fs').readdirSync(src, { withFileTypes: true })
  for (const entry of entries) {
    const srcPath = join(src, entry.name)
    const destPath = join(dest, entry.name)
    if (entry.isDirectory()) {
      copyDirRecursive(srcPath, destPath)
    } else {
      const buffer = readFileSync(srcPath)
      writeFileSync(destPath, buffer)
    }
  }
}
