import Foundation

// MARK: - 股票预警条目（对应 Android stock_info 表）

struct StockItem: Codable, Identifiable, Equatable {
    var id: String { code }
    var code: String
    var name: String
    var priceH: Float
    var priceL: Float
    var rise: Int
    var drop: Int
    var valid: Bool

    /// 预警比较（与 Android StockMng.Compare 逻辑一致）
    /// 返回位标志，文本写入 compareResult
    mutating func compare(priceCur: Float, priceYesterday: Float, compareResult: inout String) -> Int {
        var result = AppConstants.cmpResultNone
        compareResult = ""

        if priceCur < 0.0000001 {
            return result
        }

        if priceCur > priceH && priceH > 0 {
            result |= AppConstants.cmpResultPriceH
            compareResult += "高于设定值:\(formatNum(priceH))"
        }

        if priceCur < priceL && priceL > 0 {
            result |= AppConstants.cmpResultPriceL
            compareResult += "低于设定值:\(formatNum(priceL))"
        }

        if result != AppConstants.cmpResultNone {
            compareResult = "现价:\(formatNum(priceCur))" + compareResult
        }

        if (priceCur - priceYesterday) * 100 > Float(rise) * priceYesterday && rise > 0 {
            result |= AppConstants.cmpResultRise
            compareResult += "涨幅大于:\(rise)%"
        }

        if (priceYesterday - priceCur) * 100 > Float(drop) * priceYesterday && drop > 0 {
            result |= AppConstants.cmpResultDrop
            compareResult += "跌幅大于:\(drop)%"
        }

        return result
    }

    private func formatNum(_ v: Float) -> String {
        if v == v.rounded() { return String(Int(v)) }
        return String(format: "%.2f", v)
    }
}

// MARK: - 报警日志

struct AlarmLogEntry: Codable, Identifiable {
    var id: String
    var code: String
    var name: String
    var message: String
    var type: String
    var createTime: String
}

// MARK: - 本地存储（UserDefaults + JSON，替代 Android SQLite）

final class StockStore {
    static let shared = StockStore()

    private let defaults = UserDefaults.standard
    private let stocksKey = "stock_info"
    private let maKey = "ma_price"
    private let maAlarmLogKey = "ma_alarm_log"
    private let shlAlarmLogKey = "shl_alarm_log"
    private let alarmLogKey = "alarm_log"
    private let tsPrefix = "sync_ts:"
    private let clearStockKey = "clear_stock"
    private let queue = DispatchQueue(label: "stockalarm.store")

    private func loadStocks() -> [StockItem] {
        guard let data = defaults.data(forKey: stocksKey) else { return [] }
        return (try? JSONDecoder().decode([StockItem].self, from: data)) ?? []
    }

    private func saveStocks(_ list: [StockItem]) {
        if let data = try? JSONEncoder().encode(list) {
            defaults.set(data, forKey: stocksKey)
        }
    }

    func getStocks() -> [StockItem] {
        queue.sync { loadStocks() }
    }

    func getActiveCodes() -> [String] {
        queue.sync { loadStocks().filter { $0.valid }.map { $0.code } }
    }

    func add(_ item: StockItem) -> Bool {
        queue.sync {
            var list = loadStocks()
            if list.contains(where: { $0.code == item.code }) { return false }
            if list.count >= AppConstants.maxStockCount { return false }
            list.append(item)
            saveStocks(list)
            return true
        }
    }

    func update(_ item: StockItem) {
        queue.sync {
            var list = loadStocks()
            if let idx = list.firstIndex(where: { $0.code == item.code }) {
                list[idx] = item
                saveStocks(list)
            }
        }
    }

    func delete(code: String) {
        queue.sync {
            saveStocks(loadStocks().filter { $0.code != code })
        }
    }

    func setValid(code: String, valid: Bool) {
        queue.sync {
            var list = loadStocks()
            if let idx = list.firstIndex(where: { $0.code == code }) {
                list[idx].valid = valid
                saveStocks(list)
            }
        }
    }

    func clearAll() {
        queue.sync {
            defaults.removeObject(forKey: stocksKey)
            defaults.removeObject(forKey: maKey)
            defaults.removeObject(forKey: maAlarmLogKey)
            defaults.removeObject(forKey: shlAlarmLogKey)
            clearAllTimestamps()
        }
    }

