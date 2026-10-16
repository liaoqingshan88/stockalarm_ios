import Foundation
import UserNotifications

/// 报警通知（替代 Android AlarmNotifier）
enum AlarmNotifier {

    static func requestAuthorization() {
        let center = UNUserNotificationCenter.current()
        center.requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
    }

    static func notify(message: String) {
        let content = UNMutableNotificationContent()
        content.title = "声动金来预警"
        content.body = message
        content.sound = .default

        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 0.5, repeats: false)
        let request = UNNotificationRequest(identifier: "alarm_\(UUID().uuidString)", content: content, trigger: trigger)
        UNUserNotificationCenter.current().add(request)

        // 记录报警历史
        let today = StockTime.dateStr()
        let codePart = message.contains("-") ? String(message.split(separator: "-").first ?? "") : ""
        let entry = AlarmLogEntry(
            id: "al_\(UUID().uuidString)",
            code: "",
            name: codePart,
            message: message,
            type: "alarm",
            createTime: StockTime.dateTimeStr()
        )
        StockStore.shared.addAlarmLog(entry)
    }
}