import { app, session, ipcMain, BrowserWindow, dialog, shell } from "electron";
import { join, basename } from "path";
import { existsSync, mkdirSync, writeFileSync, readFileSync, renameSync, readdirSync, statSync, rmSync, copyFileSync } from "fs";
import { execFile, execSync } from "child_process";
import { promisify } from "util";
import { randomUUID } from "crypto";
import { homedir } from "os";
import __cjs_mod__ from "node:module";
const __filename = import.meta.filename;
const __dirname = import.meta.dirname;
const require2 = __cjs_mod__.createRequire(import.meta.url);
const is = {
  dev: !app.isPackaged
};
const platform = {
  isWindows: process.platform === "win32",
  isMacOS: process.platform === "darwin",
  isLinux: process.platform === "linux"
};
const electronApp = {
  setAppUserModelId(id) {
    if (platform.isWindows)
      app.setAppUserModelId(is.dev ? process.execPath : id);
  },
  setAutoLaunch(auto) {
    if (platform.isLinux)
      return false;
    const isOpenAtLogin = () => {
      return app.getLoginItemSettings().openAtLogin;
    };
    if (isOpenAtLogin() !== auto) {
      app.setLoginItemSettings({ openAtLogin: auto });
      return isOpenAtLogin() === auto;
    } else {
      return true;
    }
  },
  skipProxy() {
    return session.defaultSession.setProxy({ mode: "direct" });
  }
};
const optimizer = {
  watchWindowShortcuts(window, shortcutOptions) {
    if (!window)
      return;
    const { webContents } = window;
    const { escToCloseWindow = false, zoom = false } = shortcutOptions || {};
    webContents.on("before-input-event", (event, input) => {
      if (input.type === "keyDown") {
        if (!is.dev) {
          if (input.code === "KeyR" && (input.control || input.meta))
            event.preventDefault();
          if (input.code === "KeyI" && (input.alt && input.meta || input.control && input.shift)) {
            event.preventDefault();
          }
        } else {
          if (input.code === "F12") {
            if (webContents.isDevToolsOpened()) {
              webContents.closeDevTools();
            } else {
              webContents.openDevTools({ mode: "undocked" });
              console.log("Open dev tool...");
            }
          }
        }
        if (escToCloseWindow) {
          if (input.code === "Escape" && input.key !== "Process") {
            window.close();
            event.preventDefault();
          }
        }
        if (!zoom) {
          if (input.code === "Minus" && (input.control || input.meta))
            event.preventDefault();
          if (input.code === "Equal" && input.shift && (input.control || input.meta))
            event.preventDefault();
        }
      }
    });
  },
  registerFramelessWindowIpc() {
    ipcMain.on("win:invoke", (event, action) => {
      const win = BrowserWindow.fromWebContents(event.sender);
      if (win) {
        if (action === "show") {
          win.show();
        } else if (action === "showInactive") {
          win.showInactive();
        } else if (action === "min") {
          win.minimize();
        } else if (action === "max") {
          const isMaximized = win.isMaximized();
          if (isMaximized) {
            win.unmaximize();
          } else {
            win.maximize();
          }
        } else if (action === "close") {
          win.close();
        }
      }
    });
  }
};
const APP_SUPPORT = app.getPath("appData");
const CMP_DIR$1 = join(APP_SUPPORT, "CMP");
const BUNDLES_INDEX = join(CMP_DIR$1, "bundles.json");
const TMP_DIR = join(CMP_DIR$1, "tmp");
function resolveModcompilerRoot$2() {
  let dir = __dirname;
  let bestMatch = "";
  for (let i = 0; i < 10; i++) {
    if (existsSync(join(dir, "CMP", "BundleDrafts"))) {
      if (basename(dir) === "ModCompiler") {
        return dir;
      }
      if (!bestMatch) bestMatch = dir;
    }
    const parent = join(dir, "..");
    if (parent === dir) break;
    dir = parent;
  }
  return bestMatch || process.cwd();
}
const MODCOMPILER_ROOT$2 = resolveModcompilerRoot$2();
const BUNDLE_DRAFTS_DIR$1 = join(MODCOMPILER_ROOT$2, "CMP", "BundleDrafts");
const BUNDLE_PUBLISHED_DIR$1 = join(MODCOMPILER_ROOT$2, "CMP", "BundlePublished");
function ensureDirs() {
  if (!existsSync(CMP_DIR$1)) mkdirSync(CMP_DIR$1, { recursive: true });
  if (!existsSync(TMP_DIR)) mkdirSync(TMP_DIR, { recursive: true });
}
function getBundlesIndex() {
  ensureDirs();
  if (!existsSync(BUNDLES_INDEX)) return [];
  return JSON.parse(readFileSync(BUNDLES_INDEX, "utf-8"));
}
function saveBundlesIndex(index) {
  ensureDirs();
  writeFileSync(BUNDLES_INDEX, JSON.stringify(index, null, 2), "utf-8");
}
function createEmptyManifest(name) {
  const slug = name.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
  return {
    cmp_version: 1,
    mod_info: {
      name,
      slug,
      summary: "",
      project_type: "mod",
      categories: [],
      additional_categories: [],
      license_id: "MIT",
      license_url: "",
      donation_urls: []
    },
    version_info: {
      mod_version: "1.0.0",
      loaders: ["fabric"],
      client_side: "required",
      server_side: "optional",
      minecraft_versions: [],
      version_type: "release",
      changelog: "",
      dependencies: [],
      featured: false
    },
    description: {
      body: "",
      images: []
    },
    icon: "",
    gallery: [],
    files: {
      jar: "",
      source: ""
    },
    links: {
      issues_url: "",
      source_url: "",
      wiki_url: "",
      discord_url: ""
    },
    publishing: {
      modrinth_project_id: "",
      github_owner: "",
      github_repo_name: "",
      requested_status: ""
    }
  };
}
function registerBundleHandlers() {
  ipcMain.handle("bundle:create", async (_event, filePath, name) => {
    ensureDirs();
    const manifest = createEmptyManifest(name);
    const manifestPath = join(filePath, "manifest.json");
    if (!existsSync(filePath)) {
      mkdirSync(filePath, { recursive: true });
      mkdirSync(join(filePath, "jar"), { recursive: true });
      mkdirSync(join(filePath, "source"), { recursive: true });
      mkdirSync(join(filePath, "gallery"), { recursive: true });
      mkdirSync(join(filePath, "description_images"), { recursive: true });
    }
    writeFileSync(manifestPath, JSON.stringify(manifest, null, 2), "utf-8");
    const index = getBundlesIndex();
    const existing = index.findIndex((b) => b.path === filePath);
    const entry = { path: filePath, name, lastModified: (/* @__PURE__ */ new Date()).toISOString() };
    if (existing >= 0) {
      index[existing] = entry;
    } else {
      index.push(entry);
    }
    saveBundlesIndex(index);
    return { path: filePath, manifest };
  });
  ipcMain.handle("bundle:open", async (_event, filePath) => {
    const manifestPath = join(filePath, "manifest.json");
    if (!existsSync(manifestPath)) {
      throw new Error(`No manifest.json found at ${filePath}`);
    }
    const manifest = JSON.parse(readFileSync(manifestPath, "utf-8"));
    const index = getBundlesIndex();
    const existing = index.findIndex((b) => b.path === filePath);
    const entry = { path: filePath, name: manifest.mod_info.name, lastModified: (/* @__PURE__ */ new Date()).toISOString() };
    if (existing >= 0) {
      index[existing] = entry;
    } else {
      index.push(entry);
    }
    saveBundlesIndex(index);
    return { path: filePath, manifest };
  });
  ipcMain.handle("bundle:save", async (_event, filePath, manifest) => {
    ensureDirs();
    const manifestPath = join(filePath, "manifest.json");
    writeFileSync(manifestPath, JSON.stringify(manifest, null, 2), "utf-8");
    const index = getBundlesIndex();
    const existing = index.findIndex((b) => b.path === filePath);
    const entry = { path: filePath, name: manifest.mod_info.name, lastModified: (/* @__PURE__ */ new Date()).toISOString() };
    if (existing >= 0) {
      index[existing] = entry;
    } else {
      index.push(entry);
    }
    saveBundlesIndex(index);
    return { success: true };
  });
  ipcMain.handle("bundle:recent", async () => {
    return getBundlesIndex();
  });
  ipcMain.handle("bundle:delete-from-index", async (_event, filePath) => {
    const index = getBundlesIndex();
    const filtered = index.filter((b) => b.path !== filePath);
    saveBundlesIndex(filtered);
    return { success: true };
  });
  ipcMain.handle("bundle:read-file-as-base64", async (_event, filePath) => {
    if (!existsSync(filePath)) return null;
    const buffer = readFileSync(filePath);
    return buffer.toString("base64");
  });
  ipcMain.handle("bundle:copy-file-to-bundle", async (_event, sourcePath, bundlePath, relativeDest) => {
    const destPath = join(bundlePath, relativeDest);
    const destDir = join(destPath, "..");
    if (!existsSync(destDir)) mkdirSync(destDir, { recursive: true });
    const buffer = readFileSync(sourcePath);
    writeFileSync(destPath, buffer);
    return { success: true, destPath };
  });
  ipcMain.handle("bundle:copy-dir-to-bundle", async (_event, sourcePath, bundlePath, relativeDest) => {
    const destPath = join(bundlePath, relativeDest);
    copyDirRecursive$1(sourcePath, destPath);
    return { success: true, destPath };
  });
  ipcMain.handle("bundle:list-files", async (_event, dirPath) => {
    if (!existsSync(dirPath)) return [];
    return listFilesRecursive(dirPath, dirPath);
  });
  ipcMain.handle("bundle:scan-drafts", async () => {
    console.log("[CMP] Scanning for bundles...");
    console.log("[CMP] MODCOMPILER_ROOT:", MODCOMPILER_ROOT$2);
    console.log("[CMP] BUNDLE_DRAFTS_DIR:", BUNDLE_DRAFTS_DIR$1, "exists:", existsSync(BUNDLE_DRAFTS_DIR$1));
    console.log("[CMP] BUNDLE_PUBLISHED_DIR:", BUNDLE_PUBLISHED_DIR$1, "exists:", existsSync(BUNDLE_PUBLISHED_DIR$1));
    const results = [];
    if (existsSync(BUNDLE_DRAFTS_DIR$1)) {
      const draftEntries = scanBundleDir(BUNDLE_DRAFTS_DIR$1, "draft");
      results.push(...draftEntries);
    }
    if (existsSync(BUNDLE_PUBLISHED_DIR$1)) {
      const publishedEntries = scanBundleDir(BUNDLE_PUBLISHED_DIR$1, "published");
      results.push(...publishedEntries);
    }
    return results;
  });
  ipcMain.handle("bundle:move-to-published", async (_event, bundlePath) => {
    const bundleName = basename(bundlePath);
    const destPath = join(BUNDLE_PUBLISHED_DIR$1, bundleName);
    if (!existsSync(BUNDLE_PUBLISHED_DIR$1)) {
      mkdirSync(BUNDLE_PUBLISHED_DIR$1, { recursive: true });
    }
    if (existsSync(destPath)) {
      throw new Error(`A published bundle already exists at ${destPath}`);
    }
    renameSync(bundlePath, destPath);
    return { success: true, newPath: destPath };
  });
  ipcMain.handle("bundle:write-base64-file", async (_event, base64Data, bundlePath, relativePath) => {
    const destPath = join(bundlePath, relativePath);
    const destDir = join(destPath, "..");
    if (!existsSync(destDir)) mkdirSync(destDir, { recursive: true });
    const buffer = Buffer.from(base64Data, "base64");
    writeFileSync(destPath, buffer);
    return { success: true, destPath };
  });
}
function copyDirRecursive$1(src, dest) {
  if (!existsSync(dest)) mkdirSync(dest, { recursive: true });
  const entries = readdirSync(src, { withFileTypes: true });
  for (const entry of entries) {
    const srcPath = join(src, entry.name);
    const destPath = join(dest, entry.name);
    if (entry.isDirectory()) {
      copyDirRecursive$1(srcPath, destPath);
    } else {
      const buffer = readFileSync(srcPath);
      writeFileSync(destPath, buffer);
    }
  }
}
function listFilesRecursive(dir, base) {
  const results = [];
  if (!existsSync(dir)) return results;
  const entries = readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    const fullPath = join(dir, entry.name);
    const relPath = fullPath.slice(base.length + 1);
    if (entry.isDirectory()) {
      results.push({ path: relPath, name: entry.name, isDir: true });
      results.push(...listFilesRecursive(fullPath, base));
    } else {
      results.push({ path: relPath, name: entry.name, isDir: false });
    }
  }
  return results;
}
function scanBundleDir(baseDir, status) {
  const results = [];
  if (!existsSync(baseDir)) return results;
  const entries = readdirSync(baseDir, { withFileTypes: true });
  for (const entry of entries) {
    if (!entry.isDirectory()) continue;
    const subDir = join(baseDir, entry.name);
    const manifestPath = join(subDir, "manifest.json");
    if (!existsSync(manifestPath)) continue;
    let name = entry.name;
    try {
      const manifest = JSON.parse(readFileSync(manifestPath, "utf-8"));
      if (manifest.mod_info?.name) name = manifest.mod_info.name;
    } catch {
    }
    const stat = statSync(subDir);
    results.push({
      path: subDir,
      name,
      lastModified: stat.mtime.toISOString(),
      status
    });
  }
  return results;
}
const execFileAsync = promisify(execFile);
async function runGh(args, cwd) {
  try {
    const result = await execFileAsync("gh", args, {
      cwd: cwd || process.cwd(),
      env: { ...process.env },
      timeout: 6e4
    });
    return result;
  } catch (error) {
    throw new Error(`gh CLI failed: ${error.stderr || error.message}`);
  }
}
async function runGit(args, cwd) {
  try {
    const result = await execFileAsync("git", args, {
      cwd,
      env: { ...process.env },
      timeout: 6e4
    });
    return result;
  } catch (error) {
    throw new Error(`git failed: ${error.stderr || error.message}`);
  }
}
function registerGitHubHandlers() {
  ipcMain.handle("publish:github-check", async () => {
    try {
      const { stdout } = await runGh(["auth", "status"]);
      const match = stdout.match(/Logged in to github\.com account (\S+)/i) || stdout.match(/as (\S+)/i);
      const owner = match ? match[1] : "";
      return { authenticated: true, owner };
    } catch (error) {
      const stderr = error.stderr || "";
      const match = stderr.match(/Logged in to github\.com account (\S+)/i) || stderr.match(/as (\S+)/i);
      if (match) {
        return { authenticated: true, owner: match[1] };
      }
      return { authenticated: false, error: "gh CLI not authenticated. Run `gh auth login` first.", owner: "" };
    }
  });
  ipcMain.handle("publish:github-create", async (_event, config) => {
    const { owner, repo, sourceDir, modName, modSummary } = config;
    const { stdout } = await runGh([
      "repo",
      "create",
      `${owner}/${repo}`,
      "--public",
      "--description",
      modSummary
    ]);
    const repoUrl = `https://github.com/${owner}/${repo}`;
    const issuesUrl = `${repoUrl}/issues`;
    let wikiUrl = `${repoUrl}/wiki`;
    return { repoUrl, issuesUrl, wikiUrl };
  });
  ipcMain.handle("publish:github-push", async (_event, config) => {
    const { owner, repo, sourceDir, modName, modSummary } = config;
    const tmpDir = join(process.cwd(), "cmp-tmp-gh", randomUUID());
    try {
      await runGit(["clone", `https://github.com/${owner}/${repo}.git`, tmpDir]);
      const srcDest = join(tmpDir, "src");
      if (existsSync(sourceDir)) {
        copyDirRecursive(sourceDir, srcDest);
      }
      const readme = `# ${modName}

${modSummary}

## Installation

1. Download the latest release
2. Place the jar in your mods folder
3. Enjoy!

## Issues

Report issues on the [Issues page](${`https://github.com/${owner}/${repo}/issues`}).
`;
      writeFileSync(join(tmpDir, "README.md"), readme, "utf-8");
      const issueDir = join(tmpDir, ".github", "ISSUE_TEMPLATE");
      mkdirSync(issueDir, { recursive: true });
      writeFileSync(join(issueDir, "bug_report.md"), `---
name: Bug Report
about: Report a bug
labels: bug
---

## Description

## Steps to Reproduce

1. 

## Expected Behavior

## Actual Behavior

## Mod Version

## Minecraft Version
`, "utf-8");
      writeFileSync(join(issueDir, "feature_request.md"), `---
name: Feature Request
about: Suggest a feature
labels: enhancement
---

## Feature Description

## Why

## How
`, "utf-8");
      await runGit(["add", "."], tmpDir);
      await runGit(["commit", "-m", "Initial commit from CMP"], tmpDir);
      await runGit(["push", "origin", "main"], tmpDir);
    } finally {
      if (existsSync(tmpDir)) {
        rmSync(tmpDir, { recursive: true, force: true });
      }
    }
    return { success: true };
  });
  ipcMain.handle("publish:github-wiki", async (_event, config) => {
    const { owner, repo, modName } = config;
    const wikiUrl = `https://github.com/${owner}/${repo}/wiki`;
    const tmpDir = join(process.cwd(), "cmp-tmp-wiki", randomUUID());
    try {
      try {
        await runGit(["clone", `${wikiUrl}.git`, tmpDir]);
      } catch {
        return { wikiUrl, created: false };
      }
      writeFileSync(join(tmpDir, "Home.md"), `# ${modName}

Welcome to the ${modName} wiki.
`, "utf-8");
      writeFileSync(join(tmpDir, "Installation.md"), `# Installation

1. Download the latest release from Modrinth
2. Place the jar in your mods folder
3. Launch Minecraft
`, "utf-8");
      writeFileSync(join(tmpDir, "Troubleshooting.md"), `# Troubleshooting

## Common Issues

If you encounter issues, please report them on the [Issues page](https://github.com/${owner}/${repo}/issues).
`, "utf-8");
      await runGit(["add", "."], tmpDir);
      await runGit(["commit", "-m", "Add wiki pages from CMP"], tmpDir);
      await runGit(["push", "origin", "master"], tmpDir);
    } finally {
      if (existsSync(tmpDir)) {
        rmSync(tmpDir, { recursive: true, force: true });
      }
    }
    return { wikiUrl, created: true };
  });
}
function copyDirRecursive(src, dest) {
  if (!existsSync(dest)) mkdirSync(dest, { recursive: true });
  const entries = require2("fs").readdirSync(src, { withFileTypes: true });
  for (const entry of entries) {
    const srcPath = join(src, entry.name);
    const destPath = join(dest, entry.name);
    if (entry.isDirectory()) {
      copyDirRecursive(srcPath, destPath);
    } else {
      const buffer = readFileSync(srcPath);
      writeFileSync(destPath, buffer);
    }
  }
}
const MODRINTH_API$1 = "https://api.modrinth.com/v2";
async function modrinthRequest(method, path, token, options = {}) {
  const url = `${MODRINTH_API$1}${path}`;
  const headers = {
    "User-Agent": "CMP/1.0 (Center Mod Publishment)",
    Authorization: token,
    ...options.headers
  };
  const fetchOptions = {
    method,
    headers
  };
  if (options.body && !headers["Content-Type"]?.includes("multipart")) {
    headers["Content-Type"] = "application/json";
    fetchOptions.body = JSON.stringify(options.body);
  } else if (options.body) {
    fetchOptions.body = options.body;
  }
  const response = await fetch(url, fetchOptions);
  if (!response.ok) {
    const text2 = await response.text();
    throw new Error(`Modrinth API error (${response.status}): ${text2.slice(0, 300)}`);
  }
  const text = await response.text();
  if (!text) return {};
  return JSON.parse(text);
}
function buildMultipartBody(fields, files) {
  const boundary = `cmp-${Date.now()}-${Math.random().toString(36).slice(2)}`;
  const parts = [];
  for (const [name, value] of Object.entries(fields)) {
    parts.push(Buffer.from(`--${boundary}\r
Content-Disposition: form-data; name="${name}"\r
\r
${value}\r
`));
  }
  for (const file of files) {
    parts.push(Buffer.from(`--${boundary}\r
Content-Disposition: form-data; name="${file.name}"; filename="${file.filename}"\r
Content-Type: ${file.contentType}\r
\r
`));
    parts.push(file.data);
    parts.push(Buffer.from("\r\n"));
  }
  parts.push(Buffer.from(`--${boundary}--\r
`));
  return {
    body: Buffer.concat(parts),
    contentType: `multipart/form-data; boundary=${boundary}`
  };
}
function registerModrinthHandlers() {
  ipcMain.handle("publish:modrinth-create-project", async (_event, config) => {
    const { token, manifest, iconPath } = config;
    const data = {
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
      discord_url: manifest.links.discord_url
    };
    if (manifest.publishing.requested_status) {
      data.requested_status = manifest.publishing.requested_status;
    }
    const fields = {
      data: JSON.stringify(data)
    };
    const files = [];
    if (iconPath && existsSync(iconPath)) {
      const iconData = readFileSync(iconPath);
      const ext = iconPath.split(".").pop() || "png";
      files.push({
        name: "icon",
        filename: `icon.${ext}`,
        data: iconData,
        contentType: ext === "svg" ? "image/svg+xml" : `image/${ext}`
      });
    }
    const { body, contentType } = buildMultipartBody(fields, files);
    const result = await modrinthRequest("POST", "/project", token, {
      body,
      headers: { "Content-Type": contentType }
    });
    return { projectId: result.id, slug: result.slug };
  });
  ipcMain.handle("publish:modrinth-upload-version", async (_event, config) => {
    const { token, projectId, manifest, jarPath } = config;
    if (!existsSync(jarPath)) {
      throw new Error(`Jar file not found: ${jarPath}`);
    }
    const jarData = readFileSync(jarPath);
    const jarName = jarPath.split("/").pop() || "mod.jar";
    const versionTitle = `${manifest.mod_info.name} ${manifest.version_info.mod_version} (${manifest.version_info.loaders.join(", ")} ${manifest.version_info.minecraft_versions.join(", ")})`;
    const fields = {
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
        file_parts: ["file"],
        primary_file: "file"
      })
    };
    const files = [{
      name: "file",
      filename: jarName,
      data: jarData,
      contentType: "application/java-archive"
    }];
    const { body, contentType } = buildMultipartBody(fields, files);
    const result = await modrinthRequest("POST", "/version", token, {
      body,
      headers: { "Content-Type": contentType }
    });
    return { versionId: result.id };
  });
  ipcMain.handle("publish:modrinth-upload-gallery", async (_event, config) => {
    const { token, projectRef, gallery } = config;
    for (const item of gallery) {
      if (!existsSync(item.imagePath)) continue;
      const imageData = readFileSync(item.imagePath);
      const ext = item.imagePath.split(".").pop() || "png";
      const params = new URLSearchParams({
        ext,
        featured: item.featured ? "true" : "false",
        title: item.title,
        description: item.description
      });
      await modrinthRequest("POST", `/project/${encodeURIComponent(projectRef)}/gallery?${params}`, token, {
        body: imageData,
        headers: { "Content-Type": ext === "svg" ? "image/svg+xml" : `image/${ext}` }
      });
    }
    return { success: true };
  });
  ipcMain.handle("publish:modrinth-update-project", async (_event, config) => {
    const { token, projectRef, description, links, manifest } = config;
    const payload = {
      description
    };
    if (links.source_url) payload.source_url = links.source_url;
    if (links.issues_url) payload.issues_url = links.issues_url;
    if (links.wiki_url) payload.wiki_url = links.wiki_url;
    if (links.discord_url) payload.discord_url = links.discord_url;
    if (manifest.mod_info.additional_categories?.length) payload.additional_categories = manifest.mod_info.additional_categories;
    if (manifest.mod_info.license_id) payload.license_id = manifest.mod_info.license_id;
    if (manifest.mod_info.license_url) payload.license_url = manifest.mod_info.license_url;
    if (manifest.mod_info.donation_urls?.length) payload.donation_urls = manifest.mod_info.donation_urls;
    await modrinthRequest("PATCH", `/project/${encodeURIComponent(projectRef)}`, token, {
      body: payload
    });
    return { success: true };
  });
  ipcMain.handle("publish:modrinth-get-project", async (_event, config) => {
    const { token, projectRef } = config;
    return await modrinthRequest("GET", `/project/${encodeURIComponent(projectRef)}`, token);
  });
  ipcMain.handle("publish:modrinth-upload-description-image", async (_event, config) => {
    const { token, projectRef, imagePath } = config;
    if (!existsSync(imagePath)) {
      throw new Error(`Image not found: ${imagePath}`);
    }
    const imageData = readFileSync(imagePath);
    const ext = imagePath.split(".").pop() || "png";
    const params = new URLSearchParams({
      ext,
      featured: "false",
      title: "Description image",
      description: ""
    });
    await modrinthRequest("POST", `/project/${encodeURIComponent(projectRef)}/gallery?${params}`, token, {
      body: imageData,
      headers: { "Content-Type": ext === "svg" ? "image/svg+xml" : `image/${ext}` }
    });
    const project = await modrinthRequest("GET", `/project/${encodeURIComponent(projectRef)}`, token);
    const gallery = project.gallery || [];
    const lastImage = gallery[gallery.length - 1];
    return { url: lastImage?.url || "" };
  });
}
const MODRINTH_API = "https://api.modrinth.com/v2";
const cache = {
  categories: null,
  loaders: null,
  gameVersions: null,
  licenses: null,
  donationPlatforms: null
};
async function fetchFromModrinth(path) {
  const response = await fetch(`${MODRINTH_API}${path}`, {
    headers: { "User-Agent": "CMP/1.0 (Center Mod Publishment)" }
  });
  if (!response.ok) throw new Error(`Modrinth API error: ${response.status}`);
  return response.json();
}
function registerModrinthTagHandlers() {
  ipcMain.handle("modrinth:categories", async () => {
    if (!cache.categories) {
      cache.categories = await fetchFromModrinth("/tag/category");
    }
    return cache.categories;
  });
  ipcMain.handle("modrinth:loaders", async () => {
    if (!cache.loaders) {
      cache.loaders = await fetchFromModrinth("/tag/loader");
    }
    return cache.loaders;
  });
  ipcMain.handle("modrinth:game-versions", async () => {
    if (!cache.gameVersions) {
      const all = await fetchFromModrinth("/tag/game_version");
      cache.gameVersions = all;
    }
    return cache.gameVersions;
  });
  ipcMain.handle("modrinth:licenses", async () => {
    if (!cache.licenses) {
      cache.licenses = await fetchFromModrinth("/tag/license");
    }
    return cache.licenses;
  });
  ipcMain.handle("modrinth:donation-platforms", async () => {
    if (!cache.donationPlatforms) {
      cache.donationPlatforms = await fetchFromModrinth("/tag/donation_platform");
    }
    return cache.donationPlatforms;
  });
}
function resolveModcompilerRoot$1() {
  let dir = __dirname;
  let bestMatch = "";
  for (let i = 0; i < 10; i++) {
    if (existsSync(join(dir, "CMP", "BundleDrafts"))) {
      if (basename(dir) === "ModCompiler") return dir;
      if (!bestMatch) bestMatch = dir;
    }
    const parent = join(dir, "..");
    if (parent === dir) break;
    dir = parent;
  }
  return bestMatch || process.cwd();
}
const MODCOMPILER_ROOT$1 = resolveModcompilerRoot$1();
const CMP_DIR = join(MODCOMPILER_ROOT$1, "CMP");
const BUNDLE_DRAFTS_DIR = join(CMP_DIR, "BundleDrafts");
const BUNDLE_PUBLISHED_DIR = join(CMP_DIR, "BundlePublished");
const MOD_DEV_DIR$1 = join(MODCOMPILER_ROOT$1, "Mod Development");
const TO_BE_UPLOADED_DIR = join(MODCOMPILER_ROOT$1, "ToBeUploaded");
function ensureDir(dir) {
  if (!existsSync(dir)) mkdirSync(dir, { recursive: true });
  return dir;
}
function registerDialogHandlers() {
  ipcMain.handle("app:default-paths", () => ({
    modcompilerRoot: MODCOMPILER_ROOT$1,
    cmpDir: ensureDir(CMP_DIR),
    bundleDraftsDir: ensureDir(BUNDLE_DRAFTS_DIR),
    bundlePublishedDir: ensureDir(BUNDLE_PUBLISHED_DIR),
    modDevDir: existsSync(MOD_DEV_DIR$1) ? MOD_DEV_DIR$1 : MODCOMPILER_ROOT$1,
    toBeUploadedDir: existsSync(TO_BE_UPLOADED_DIR) ? TO_BE_UPLOADED_DIR : MODCOMPILER_ROOT$1
  }));
  ipcMain.handle("dialog:open-file", async (_event, options) => {
    const window = BrowserWindow.getFocusedWindow();
    const result = await dialog.showOpenDialog(window, {
      title: options?.title || "Select File",
      defaultPath: options?.defaultPath,
      properties: ["openFile"],
      filters: options?.filters || [{ name: "All Files", extensions: ["*"] }]
    });
    if (result.canceled || result.filePaths.length === 0) return null;
    return result.filePaths[0];
  });
  ipcMain.handle("dialog:open-directory", async (_event, options) => {
    const window = BrowserWindow.getFocusedWindow();
    const result = await dialog.showOpenDialog(window, {
      title: options?.title || "Select Directory",
      defaultPath: options?.defaultPath,
      properties: ["openDirectory"]
    });
    if (result.canceled || result.filePaths.length === 0) return null;
    return result.filePaths[0];
  });
  ipcMain.handle("dialog:save-file", async (_event, options) => {
    const window = BrowserWindow.getFocusedWindow();
    const result = await dialog.showSaveDialog(window, {
      title: options?.title || "Save File",
      defaultPath: options?.defaultPath,
      filters: options?.filters || [{ name: "CMP Bundle", extensions: ["cmp"] }]
    });
    if (result.canceled) return null;
    return result.filePath;
  });
}
const PRISM_INSTANCES_DIR = join(homedir(), "Library/Application Support/PrismLauncher/instances");
const JAVA8_HOME = "/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home";
function resolveModcompilerRoot() {
  let dir = __dirname;
  let bestMatch = "";
  for (let i = 0; i < 10; i++) {
    if (existsSync(join(dir, "CMP", "BundleDrafts"))) {
      if (basename(dir) === "ModCompiler") return dir;
      if (!bestMatch) bestMatch = dir;
    }
    const parent = join(dir, "..");
    if (parent === dir) break;
    dir = parent;
  }
  return bestMatch || process.cwd();
}
const MODCOMPILER_ROOT = resolveModcompilerRoot();
const MOD_DEV_DIR = join(MODCOMPILER_ROOT, "Mod Development");
function parseModProperties(filePath) {
  const content = readFileSync(filePath, "utf-8");
  const props = {};
  for (const line of content.split("\n")) {
    const eqIndex = line.indexOf("=");
    if (eqIndex === -1) continue;
    const key = line.slice(0, eqIndex).trim();
    const value = line.slice(eqIndex + 1).trim();
    if (key) props[key] = value;
  }
  return props;
}
function detectWorkspace(sourcePath) {
  if (sourcePath.includes("1.12.2-forge")) {
    return { workspace: "1.12.2-forge", workspacePath: join(MOD_DEV_DIR, "1.12.2-forge") };
  }
  if (sourcePath.includes("1.21.1-fabric")) {
    return { workspace: "1.21.1-fabric", workspacePath: join(MOD_DEV_DIR, "1.21.1-fabric") };
  }
  return null;
}
function registerModDevHandlers() {
  ipcMain.handle("mod:compile", async (_event, { sourcePath, modName }) => {
    try {
      const detected = detectWorkspace(sourcePath);
      if (!detected) {
        return { success: false, jarPath: null, output: `Could not detect workspace from sourcePath: ${sourcePath}` };
      }
      const { workspace, workspacePath } = detected;
      if (!existsSync(workspacePath)) {
        return { success: false, jarPath: null, output: `Workspace directory not found: ${workspacePath}` };
      }
      const buildScript = join(workspacePath, "build_mod.sh");
      if (!existsSync(buildScript)) {
        return { success: false, jarPath: null, output: `Build script not found: ${buildScript}` };
      }
      const javaHome = workspace === "1.12.2-forge" ? JAVA8_HOME : process.env.JAVA_HOME || "";
      const modDirName = modName || basename(sourcePath);
      let output;
      try {
        output = execSync(`./build_mod.sh ${modDirName}`, {
          cwd: workspacePath,
          timeout: 6e5,
          env: { ...process.env, JAVA_HOME: javaHome },
          encoding: "utf-8",
          stdio: ["pipe", "pipe", "pipe"]
        });
      } catch (err) {
        const combined = (err.stdout || "") + (err.stderr || "");
        return { success: false, jarPath: null, output: combined || err.message };
      }
      const outputDir = join(workspacePath, "output");
      if (!existsSync(outputDir)) {
        return { success: false, jarPath: null, output: output + "\nOutput directory not found after build." };
      }
      const jars = readdirSync(outputDir).filter((f) => f.endsWith(".jar"));
      if (jars.length === 0) {
        return { success: false, jarPath: null, output: output + "\nNo JAR found in output directory after build." };
      }
      const jarPath = join(outputDir, jars[jars.length - 1]);
      return { success: true, jarPath, output };
    } catch (err) {
      return { success: false, jarPath: null, output: err.message || String(err) };
    }
  });
  ipcMain.handle("mod:launch-client", async (_event, { jarPath, instanceName }) => {
    try {
      if (!existsSync(jarPath)) {
        return { success: false, message: `JAR not found: ${jarPath}` };
      }
      const instanceDir = join(PRISM_INSTANCES_DIR, instanceName);
      if (!existsSync(instanceDir)) {
        return { success: false, message: `Prism Launcher instance not found: ${instanceDir}` };
      }
      const modsDir = join(instanceDir, "minecraft", "mods");
      if (existsSync(modsDir)) {
        const existingJars = readdirSync(modsDir).filter((f) => f.endsWith(".jar"));
        for (const jar of existingJars) {
          rmSync(join(modsDir, jar));
        }
      } else {
        mkdirSync(modsDir, { recursive: true });
      }
      const jarName = basename(jarPath);
      copyFileSync(jarPath, join(modsDir, jarName));
      try {
        execSync('open -a "Prism Launcher"', { timeout: 1e4 });
      } catch {
        return { success: true, message: `JAR deployed to ${instanceName}, but failed to launch Prism Launcher. Please launch it manually.` };
      }
      return { success: true, message: `Deployed ${jarName} to ${instanceName} and launched Prism Launcher.` };
    } catch (err) {
      return { success: false, message: err.message || String(err) };
    }
  });
  ipcMain.handle("mod:list-instances", async () => {
    try {
      if (!existsSync(PRISM_INSTANCES_DIR)) return [];
      return readdirSync(PRISM_INSTANCES_DIR, { withFileTypes: true }).filter((d) => d.isDirectory()).map((d) => d.name);
    } catch {
      return [];
    }
  });
  ipcMain.handle("mod:detect-workspace", async (_event, { sourcePath }) => {
    const detected = detectWorkspace(sourcePath);
    if (!detected) {
      return { workspace: "unknown", workspacePath: "", modName: basename(sourcePath), modProperties: null };
    }
    const modDirName = basename(sourcePath);
    const propsPath = join(sourcePath, "mod.properties");
    const modProperties = existsSync(propsPath) ? parseModProperties(propsPath) : null;
    return {
      workspace: detected.workspace,
      workspacePath: detected.workspacePath,
      modName: modDirName,
      modProperties
    };
  });
  ipcMain.handle("mod:scan-mods", async (_event, { workspace }) => {
    const modsDir = join(MOD_DEV_DIR, workspace, "mods");
    if (!existsSync(modsDir)) return [];
    const results = [];
    const entries = readdirSync(modsDir, { withFileTypes: true });
    for (const entry of entries) {
      if (!entry.isDirectory()) continue;
      const modPath = join(modsDir, entry.name);
      const propsPath = join(modPath, "mod.properties");
      if (!existsSync(propsPath)) continue;
      const props = parseModProperties(propsPath);
      results.push({
        name: entry.name,
        path: modPath,
        modid: props.modid || "",
        version: props.version || ""
      });
    }
    return results;
  });
  ipcMain.handle("mod:scan-all-mods", async () => {
    const workspaces = [];
    if (existsSync(MOD_DEV_DIR)) {
      const entries = readdirSync(MOD_DEV_DIR, { withFileTypes: true });
      for (const entry of entries) {
        if (entry.isDirectory() && existsSync(join(MOD_DEV_DIR, entry.name, "mods"))) {
          workspaces.push(entry.name);
        }
      }
    }
    const results = [];
    for (const ws of workspaces) {
      const modsDir = join(MOD_DEV_DIR, ws, "mods");
      if (!existsSync(modsDir)) continue;
      const loader = ws.includes("forge") ? "Forge" : ws.includes("fabric") ? "Fabric" : "Unknown";
      const entries = readdirSync(modsDir, { withFileTypes: true });
      for (const entry of entries) {
        if (!entry.isDirectory()) continue;
        const modPath = join(modsDir, entry.name);
        const propsPath = join(modPath, "mod.properties");
        const props = existsSync(propsPath) ? parseModProperties(propsPath) : null;
        results.push({
          name: entry.name,
          path: modPath,
          modid: props?.modid || "",
          version: props?.version || "",
          workspace: ws,
          loader
        });
      }
    }
    return results;
  });
}
let mainWindow = null;
function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 800,
    minWidth: 1024,
    minHeight: 768,
    show: false,
    titleBarStyle: "hiddenInset",
    backgroundColor: "#0F0F23",
    webPreferences: {
      preload: join(__dirname, "../preload/index.cjs"),
      sandbox: false,
      contextIsolation: true,
      nodeIntegration: false
    }
  });
  mainWindow.on("ready-to-show", () => {
    mainWindow?.show();
  });
  mainWindow.webContents.setWindowOpenHandler((details) => {
    shell.openExternal(details.url);
    return { action: "deny" };
  });
  if (is.dev && process.env["ELECTRON_RENDERER_URL"]) {
    mainWindow.loadURL(process.env["ELECTRON_RENDERER_URL"]);
  } else {
    mainWindow.loadFile(join(__dirname, "../renderer/index.html"));
  }
}
app.whenReady().then(() => {
  electronApp.setAppUserModelId("com.modcompiler.cmp");
  app.on("browser-window-created", (_, window) => {
    optimizer.watchWindowShortcuts(window);
  });
  registerBundleHandlers();
  registerGitHubHandlers();
  registerModrinthHandlers();
  registerModrinthTagHandlers();
  registerDialogHandlers();
  registerModDevHandlers();
  createWindow();
  app.on("activate", function() {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});
app.on("window-all-closed", () => {
  if (process.platform !== "darwin") {
    app.quit();
  }
});
