import SwiftUI

struct SidebarView: View {
    @EnvironmentObject var appState: AppState

    var body: some View {
        VStack(spacing: 0) {
            // Header with workspace info
            if let workspace = appState.workspace {
                HStack {
                    Image(systemName: "folder.fill")
                        .foregroundColor(.blue)
                    Text(workspace.name)
                        .font(.headline)
                        .lineLimit(1)
                    Spacer()
                }
                .padding(.horizontal)
                .padding(.vertical, 8)

                Divider()
            }

            // Search bar
            HStack {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(.secondary)

                TextField("Search projects...", text: $appState.searchText)
                    .textFieldStyle(.plain)

                if !appState.searchText.isEmpty {
                    Button(action: { appState.searchText = "" }) {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(.secondary)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(8)
            .background(Color(NSColor.controlBackgroundColor))

            Divider()

            // Project list or empty state
            if appState.isLoading && appState.workspace == nil {
                ProgressView("Scanning...")
                    .padding()
            } else if appState.workspace == nil {
                EmptyStateView(
                    title: "No Workspace Open",
                    subtitle: "Open a directory to scan for projects"
                )
            } else if appState.filteredProjects.isEmpty {
                EmptyStateView(
                    title: "No Projects Found",
                    subtitle: appState.searchText.isEmpty ?
                        "No projects detected in this directory" :
                        "No matches for \"\(appState.searchText)\""
                )
            } else {
                List(appState.filteredProjects, selection: $appState.selectedProject) { project in
                    ProjectRowView(project: project)
                        .tag(project as Project?)
                }
                .listStyle(.sidebar)
            }

            // Stats footer
            Divider()
            HStack {
                Text(appState.workspaceStats)
                    .font(.caption)
                    .foregroundColor(.secondary)

                Spacer()

                if let _ = appState.selectedProject {
                    Text(appState.selectedProjectStats)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
            .padding(8)
        }
        .frame(minWidth: 250)
    }
}

struct ProjectRowView: View {
    let project: Project

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                platformIcon
                Text(project.name)
                    .font(.body)
                    .lineLimit(1)
            }

            if let version = project.version, let platform = project.platform {
                Text("\(version) • \(platform.displayName)")
                    .font(.caption)
                    .foregroundColor(.secondary)
            } else if let platform = project.platform {
                Text(platform.displayName)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .padding(.vertical, 2)
        .contextMenu {
            ContextMenuView(itemURL: project.path, baseURL: project.path)
        }
    }

    private var platformIcon: some View {
        Group {
            switch project.platform {
            case .forge:
                Image(systemName: "hammer")
                    .foregroundColor(.orange)
            case .fabric:
                Image(systemName: "scissors")
                    .foregroundColor(.purple)
            case .vanilla:
                Image(systemName: "cube.box")
                    .foregroundColor(.green)
            case .other, .none:
                Image(systemName: "folder")
                    .foregroundColor(.blue)
            }
        }
        .frame(width: 16)
    }
}

struct EmptyStateView: View {
    let title: String
    let subtitle: String

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "folder.badge.questionmark")
                .font(.system(size: 48))
                .foregroundColor(.secondary)

            Text(title)
                .font(.headline)
                .foregroundColor(.secondary)

            Text(subtitle)
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

#Preview {
    SidebarView()
        .environmentObject(AppState())
}
