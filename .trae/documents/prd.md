# PRD — Mod Wizard Web App

## 1. Product Overview

A browser-based web app that replicates and extends the Python mod_wizard CLI tool for creating Minecraft 1.12.2 Forge mods with AI assistance. The app communicates with the same local FreeDeepseek server (localhost:8129) and the same Gradle build workspace, but provides a rich visual interface with session persistence, checkpoint-based resume, real-time streaming, and project file inspection.

- **Target users**: Developers using the ModCompiler toolchain who want a more flexible, visual workflow for creating mods
- **Key value**: Visual step tracking, checkpoint resume to any step, live AI streaming, file inspection, and session history — all impossible in a terminal

## 2. Core Features

### 2.1 Feature Modules

1. **Dashboard**: Session list, new/resume actions, server status
2. **Wizard Flow**: Step-by-step mod creation with AI (name → describe → AI generate → diagnose → compile → refine)
3. **Session Manager**: Save/load/resume sessions with checkpoint granularity
4. **File Inspector**: Browse and view extracted mod source files from AI responses
5. **Build Monitor**: Real-time build log streaming, error highlighting, jar collection
6. **AI Chat View**: Streaming AI responses with summary extraction

### 2.2 Page Details

| Page | Module | Feature Description |
|------|--------|---------------------|
| Dashboard | Server Status | Live indicator showing FreeDeepseek server connectivity |
| Dashboard | Session List | Table of all saved sessions with mod name, step, last updated; resume/delete actions |
| Dashboard | New Session | Button to start a new mod creation session |
| Wizard | Step Tracker | Visual progress bar showing current step (1-7) with completed/active/pending states |
| Wizard | Step 1 - Name | Input field for mod name with validation |
| Wizard | Step 2 - Description | Textarea for mod description with paste support |
| Wizard | Step 3 - AI Generate | Send prompt to AI, show streaming response in real-time, extract chat URL |
| Wizard | Step 5 - Diagnose | Show diagnosis issues, offer fix prompt, display AI summary |
| Wizard | Step 6 - Compile | Trigger build, stream build log, show errors with highlighting, offer AI fix |
| Wizard | Step 7 - Refine | Test feedback input, send refinement to AI, recompile loop |
| File Inspector | File Tree | Tree view of all extracted files from AI response |
| File Inspector | Code Viewer | Syntax-highlighted view of individual file contents |
| File Inspector | Metadata | Show extracted metadata (group, archivesBaseName) |
| Session | Checkpoint List | Visual timeline of all checkpoints with step labels |
| Session | Resume to Checkpoint | Click any checkpoint to resume from that step |
| Session | History Replay | Expandable log of all terminal output from the session |

## 3. Core Process

### 3.1 New Mod Creation Flow

```mermaid
flowchart TD
    "A[Dashboard]" --> "B[New Session]"
    "B" --> "C[Step 1: Enter Mod Name]"
    "C" --> "D[Step 2: Enter Description]"
    "D" --> "E[Step 3: AI Generates Code]"
    "E" --> "F{AI Response OK?}"
    "F" --> "|No| G[Diagnosis Fix Prompt]"
    "G" --> "E"
    "F" --> "|Yes| H[Step 5: Diagnose Files]"
    "H" --> "I{Issues Found?}"
    "I" --> "|Yes| J[Send Fix to AI]"
    "J" --> "H"
    "I" --> "|No| K[Step 6: Compile]"
    "K" --> "L{Build Success?}"
    "L" --> "|No| M[AI Fix Loop]"
    "M" --> "K"
    "L" --> "|Yes| N[Step 7: Refinement]"
    "N" --> "O{User Done?}"
    "O" --> "|No| P[Send Refinement]"
    "P" --> "K"
    "O" --> "|Yes| Q[Complete]"
```

### 3.2 Session Resume Flow

```mermaid
flowchart TD
    "A[Dashboard]" --> "B[Select Saved Session]"
    "B" --> "C[View Checkpoint Timeline]"
    "C" --> "D[Click Checkpoint to Resume]"
    "D" --> "E[Replay History Above]"
    "E" --> "F[Continue from Selected Step]"
```

## 4. User Interface Design

### 4.1 Design Style

- **Primary colors**: Black (#000000) background, white (#FFFFFF) text
- **Accent color**: Emerald green (#10B981) for success/active states, Red (#EF4444) for errors
- **Secondary accent**: Zinc gray (#3F3F46) for borders and muted elements
- **Button style**: Minimal, border-only with white text, filled on hover
- **Font**: JetBrains Mono for code, system sans-serif for UI
- **Layout style**: Single-page app with sidebar navigation, main content area
- **Icon style**: Lucide icons (minimal line icons)

### 4.2 Page Design Overview

| Page | Module | UI Elements |
|------|--------|-------------|
| Dashboard | Server Status | Green/red dot indicator, "Connected" / "Disconnected" label |
| Dashboard | Session List | Table rows with mod name, step badge, timestamp, action buttons |
| Wizard | Step Tracker | Horizontal stepper with numbered circles, connecting lines, active highlight |
| Wizard | AI Response | Streaming text area with typing animation, summary card below |
| Wizard | Build Log | Monospace scrolling area, red-highlighted error lines |
| File Inspector | File Tree | Collapsible tree with folder/file icons |
| File Inspector | Code Viewer | Full-width syntax-highlighted code block with line numbers |
| Session | Checkpoint Timeline | Vertical timeline with step labels, clickable nodes |

### 4.3 Responsiveness

- Desktop-first design (primary use case is development workstation)
- Minimum viewport: 1024px wide
- Sidebar collapses to hamburger menu below 768px
- Code viewer uses full available width

### 4.4 Layout Structure

```
┌─────────────────────────────────────────────────────────┐
│  Header: Mod Wizard          [Server: ● Connected]      │
├──────────┬──────────────────────────────────────────────┤
│          │                                              │
│ Sidebar  │  Main Content Area                          │
│          │                                              │
│ ○ Home   │  ┌─ Step Tracker ──────────────────────┐   │
│ ○ Wizard │  │ 1 → 2 → 3 → 5 → 6 → 7              │   │
│ ○ Files  │  └──────────────────────────────────────┘   │
│ ○ Build  │                                              │
│          │  ┌─ Active Step Panel ──────────────────┐   │
│          │  │                                      │   │
│          │  │  (step-specific content)             │   │
│          │  │                                      │   │
│          │  └──────────────────────────────────────┘   │
│          │                                              │
│          │  ┌─ AI Summary / Build Log ─────────────┐   │
│          │  │                                      │   │
│          │  └──────────────────────────────────────┘   │
│          │                                              │
├──────────┴──────────────────────────────────────────────┤
│  Footer: Session: auto_fish | Step: Compile | 3 files  │
└─────────────────────────────────────────────────────────┘
```
