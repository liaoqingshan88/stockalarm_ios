import Foundation

/// 交易时段判定（与 Android StockTime 一致）
enum StockTime {

    private static func sec(_ h: Int, _ m: Int, _ s: Int) -> Int { h * 3600 + m * 60 + s }

    private static let tAOpen = sec(9, 30, 0)
    private static let tAAmEnd = sec(11, 30, 0)
    private static let tHkAmEnd = sec(12, 0, 0)
    private static let tPmOpen = sec(13, 0, 0)
    private static let tAPmEnd = sec(15, 0, 0)
    private static let tHkPmEnd = sec(16, 0, 0)

    static var now: Date { Date() }

    /// 当前秒数（当日 0 点起）
    static func currentSecond(_ date: Date = Date()) -> Int {
        let cal = Calendar.current
        let c = cal.dateComponents([.hour, .minute, .second], from: date)
        return (c.hour ?? 0) * 3600 + (c.minute ?? 0) * 60 + (c.second ?? 0)
    }

    private static func isWorkday(_ date: Date) -> Bool {
        let wd = Calendar.current.component(.weekday, from: date)
        return wd >= 2 && wd <= 6
    }

    /// 是否处于交易时段；hasHk 为 true 时包含港股延长时段
    static func isTradeTime(hasHk: Bool, date: Date = Date()) -> Bool {
        guard isWorkday(date) else { return false }
        let cur = currentSecond(date)

        // A股核心时段（9:25 缓冲起）
        if (cur >= sec(9, 25, 0) && cur <= tAAmEnd) || (cur >= tPmOpen && cur <= tAPmEnd) {
            return true
        }

        // 港股延长时段：11:30-12:00、15:00-16:00
        if hasHk {
            if (cur > tAAmEnd && cur <= tHkAmEnd) || (cur > tAPmEnd && cur <= tHkPmEnd) {
                return true
            }
        }
        return false
    }

    static func isTradeTime() -> Bool { isTradeTime(hasHk: false) }

    /// 判断某只股票当前是否处于其交易时段
    static func isTradeTimeFor(code: String, date: Date = Date()) -> Bool {
        isHkStock(code) ? (isTradeTime(hasHk: true, date: date) && isHkHour(currentSecond(date))) : isTradeTime(hasHk: false, date: date)
    }

    private static func isHkHour(_ cur: Int) -> Bool {
        (cur >= sec(9, 25, 0) && cur <= tHkAmEnd) || (cur >= tPmOpen && cur <= tHkPmEnd)
    }

    /// 判断股票代码是否为港股
    static func isHkStock(_ code: String?) -> Bool {
        guard let code = code?.trimmingCharacters(in: .whitespaces), !code.isEmpty else { return false }
        let lower = code.lowercased()
        if lower.hasPrefix("hk") { return true }
        if lower.hasSuffix(".hk") { return true }
        let digits = lower.filter { $0.isNumber }
        if digits.isEmpty { return false }
        return digits.count >= 1 && digits.count <= 5
    }

    /// 列表中是否含港股
    static func hasHkStock(codes: [String]) -> Bool {
        codes.contains { isHkStock($0) }
    }

    /// 距下一交易时段开盘的秒数（非交易时段低频调度用）
    static func secondsUntilNextTradeStart(hasHk: Bool) -> TimeInterval {
        if isTradeTime(hasHk: hasHk) { return 0 }
        let cal = Calendar.current
        let now = Date()
        let cur = currentSecond(now)

        if isWorkday(now) {
            if cur < tAOpen {
                return TimeInterval(todayAt(cal, sec: tAOpen).timeIntervalSince(now))
            }
            if cur < tPmOpen && cur >= tAAmEnd {
                return TimeInterval(todayAt(cal, sec: tPmOpen).timeIntervalSince(now))
            }
        }
        // 盘后/周末 → 下一工作日 9:30
        return TimeInterval(nextWorkdayOpen(cal, after: now).timeIntervalSince(now))
    }

    private static func todayAt(_ cal: Calendar, sec s: Int) -> Date {
        var c = cal.dateComponents([.year, .month, .day], from: Date())
        c.hour = s / 3600
        c.minute = (s % 3600) / 60
        c.second = 0
        return cal.date(from: c) ?? Date()
    }

    private static func nextWorkdayOpen(_ cal: Calendar, after date: Date) -> Date {
        var d = cal.date(byAdding: .day, value: 1, to: date) ?? date
        while !isWorkday(d) {
            d = cal.date(byAdding: .day, value: 1, to: d) ?? d
        }
        var c = cal.dateComponents([.year, .month, .day], from: d)
        c.hour = 9
        c.minute = 30
        c.second = 0
        return cal.date(from: c) ?? d
    }

    /// yyyyMMdd
    static func dateStr(_ date: Date = Date()) -> String {
        let fmt = DateFormatter()
        fmt.dateFormat = "yyyyMMdd"
        fmt.locale = Locale(identifier: "en_US_POSIX")
        return fmt.string(from: date)
    }

    /// yyyy-MM-dd HH:mm:ss
    static func dateTimeStr(_ date: Date = Date()) -> String {
        let fmt = DateFormatter()
        fmt.dateFormat = "yyyy-MM-dd HH:mm:ss"
        fmt.locale = Locale(identifier: "en_US_POSIX")
        return fmt.string(from: date)
    }
}