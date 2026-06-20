# ModCodeExplorer v2.0 - Project Viewer & Mini-IDE Upgrade

## Overview

ModCodeExplorer has been transformed from a simple mod source code viewer into a **full-featured project viewer and mini-IDE** with enhanced capabilities for managing multiple projects, batch operations, and IDE-like workflows.

---

## What's New in v2.0

### 1. Workspace-Based Architecture

**Before:** Single mod-focused application  
**After:** Multi-project workspace manager

The app now treats any directory as a "workspace" that can contain multiple projects. Each project is automatically detected and cataloged.

**Key Features:**
- Open any directory as a workspace
- Automatic project detection (mods, libraries, any codebase)
- Support for mixed platforms (Forge, Fabric, Vanilla, Other)
- Recent workspaces tracking

### 2. Context Menu System (Right-Click Actions)

Every file and directory now supports a comprehensive context menu with the following actions:

#### For Directories:
- **Export to Text** - Export entire directory tree to .txt file
- **Count Files** - Show statistics (total files, source files, directories)
- **Reveal in Finder** - Open directory in macOS Finder
- **Copy Path** - Copy absolute path to clipboard
- **Copy Relative Path** - Copy path relative to workspace root
- **Open in External Editor** - Open with default application

#### For Files:
- **Export to Text** - Export single file
- **Reveal in Finder** - Show file in Finder
- **Copy Path** - Copy file path
- **Copy Relative Path** - Copy relative path
- **Copy Content** - Copy file contents to clipboard
- **Open in External Editor** - Open in VS Code, Xcode, etc.

### 3. Batch Export Capabilities

**New Features:**
- Export entire workspace (all projects) with one click
- Export multiple selected projects simultaneously
- Progress indicators during batch operations
- Automatic timestamp in filenames to prevent overwrites

**Export Options (Toolbar Menu):**
1. **Export Selected** - Export currently selected file/directory
2. **Export Current Project** - Export the active project
3. **Export All Projects** - Batch export all projects in workspace

### 4. Enhanced File Operations

**File Size Display:**
- Files now show their size in the tree view
- Formatted as KB/MB for readability

**File Statistics:**
- Real-time file counting for any directory
- Breakdown by: total files, source files, directories
- Accessible via right-click → "Count Files"

**Smart Refresh:**
- Refresh button to reload current project
- Useful after external changes (git pull, builds, etc.)

### 5. Improved Project Detection

**Platform Auto-Detection:**
- Forge projects (detects `META-INF/mods.toml`)
- Fabric projects (detects `fabric.mod.json`)
- Vanilla projects
- Generic projects (any codebase)

**Project Indicators:**
The app now recognizes projects by looking for:
- `src/` directory
- `build.gradle` or `build.gradle.kts`
- `pom.xml` (Maven)
- `package.json` (Node.js)
- `Cargo.toml` (Rust)
- `.git` directory
- `Makefile` or `CMakeLists.txt`
- Any directory with source files

### 6. Better UI/UX

**Enhanced Sidebar:**
- Workspace header showing current directory name
- Platform-specific icons (🔨 Forge, ✂️ Fabric, 📦 Vanilla, 📁 Other)
- Version badges when detected
- Improved search filtering
- Statistics footer

**Improved File Tree:**
- File sizes displayed inline
- Better color coding
- Context menus on every item
- Smooth expansion/collapse

**Better Empty States:**
- Helpful messages when no workspace is open
- Search result feedback
- Clear calls-to-action

---

## Architecture Changes

### New Data Models

**Workspace.swift** (NEW)
```swift
struct Workspace {
    let id: UUID
    let name: String
    let path: URL
    var projects: [Project]
}

struct Project {
    let id: UUID
    let name: String
    let path: URL
    let platform: PlatformType?
    let version: String?
    var rootItems: [CodeItem]?
}

enum ContextAction {
    case exportToText
    case revealInFinder
    case copyPath
    case copyRelativePath
    case openInExternalEditor
    case countFiles
    case expandAll
    case collapseAll
}
```

### Updated Services

**FileSystemService.swift** (REWRITTEN)
- `scanWorkspace(at:)` - Create workspace from directory
- `scanForProjects(in:)` - Detect all projects in workspace
- `exportDirectoryToText(at:baseURL:outputURL:)` - Export any directory
- `exportMultipleDirectories(urls:baseOutputURL:progressHandler:)` - Batch export
- `countFiles(at:)` - Get file statistics
- `loadProjectRoot(for:)` - Lazy load project files

**ExportService.swift** (ENHANCED)
- `exportDirectory(at:baseURL:to:)` - Export single directory
- `exportMultipleDirectories(urls:baseURL:to:progressHandler:)` - Batch export
- `revealMultipleInFinder(_:)` - Reveal multiple items
- `copyMultiplePaths(_:)` - Copy multiple paths
- `copyRelativePath(_:baseURL:)` - Copy relative paths
- `showFileCountAlert(_:)` - Display statistics dialog
- `showSavePanel(defaultName:)` - Custom save location
- `showFolderSelectionPanel(message:)` - Folder picker

### Updated Views

