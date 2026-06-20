import { ipcMain } from 'electron'
import { readFileSync, existsSync } from 'fs'
import { join } from 'path'
import type { Manifest } from '../../shared/types'

const MODRINTH_API = 'https://api.modrinth.com/v2'

async function modrinthRequest(
  method: string,
  path: string,
  token: string,
  options: { body?: any; headers?: Record<string, string> } = {}
): Promise<any> {
  const url = `${MODRINTH_API}${path}`
  const headers: Record<string, string> = {
    'User-Agent': 'CMP/1.0 (Center Mod Publishment)',
    Authorization: token,
    ...options.headers,
  }

  const fetchOptions: RequestInit = {
    method,
    headers,
  }

  if (options.body && !headers['Content-Type']?.includes('multipart')) {
    headers['Content-Type'] = 'application/json'
    fetchOptions.body = JSON.stringify(options.body)
  } else if (options.body) {
    fetchOptions.body = options.body
  }

  const response = await fetch(url, fetchOptions)

  if (!response.ok) {
    const text = await response.text()
    throw new Error(`Modrinth API error (${response.status}): ${text.slice(0, 300)}`)
  }

  const text = await response.text()
  if (!text) return {}
  return JSON.parse(text)
}

function buildMultipartBody(fields: Record<string, string>, files: Array<{ name: string; filename: string; data: Buffer; contentType: string }>): { body: Buffer; contentType: string } {
  const boundary = `cmp-${Date.now()}-${Math.random().toString(36).slice(2)}`
  const parts: Buffer[] = []

  for (const [name, value] of Object.entries(fields)) {
    parts.push(Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="${name}"\r\n\r\n${value}\r\n`))
  }

  for (const file of files) {
    parts.push(Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="${file.name}"; filename="${file.filename}"\r\nContent-Type: ${file.contentType}\r\n\r\n`))
    parts.push(file.data)
    parts.push(Buffer.from('\r\n'))
  }

  parts.push(Buffer.from(`--${boundary}--\r\n`))

  return {
    body: Buffer.concat(parts),
    contentType: `multipart/form-data; boundary=${boundary}`,
  }
}

