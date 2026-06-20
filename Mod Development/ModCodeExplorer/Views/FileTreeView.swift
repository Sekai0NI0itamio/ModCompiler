import SwiftUI

struct FileTreeView: View {
    @EnvironmentObject var appState: AppState

    var body: some View {
        VStack(spacing: 0) {
            if let project = appState.selectedProject {
                // Header
                HStack {
                    Image(systemName: "folder.fill")
                        .foregroundColor(.blue)

                    Text(project.name)
                        .font(.headline)

                    Spacer()

                    Text("\(appState.fileTree.count) items")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                .padding(.horizontal)
                .padding(.vertical, 8)

                Divider()

                // File tree
                if appState.isLoading {
                    ProgressView("Loading files...")
                        .padding()
                } else if appState.fileTree.isEmpty {
                    EmptyStateView(
                        title: "No Source Files",
                        subtitle: "This mod doesn't contain any source files"
                    )
                } else {
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: 0) {
                            ForEach(appState.fileTree) { item in
                                FileTreeItemView(
                                    item: item,
                                    selectedItem: appState.selectedItem,
                                    onSelect: { appState.selectItem($0) },
                                    loadChildren: { appState.loadChildren(for: $0) }
                                )
                            }
                        }
                    }
                }
            } else {
                EmptyStateView(
                    title: "Select a Mod",
                    subtitle: "Choose a mod from the sidebar to view its files"
                )
            }
        }
        .frame(minWidth: 250)
    }
}

struct FileTreeItemView: View {
    let item: CodeItem
    let selectedItem: CodeItem?
    let onSelect: (CodeItem) -> Void
    let loadChildren: (CodeItem) -> [CodeItem]?

    @State private var isExpanded = false
    @State private var children: [CodeItem]? = nil

    var isSelected: Bool {
        selectedItem?.id == item.id
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Item row
            HStack(spacing: 6) {
                // Expand/collapse indicator for directories
                if item.isDirectory {
                    Image(systemName: expansionIcon)
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .frame(width: 16)
                        .onTapGesture {
                            toggleExpansion()
                        }
                } else {
                    Rectangle()
                        .fill(Color.clear)
                        .frame(width: 16)
                }

                // Icon
                Image(systemName: itemIcon)
                    .foregroundColor(iconColor)
                    .frame(width: 16)

                // Name
                Text(item.name)
                    .font(.system(size: 13))
                    .lineLimit(1)

                Spacer()
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(isSelected ? Color.accentColor.opacity(0.2) : Color.clear)
            .contentShape(Rectangle())
            .onTapGesture {
                onSelect(item)
            }

            // Children (if expanded)
            if isExpanded, let children = children {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(children) { child in
                        FileTreeItemView(
                            item: child,
                            selectedItem: selectedItem,
                            onSelect: onSelect,
                            loadChildren: loadChildren
                        )
                        .padding(.leading, 20)
                    }
                }
            }
        }
    }

    private var expansionIcon: String {
        if children == nil {
            return "chevron.right"
        }
        return isExpanded ? "chevron.down" : "chevron.right"
    }

    private var itemIcon: String {
        if item.isDirectory {
            return isExpanded ? "folder.fill" : "folder"
        }

        switch item.fileExtension {
        case "java":
            return "file.text"
        case "kt", "kts":
            return "file.text"
        case "json":
            return "curlybraces"
        case "xml":
            return "anglebrackets"
        default:
            return "doc"
        }
    }

    private var iconColor: Color {
        if item.isDirectory {
            return .blue
        }

        switch item.fileExtension {
        case "java":
            return .orange
        case "kt", "kts":
            return .purple
        case "json":
            return .green
        case "xml":
            return .blue
        default:
            return .secondary
        }
    }

    private func toggleExpansion() {
        if children == nil {
            // Load children on first expand
            children = loadChildren(item) ?? []
        }
        isExpanded.toggle()
    }
}

#Preview {
    FileTreeView()
        .environmentObject(AppState())
}
