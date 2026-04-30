import SwiftUI

struct ContentView: View {
    @StateObject private var vm = DashboardViewModel()

    var body: some View {
        HSplitView {
            // Left: project list
            ProjectListView(vm: vm)
                .frame(minWidth: 240, idealWidth: 280, maxWidth: 340)

            // Right: detail sidebar
            DetailSidebar(vm: vm)
                .frame(minWidth: 480)
        }
        .frame(minWidth: 780, minHeight: 560)
        .background(Color(white: 0.08))
        .preferredColorScheme(.dark)
        .sheet(isPresented: $vm.showSettings) {
            SettingsView(vm: vm)
        }
        .task {
            await vm.loadProjects()
        }
    }
}