export function registerModrinthHandlers(): void {
  ipcMain.handle('publish:modrinth-create-project', async (_event, config: {
    token: string
    manifest: Manifest
    iconPath: string
  }) => {
    const { token, manifest, iconPath } = config

    const data: Record<string, any> = {
      name: manifest.mod_info.name,
      slug: manifest.mod_info.slug,
      summary: manifest.mod_info.summary,
      project_type: manifest.mod_info.project_type,
      categories: manifest.mod_info.categories,
      additional_categories: manifest.mod_info.additional_categories,
      client_side: manifest.version_info.client_side,
      server_side: manifest.version_info.server_side,
      initial_versions: [],
      is_draft: true,
      license_id: manifest.mod_info.license_id,
      license_url: manifest.mod_info.license_url,
      donation_urls: manifest.mod_info.donation_urls,
      discord_url: manifest.links.discord_url,
    }

    if (manifest.publishing.requested_status) {
      data.requested_status = manifest.publishing.requested_status
    }

    const fields: Record<string, string> = {
      data: JSON.stringify(data),
    }

    const files: Array<{ name: string; filename: string; data: Buffer; contentType: string }> = []

    if (iconPath && existsSync(iconPath)) {
      const iconData = readFileSync(iconPath)
      const ext = iconPath.split('.').pop() || 'png'
      files.push({
        name: 'icon',
        filename: `icon.${ext}`,
        data: iconData,
        contentType: ext === 'svg' ? 'image/svg+xml' : `image/${ext}`,
      })
    }

    const { body, contentType } = buildMultipartBody(fields, files)
    const result = await modrinthRequest('POST', '/project', token, {
      body,
      headers: { 'Content-Type': contentType },
    })

    return { projectId: result.id, slug: result.slug }
  })

  ipcMain.handle('publish:modrinth-upload-version', async (_event, config: {
    token: string
    projectId: string
    manifest: Manifest
    jarPath: string
  }) => {
    const { token, projectId, manifest, jarPath } = config

    if (!existsSync(jarPath)) {
      throw new Error(`Jar file not found: ${jarPath}`)
    }

    const jarData = readFileSync(jarPath)
    const jarName = jarPath.split('/').pop() || 'mod.jar'

    const versionTitle = `${manifest.mod_info.name} ${manifest.version_info.mod_version} (${manifest.version_info.loaders.join(', ')} ${manifest.version_info.minecraft_versions.join(', ')})`

    const fields: Record<string, string> = {
      data: JSON.stringify({
        name: versionTitle,
        version_number: manifest.version_info.mod_version,
        changelog: manifest.version_info.changelog,
        dependencies: manifest.version_info.dependencies,
        game_versions: manifest.version_info.minecraft_versions,
        version_type: manifest.version_info.version_type,
        loaders: manifest.version_info.loaders,
        featured: manifest.version_info.featured,
        project_id: projectId,
        file_parts: ['file'],
        primary_file: 'file',
      }),
    }

    const files = [{
      name: 'file',
      filename: jarName,
      data: jarData,
      contentType: 'application/java-archive',
    }]

    const { body, contentType } = buildMultipartBody(fields, files)
    const result = await modrinthRequest('POST', '/version', token, {
      body,
      headers: { 'Content-Type': contentType },
    })

    return { versionId: result.id }
  })

  ipcMain.handle('publish:modrinth-upload-gallery', async (_event, config: {
    token: string
    projectRef: string
    gallery: Array<{ imagePath: string; featured: boolean; title: string; description: string }>
  }) => {
    const { token, projectRef, gallery } = config

    for (const item of gallery) {
      if (!existsSync(item.imagePath)) continue

      const imageData = readFileSync(item.imagePath)
      const ext = item.imagePath.split('.').pop() || 'png'

      // Upload gallery image via PATCH-style endpoint
      const params = new URLSearchParams({
        ext,
        featured: item.featured ? 'true' : 'false',
        title: item.title,
        description: item.description,
      })

      await modrinthRequest('POST', `/project/${encodeURIComponent(projectRef)}/gallery?${params}`, token, {
        body: imageData,
        headers: { 'Content-Type': ext === 'svg' ? 'image/svg+xml' : `image/${ext}` },
      })
    }

    return { success: true }
  })

  ipcMain.handle('publish:modrinth-update-project', async (_event, config: {
    token: string
    projectRef: string
    description: string
    links: { source_url: string; issues_url: string; wiki_url: string; discord_url: string }
    manifest: Manifest
  }) => {
    const { token, projectRef, description, links, manifest } = config

    const payload: Record<string, any> = {
      description,
    }

    if (links.source_url) payload.source_url = links.source_url
    if (links.issues_url) payload.issues_url = links.issues_url
    if (links.wiki_url) payload.wiki_url = links.wiki_url
    if (links.discord_url) payload.discord_url = links.discord_url
    if (manifest.mod_info.additional_categories?.length) payload.additional_categories = manifest.mod_info.additional_categories
    if (manifest.mod_info.license_id) payload.license_id = manifest.mod_info.license_id
    if (manifest.mod_info.license_url) payload.license_url = manifest.mod_info.license_url
    if (manifest.mod_info.donation_urls?.length) payload.donation_urls = manifest.mod_info.donation_urls

    await modrinthRequest('PATCH', `/project/${encodeURIComponent(projectRef)}`, token, {
      body: payload,
    })

    return { success: true }
  })

  ipcMain.handle('publish:modrinth-get-project', async (_event, config: {
    token: string
    projectRef: string
  }) => {
    const { token, projectRef } = config
    return await modrinthRequest('GET', `/project/${encodeURIComponent(projectRef)}`, token)
  })

  ipcMain.handle('publish:modrinth-upload-description-image', async (_event, config: {
    token: string
    projectRef: string
    imagePath: string
  }) => {
    const { token, projectRef, imagePath } = config

    if (!existsSync(imagePath)) {
      throw new Error(`Image not found: ${imagePath}`)
    }

    const imageData = readFileSync(imagePath)
    const ext = imagePath.split('.').pop() || 'png'

    // Modrinth uses the gallery endpoint for description images too
    // We upload and get back a URL
    const params = new URLSearchParams({
      ext,
      featured: 'false',
      title: 'Description image',
      description: '',
    })

    await modrinthRequest('POST', `/project/${encodeURIComponent(projectRef)}/gallery?${params}`, token, {
      body: imageData,
      headers: { 'Content-Type': ext === 'svg' ? 'image/svg+xml' : `image/${ext}` },
    })

    // After uploading, we need to get the image URL back
    // Modrinth returns the gallery item with a URL
    const project = await modrinthRequest('GET', `/project/${encodeURIComponent(projectRef)}`, token)
    const gallery = project.gallery || []
    const lastImage = gallery[gallery.length - 1]
    return { url: lastImage?.url || '' }
  })
}
