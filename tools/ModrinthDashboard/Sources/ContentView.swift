import SwiftUI

struct ContentView: View {
    @StateObject private var vm = DashboardViewModel()

    var body: some View {
        HSplitView {
            ProjectListView(vm: vm)
                .frame(minWidth: 240, idealWidth: 280, maxWidth: 340)
            DetailSidebar(vm: vm)
                .frame(minWidth: 520)
        }
        .frame(minWidth: 820, minHeight: 580)
        .background(Color(white: 0.08))
        .preferredColorScheme(.dark)
        .sheet(isPresented: $vm.showSettings) {
            SettingsView(vm: vm)
        }
        .task {
            // 1. Load project list (includes full body for aesthetics)
            await vm.loadProjects()
            // 2. Download analytics history for all projects
            //    (incremental if cache exists, full if first launch)
            if !vm.settings.apiToken.isEmpty {
                await vm.downloadAllHistory()
            }
        }
    }
}
