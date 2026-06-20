# ModCodeExplorer - Architecture Diagram

## Application Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│                         USER INTERACTION                             │
│                                                                      │
│  1. Opens App → 2. Selects Directory → 3. Browses Mods → 4. Exports │
└────────────────────┬────────────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    ModCodeExplorerApp.swift                          │
│                    (App Entry Point)                                 │
│                                                                      │
│  @main                                                               │
│  Creates WindowGroup with AppState environment object                │
└────────────────────┬────────────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      ContentView.swift                               │
│                   (Main Window Layout)                               │
│                                                                      │
│  NavigationSplitView                                                 │
│  ├─ Sidebar (250px): Mod list                                        │
│  ├─ Content (300px): File tree                                       │
│  └─ Detail (flexible): Code preview                                  │
│                                                                      │
│  Toolbar Actions:                                                    │
│  • Open Project (⌘O)                                                │
│  • Export to Text                                                    │
│  • Reveal in Finder                                                  │
│  • Copy Path                                                         │
└────────────────────┬────────────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    AppState.swift (@MainActor)                       │
│                  (Central State Management)                          │
│                                                                      │
│  Published Properties:                                               │
│  • rootDirectory: URL?                                               │
│  • mods: [ModProject]                                                │
│  • selectedMod: ModProject?                                          │
│  • fileTree: [CodeItem]                                              │
│  • selectedItem: CodeItem?                                           │
│  • fileContent: String?                                              │
│  • searchText: String                                                │
│                                                                      │
│  Key Methods:                                                        │
│  • openProjectFolder() → Shows NSOpenPanel                           │
│  • loadProject(at:) → Scans for mods (async)                         │
│  • selectMod(_:) → Loads file tree                                   │
│  • selectItem(_:) → Shows file content                               │
│  • exportSelectedMod() → Generates .txt file                         │
│  • revealSelectedModInFinder() → Opens Finder                        │
└──────┬──────────────────────────────────────┬───────────────────────┘
       │                                      │
       │ Uses                                 │ Uses
       ▼                                      ▼
┌──────────────────────────┐    ┌──────────────────────────────────┐
│  FileSystemService       │    │  ExportService                   │
│  (File I/O Operations)   │    │  (Export & Finder Ops)           │
│                          │    │                                  │
│  • scanForMods(in:)      │    │  • exportMod(mod:) → URL         │
│  • buildFileTree(at:)    │    │  • revealInFinder(_:)            │
│  • loadChildren(for:)    │    │  • copyFilePath(_:)              │
│  • readFileContent(at:)  │    │  • copyFileContents(_:)          │
│  • exportModToText()     │    │                                  │
│  • collectSourceFiles()  │    │                                  │
└──────────┬───────────────┘    └──────────────────────────────────┘
           │
           │ Operates on
           ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         Data Models                                  │
│                                                                      │
│  ┌────────────────────────────────────────────────────────┐         │
│  │ ModProject                                             │         │
│  │ • id: UUID                                             │         │
│  │ • name: String                                         │         │
│  │ • path: URL                                            │         │
│  │ • version: String (e.g., "1.20.1")                     │         │
│  │ • platform: PlatformType (.forge | .fabric)            │         │
│  └────────────────────────────────────────────────────────┘         │
│                                                                      │
│  ┌────────────────────────────────────────────────────────┐         │
│  │ CodeItem (Enum)                                        │         │
│  │                                                        │         │
│  │ case directory(                                        │         │
│  │   name: String,                                        │         │
│  │   url: URL,                                            │         │
│  │   children: [CodeItem]?  ← Lazy loading               │         │
│  │ )                                                      │         │
│  │                                                        │         │
│  │ case file(                                             │         │
│  │   name: String,                                        │         │
│  │   url: URL,                                            │         │
│  │   extension: String                                    │         │
│  │ )                                                      │         │
│  └────────────────────────────────────────────────────────┘         │
│                                                                      │
│  ┌────────────────────────────────────────────────────────┐         │
│  │ SourceFileFilter (Static)                              │         │
│  │                                                        │         │
│  │ supportedExtensions: Set<String>                       │         │
│  │   ["java", "kt", "json", "xml", ...]                   │         │
│  │                                                        │         │
│  │ excludedDirectories: Set<String>                       │         │
│  │   ["build", ".gradle", "assets", ...]                  │         │
│  └────────────────────────────────────────────────────────┘         │
└─────────────────────────────────────────────────────────────────────┘
```

## View Hierarchy

```
ContentView
│
├─ NavigationSplitView
│  │
│  ├─ SidebarView (Left Column)
│  │  ├─ Search Bar (TextField)
│  │  ├─ List of Mods
│  │  │  └─ ModRowView (for each mod)
│  │  │     ├─ Platform Icon (Forge/Fabric)
│  │  │     ├─ Mod Name
│  │  │     └─ Version Info
│  │  └─ Stats Footer
│  │
│  ├─ FileTreeView (Middle Column)
│  │  ├─ Header (Mod name + item count)
│  │  └─ LazyVStack of FileTreeItemView
│  │     └─ FileTreeItemView (recursive)
│  │        ├─ Expand/Collapse Indicator
│  │        ├─ File/Directory Icon
│  │        ├─ Name
│  │        └─ Children (if expanded)
│  │
│  └─ CodePreviewView (Right Column)
│     ├─ Header
│     │  ├─ File Icon
│     │  ├─ File Name
│     │  ├─ Copy Button
│     │  └─ Reveal Button
│     └─ TextEditor (monospace font)
│
└─ Overlay
   └─ ProgressOverlay (when exporting)
