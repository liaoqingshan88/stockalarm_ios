import Foundation

/// 应用设置（与 Android SettingsActivity 键一致）
final class AppSettings: ObservableObject {

    static let shared = AppSettings()

    private let defaults = UserDefaults.standard

    private let keyVibrate = "pref_vibrate"
    private let keyColorfulList = "pref_colorful_list"
    private let keyMaAlarmEnabled = "pref_ma_alarm_enabled"
    private let keyMaAlarmEnabledTs = "pref_ma_alarm_enabled_ts"

    @Published var vibrate: Bool {
        didSet { defaults.set(vibrate, forKey: keyVibrate) }
    }

    @Published var colorfulList: Bool {
        didSet { defaults.set(colorfulList, forKey: keyColorfulList) }
    }

    @Published var maAlarmEnabled: Bool {
        didSet { defaults.set(maAlarmEnabled, forKey: keyMaAlarmEnabled) }
    }

    var maAlarmEnabledTs: Int64 {
        get { Int64(defaults.double(forKey: keyMaAlarmEnabledTs)) }
        set { defaults.set(Double(newValue), forKey: keyMaAlarmEnabledTs) }
    }

    private init() {
        vibrate = defaults.object(forKey: keyVibrate) == nil ? true : defaults.bool(forKey: keyVibrate)
        colorfulList = defaults.object(forKey: keyColorfulList) == nil ? true : defaults.bool(forKey: keyColorfulList)
        maAlarmEnabled = defaults.object(forKey: keyMaAlarmEnabled) == nil ? true : defaults.bool(forKey: keyMaAlarmEnabled)
    }

    func setMaAlarmEnabled(_ enabled: Bool, ts: Int64) {
        maAlarmEnabled = enabled
        maAlarmEnabledTs = ts
    }
}