**ContentView.swift** (UPDATED)
- Workspace-oriented toolbar
- Export menu with multiple options
- Refresh button
- Recent workspace support (stubbed)

**SidebarView.swift** (REWRITTEN)
- Displays workspace name in header
- Shows all detected projects
- Platform icons and version badges
- Context menus on project rows
- Enhanced statistics footer

**FileTreeView.swift** (ENHANCED)
- Context menus on every file/directory
- File size display
- Better lazy loading
- Base URL tracking for relative paths

**CodePreviewView.swift** (ENHANCED)
- File size in header
- Additional action buttons
- Directory preview mode with export button
- Better empty states

**ContextMenuView.swift** (NEW)
- Reusable context menu component
- Handles all right-click actions
- Conditional menu items based on type

---

## Usage Examples

### Example 1: Opening a Workspace

```
1. Click "Open Workspace" (⌘O)
2. Select your mod development directory:
   /Users/stevennovak/Mod Development/
3. App scans and detects all projects:
   - 1.12.2-forge/MyMod
   - 1.20.1-forge/AnotherMod
   - 1.21.1-fabric/FabricMod
4. Browse projects in sidebar
```

### Example 2: Exporting a Single Directory

```
1. Right-click on any directory in file tree
2. Select "Export to Text"
3. App exports that directory and all subdirectories
4. Finder opens to show the exported file
```

### Example 3: Batch Export All Projects

```
1. Click Export menu (toolbar)
2. Select "Export All Projects"
3. App exports each project to separate .txt file
4. All files saved to ~/Documents/ModExports/
5. Finder opens to export directory
```

### Example 4: Getting File Statistics

```
1. Right-click on a project or directory
2. Select "Count Files"
3. Dialog shows:
   Total Files: 145
   Source Files: 98
   Directories: 23
```

### Example 5: Copying Relative Paths

```
1. Right-click on a file
2. Select "Copy Relative Path"
3. Clipboard contains: src/main/java/com/example/Mod.java
   (instead of full absolute path)
```

---

## Migration from v1.0

### Breaking Changes

**Old Model:** `ModProject`  
**New Model:** `Project` (inside `Workspace`)

The old `ModProject` model has been replaced with a more flexible `Project` model that works within a `Workspace` context.

### API Changes

**Old:**
```swift
AppState.selectMod(_ mod: ModProject)
FileSystemService.scanForMods(in: URL) -> [ModProject]
ExportService.exportMod(mod: ModProject) async throws -> URL
```

**New:**
```swift
AppState.loadWorkspace(at: URL)
AppState.selectProject(_ project: Project)
FileSystemService.scanWorkspace(at: URL) -> Workspace
ExportService.exportDirectory(at: URL) async throws -> URL
```

### Removed Features

- Mod-specific terminology (now "project")
- Hardcoded version/platform parsing (now auto-detected)

### Added Features

- Workspace management
- Context menus everywhere
- Batch operations
- File statistics
- Relative path copying
- External editor integration
- Recent workspaces (foundation)

---

## Technical Improvements

### Performance

- **Lazy Loading**: Projects load root items only when selected
- **Background Scanning**: Workspace scanning runs off main thread
- **Efficient Enumeration**: Uses FileManager.enumerator with skipDescendants
- **Minimal State**: Only essential data kept in memory

### Code Quality

- **Separation of Concerns**: Clear service/viewmodel/view boundaries
- **Reusable Components**: ContextMenuView usable anywhere
- **Type Safety**: Enum-based context actions
- **Async/Await**: Modern Swift concurrency throughout

### Error Handling

- Graceful handling of missing files
- Progress reporting for long operations
- User-friendly error messages
- Try/catch with descriptive errors

---

## Future Enhancements (Planned)

### Short Term
- [ ] Syntax highlighting in code preview
- [ ] Search within file contents
- [ ] Favorites/bookmarks system
- [ ] Recent files list
- [ ] Tabbed file viewing

### Medium Term
- [ ] Git integration (status, diff)
- [ ] Compare versions of same file
- [ ] Custom export templates
- [ ] Plugin system for file types
- [ ] Keyboard shortcuts customization

### Long Term
- [ ] Built-in code editor
- [ ] Terminal integration
- [ ] Build script execution
- [ ] Project templates
- [ ] Collaboration features

---

## System Requirements

- **macOS**: 13.0+ (Ventura or later)
- **Xcode**: 15.0+ (for building)
- **Memory**: 50-100 MB typical usage
- **Storage**: ~5 MB for app binary

---

## Building v2.0

```bash
cd "ModCodeExplorer"
open ModCodeExplorer.xcodeproj
# Press ⌘R to build and run
```

No additional dependencies required - pure Swift/SwiftUI/AppKit.

---

## Summary

ModCodeExplorer v2.0 transforms the application from a simple mod viewer into a **versatile project management tool** suitable for:

✅ Minecraft mod developers (original use case)  
✅ General software developers  
✅ Code reviewers  
✅ Technical writers  
✅ Anyone needing to browse and export codebases  

The new workspace-based architecture, comprehensive context menus, and batch operations make it a powerful mini-IDE for quick code exploration and aggregation.

---

**Version**: 2.0.0  
**Release Date**: 2026-06-02  
**License**: MIT