```

## Data Flow Example: Opening a Project

```
User clicks "Open Project" (⌘O)
    ↓
ContentView toolbar action triggers
    ↓
AppState.openProjectFolder()
    ↓
NSOpenPanel shows directory picker
    ↓
User selects "/path/to/Mod Development/"
    ↓
AppState.loadProject(at: url)
    ↓
Sets isLoading = true (shows spinner)
    ↓
Task { background thread }
    ↓
FileSystemService.scanForMods(in: url)
    ↓
Iterates through subdirectories:
  • "1.12.2-forge/" → Platform.forge, Version "1.12.2"
  • "1.20.1-forge/" → Platform.forge, Version "1.20.1"
  • "1.21.1-fabric/" → Platform.fabric, Version "1.21.1"
    ↓
For each version dir, scans for mod folders
    ↓
Validates each mod (checks for src/ or build.gradle)
    ↓
Returns [ModProject] array
    ↓
await MainActor.run {
    AppState.mods = discoveredMods
    AppState.isLoading = false
}
    ↓
SidebarView updates (List re-renders)
    ↓
User sees mod list in sidebar
```

## Data Flow Example: Exporting a Mod

```
User clicks "Export to Text" button
    ↓
ContentView toolbar action triggers
    ↓
Task { await AppState.exportSelectedMod() }
    ↓
Shows ProgressOverlay
    ↓
ExportService.exportMod(mod: selectedMod)
    ↓
Creates output directory: ~/Documents/ModExports/
    ↓
FileSystemService.exportModToText(mod:, outputURL:)
    ↓
Collects all source files:
  • Recursively traverses mod/src/main/java/
  • Skips excluded directories (build/, assets/, etc.)
  • Filters by extension (.java, .kt, .json, etc.)
    ↓
Builds concatenated string:
  For each file:
    - Add separator line (80 "=" chars)
    - Add file path header
    - Add separator line
    - Add file content
    - Add blank lines
    ↓
Writes to: ~/Documents/ModExports/ModName_export.txt
    ↓
Returns output file URL
    ↓
ExportService.revealInFinder(outputFile)
    ↓
NSWorkspace.shared.activateFileViewerSelecting([url])
    ↓
Finder window opens, highlighting the exported file
    ↓
ProgressOverlay dismissed
    ↓
User sees exported file in Finder
```

## Lazy Loading Mechanism

```
Initial State (mod selected):
┌────────────────────────────────┐
│ File Tree (collapsed)          │
│                                │
│ > src/                         │  ← children: nil
│ > build.gradle                 │
│ > README.md                    │
└────────────────────────────────┘

User clicks "src/" directory:
┌────────────────────────────────┐
│ File Tree (expanding)          │
│                                │
│ v src/                         │  ← Toggle expansion
│   > main/                      │  ← loadChildren() called
│                                │  ← children now loaded
└────────────────────────────────┘

User clicks "main/":
┌────────────────────────────────┐
│ File Tree (expanded)           │
│                                │
│ v src/                         │
│   v main/                      │  ← loadChildren() called
│     > java/                    │  ← children now loaded
│     > resources/               │
└────────────────────────────────┘

Key Code:
struct FileTreeItemView: View {
    @State private var children: [CodeItem]? = nil  // Starts as nil

    private func toggleExpansion() {
        if children == nil {
            // First expansion - load from disk
            children = loadChildren(item) ?? []
        }
        isExpanded.toggle()
    }
}
```

## Performance Optimization Layers

```
┌─────────────────────────────────────────────────────────┐
│ Layer 1: UI Thread (@MainActor)                         │
│ • Minimal work on main thread                           │
│ • Only state updates, no I/O                            │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│ Layer 2: Async Tasks (Swift Concurrency)                │
│ • File scanning runs on background threads              │
│ • Task { ... } creates async context                    │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│ Layer 3: Lazy Loading                                   │
│ • Directory children not loaded until expanded          │
│ • Reduces initial memory footprint                      │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│ Layer 4: Smart Filtering During Traversal               │
│ • Excluded directories skipped entirely                 │
│ • enumerator.skipDescendants() avoids unnecessary I/O   │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│ Layer 5: Efficient FileManager APIs                     │
│ • contentsOfDirectory() for single-level listing        │
│ • enumerator() for recursive traversal                  │
│ • No manual recursion overhead                          │
└─────────────────────────────────────────────────────────┘
```

## Memory Management

```
Small Project (< 10 mods):
• AppState: ~5 MB
• File trees: ~2-5 MB (lazy loaded)
• File content: ~100 KB (one file at a time)
• Total: ~15-20 MB

Medium Project (10-50 mods):
• AppState: ~10 MB
• File trees: ~10-15 MB
• File content: ~500 KB
• Total: ~25-40 MB

Large Project (> 50 mods):
• AppState: ~15 MB
• File trees: ~20-30 MB
• File content: ~1 MB
• Total: ~40-60 MB

Garbage Collection:
• Swift ARC automatically frees unused objects
• Old file content replaced when new file selected
• Expanded directories stay in memory (acceptable tradeoff)
```

---

**This architecture ensures:**
✓ Fast launch times
✓ Responsive UI
✓ Low memory usage
✓ Scalability to large projects
✓ Clean code organization
