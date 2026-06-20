# ModCodeExplorer

A lightweight, high-performance native macOS application for exploring and aggregating Minecraft mod development codebases. Optimized for Apple Silicon (M1/M2/M3) using Swift and SwiftUI.

## Features

### 1. Project Visualization
- **Sidebar Navigation**: Browse mod directories across different Minecraft versions (Forge 1.12.2, Forge 1.20.1, Fabric 1.21.1)
- **Hierarchical Tree View**: Navigate source files within each mod folder
- **Quick Preview Pane**: View code snippets without opening external editors

### 2. Code Aggregation & Export
- **Export to Text**: Concatenate all source code files (.java, .kt, .groovy, etc.) into a single .txt file
- **Smart Filtering**: Excludes build artifacts, binary files, and asset directories (build/, .gradle/, assets/)
- **Clear Formatting**: Output includes file headers/separators indicating source file paths

### 3. Finder Integration
- **Reveal in Finder**: Opens Finder directly to exported files or mod source roots
- **Copy Path**: Easy copying of file paths for sharing
- **Copy Content**: Quick copy of file contents to clipboard

### 4. Performance & UX
- **Lazy Loading**: Directory trees load on-demand for low memory footprint
- **Fast Launch**: Optimized for Apple Silicon with minimal startup time
- **macOS Native UI**: Follows Human Interface Guidelines
- **Search Functionality**: Quickly find specific mods or code files

## Requirements

- macOS 13.0+ (Ventura or later)
- Xcode 15.0+
- Apple Silicon Mac (M1/M2/M3) recommended (Intel also supported)

## Building the Application

### Option 1: Using Xcode

1. Open `ModCodeExplorer.xcodeproj` in Xcode
2. Select your development team in Signing & Capabilities
3. Build and run (⌘R)

### Option 2: Using Command Line

```bash
# Build the application
xcodebuild -project ModCodeExplorer.xcodeproj \
           -scheme ModCodeExplorer \
           -configuration Release \
           -destination 'platform=macOS' \
           clean build

# The built app will be in DerivedData
```

## Usage

1. **Open Project**: Click "Open Project" button or press ⌘O
2. **Select Directory**: Choose your mod development root directory (e.g., the parent of `1.12.2-forge/`, `1.20.1-forge/`)
3. **Browse Mods**: Select a mod from the sidebar to view its file structure
4. **Preview Code**: Click on any file to view its contents in the preview pane
5. **Export**: Click "Export to Text" to generate a consolidated text file
6. **Finder Actions**: Use toolbar buttons to reveal files or copy paths

## Supported File Types

The application recognizes and exports these file types:
- **Source Code**: `.java`, `.kt`, `.kts`, `.groovy`, `.scala`
- **Configuration**: `.json`, `.xml`, `.properties`, `.yml`, `.yaml`, `.toml`, `.cfg`, `.conf`

## Excluded Directories

These directories are automatically excluded from scans and exports:
- `build/`, `.gradle/`, `.idea/`, `.vscode/`
- `assets/`, `resources/`, `generated/`
- `.git/`, `node_modules/`, `bin/`, `out/`

## Architecture

```
ModCodeExplorer/
├── Models/
│   ├── ModProject.swift          # Data models for mods and files
│   └── SourceFileFilter.swift    # File filtering logic
├── Views/
│   ├── ContentView.swift         # Main window layout
│   ├── SidebarView.swift         # Mod list sidebar
│   ├── FileTreeView.swift        # Hierarchical file tree
│   ├── CodePreviewView.swift     # Code preview pane
│   └── ProgressOverlay.swift     # Loading/progress indicator
├── ViewModels/
│   └── AppState.swift            # Application state management
├── Services/
│   ├── FileSystemService.swift   # File I/O and directory traversal
│   └── ExportService.swift       # Export and Finder operations
└── ModCodeExplorerApp.swift      # App entry point
```

## Performance Optimizations

- **Lazy Loading**: Directory children are loaded only when expanded
- **Async Operations**: File scanning runs on background threads
- **Efficient Traversal**: Uses FileManager enumerator for fast recursive scans
- **Minimal Memory Footprint**: No caching of large file contents

## License

MIT License - Feel free to use and modify for your own projects.

## Contributing

Contributions welcome! Please feel free to submit issues or pull requests.