    // MARK: MA 均线价格

    private func loadMa() -> [String: [Float]] {
        guard let data = defaults.data(forKey: maKey),
              let dict = try? JSONDecoder().decode([String: [Float]].self, from: data) else { return [:] }
        return dict
    }

    func getMaPrices(code: String) -> [Float] {
        queue.sync {
            var prices = [Float](repeating: -1, count: AppConstants.maPeriods.count)
            let dict = loadMa()
            if let arr = dict[code] {
                for i in 0..<min(arr.count, prices.count) { prices[i] = arr[i] }
            }
            return prices
        }
    }

    func saveMaPrices(code: String, prices: [Float]) {
        queue.sync {
            var dict = loadMa()
            dict[code] = prices
            if let data = try? JSONEncoder().encode(dict) {
                defaults.set(data, forKey: maKey)
            }
        }
    }

    func deleteMaPrices(code: String) {
        queue.sync {
            var dict = loadMa()
            dict.removeValue(forKey: code)
            if let data = try? JSONEncoder().encode(dict) {
                defaults.set(data, forKey: maKey)
            }
        }
    }

    // MARK: MA 报警当天去重

    func isMaAlarmedToday(code: String, period: Int, type: Int, today: String) -> Bool {
        queue.sync {
            guard let data = defaults.data(forKey: maAlarmLogKey),
                  let logs = try? JSONDecoder().decode([[String]].self, from: data) else { return false }
            return logs.contains { $0.count >= 4 && $0[0] == code && $0[1] == String(period) && $0[2] == String(type) && $0[3] == today }
        }
    }

    func recordMaAlarm(code: String, period: Int, type: Int, today: String) {
        queue.sync {
            var logs: [[String]] = []
            if let data = defaults.data(forKey: maAlarmLogKey),
               let l = try? JSONDecoder().decode([[String]].self, from: data) { logs = l }
            logs.append([code, String(period), String(type), today])
            if logs.count > 2000 { logs = Array(logs.suffix(1000)) }
            if let data = try? JSONEncoder().encode(logs) {
                defaults.set(data, forKey: maAlarmLogKey)
            }
        }
    }

    // MARK: 报警历史

    func getAlarmLogs(limit: Int = 100) -> [AlarmLogEntry] {
        queue.sync {
            guard let data = defaults.data(forKey: alarmLogKey),
                  let logs = try? JSONDecoder().decode([AlarmLogEntry].self, from: data) else { return [] }
            return Array(logs.prefix(limit))
        }
    }

    func addAlarmLog(_ entry: AlarmLogEntry) {
        queue.sync {
            var logs: [AlarmLogEntry] = []
            if let data = defaults.data(forKey: alarmLogKey),
               let l = try? JSONDecoder().decode([AlarmLogEntry].self, from: data) { logs = l }
            logs.insert(entry, at: 0)
            if logs.count > 200 { logs = Array(logs.prefix(100)) }
            if let data = try? JSONEncoder().encode(logs) {
                defaults.set(data, forKey: alarmLogKey)
            }
        }
    }

    // MARK: 同步时间戳

    func localTimestamp(code: String) -> Int64 {
        Int64(defaults.double(forKey: tsPrefix + code)) == 0 && defaults.object(forKey: tsPrefix + code) == nil
            ? 0 : Int64(defaults.double(forKey: tsPrefix + code))
    }

    func saveLocalTimestamp(code: String, ts: Int64) {
        defaults.set(Double(ts), forKey: tsPrefix + code)
    }

    func removeLocalTimestamp(code: String) {
        defaults.removeObject(forKey: tsPrefix + code)
    }

    private func clearAllTimestamps() {
        for key in defaults.dictionaryRepresentation().keys where key.hasPrefix(tsPrefix) {
            defaults.removeObject(forKey: key)
        }
    }

    // MARK: 跨端清空版本号

    var clearStockVersion: Int {
        get { defaults.integer(forKey: clearStockKey) }
        set { defaults.set(newValue, forKey: clearStockKey) }
    }

    var hasAnyStock: Bool { !getStocks().isEmpty }
}