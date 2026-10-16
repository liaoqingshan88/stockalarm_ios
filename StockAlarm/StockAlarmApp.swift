import SwiftUI

@main
struct StockAlarmApp: App {
    @StateObject private var model = AppModel.shared
    @StateObject private var settings = AppSettings.shared

    var body: some Scene {
        WindowGroup {
            MainView()
                .environmentObject(model)
                .environmentObject(settings)
                .onAppear {
                    AlarmNotifier.requestAuthorization()
                    model.start()
                }
        }
    }
}