import Foundation

/// 预警比较引擎（与 Android Compare.compareAllStocks 一致）
enum CompareEngine {

    /// 同步后报警节流门：同步新增股票后 10 秒内不触发报警，让 UI 先刷新价格
    private static var lastSyncNewStockMs: Int64 = 0
    private static let syncAlarmGateMs: Int64 = 10_000

    static func markSyncNewStock() {
        lastSyncNewStockMs = Int64(Date().timeIntervalSince1970 * 1000)
    }

    static func shouldHoldBackgroundAlarm() -> Bool {
        guard lastSyncNewStockMs > 0 else { return false }
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        return now - lastSyncNewStockMs < syncAlarmGateMs
    }

    struct AlarmResult {
        var message: String
        var stockIndex: Int
        var stockCode: String
    }

    /// 对全部股票执行价格/涨幅/MA/次高低点预警检查，返回首个命中的报警
    static func compareAllStocks(stocks: [StockItem], quotes: [StockQuote]) -> AlarmResult? {
        PeakValleyTracker.shared.resetForNewDay()

        var infoByCode: [String: StockQuote] = [:]
        for q in quotes where !q.code.isEmpty {
            if let key = CodeNormalizer.normalizeKey(q.code) {
                infoByCode[key] = q
            }
        }

        for (i, stock) in stocks.enumerated() {
            guard stock.valid else { continue }
            guard let quote = infoByCode[CodeNormalizer.normalizeKey(stock.code) ?? ""] else { continue }

            var item = stock
            var cmpText = ""
            let ret = item.compare(priceCur: quote.nowPrice, priceYesterday: quote.yestodayPrice, compareResult: &cmpText)
            if ret != AppConstants.cmpResultNone {
                return AlarmResult(message: "\(quote.name)-\(cmpText)", stockIndex: i, stockCode: stock.code)
            }

            if let maAlarm = checkMaAlarm(code: stock.code, name: quote.name, nowPrice: quote.nowPrice, yestodayPrice: quote.yestodayPrice) {
                return AlarmResult(message: "\(quote.name)-\(maAlarm)", stockIndex: i, stockCode: stock.code)
            }

            if let approachAlarm = PeakValleyTracker.shared.checkApproachFailure(code: stock.code, name: quote.name, nowPrice: quote.nowPrice) {
                return AlarmResult(message: "\(quote.name)-\(approachAlarm)", stockIndex: i, stockCode: stock.code)
            }
        }
        return nil
    }

    /// MA 均线穿越检查（上穿/下穿，当天去重）
    static func checkMaAlarm(code: String, name: String, nowPrice: Float, yestodayPrice: Float) -> String? {
        guard AppSettings.shared.maAlarmEnabled else { return nil }
        guard nowPrice > 0.00001 else { return nil }

        let maPrices = StockStore.shared.getMaPrices(code: code)
        let todayStr = StockTime.dateStr()
        var alarmMsg = ""

        for i in 0..<AppConstants.maPeriods.count {
            let ma = maPrices[i]
            guard ma >= 0 else { continue }
            let period = AppConstants.maPeriods[i]
            let maName = AppConstants.maNames[i]

            let crossUp = yestodayPrice < ma && nowPrice >= ma
            let crossDown = yestodayPrice > ma && nowPrice <= ma

            if crossUp {
                if StockStore.shared.isMaAlarmedToday(code: code, period: period, type: 1, today: todayStr) { continue }
                let diff = Double((nowPrice - ma) / ma) * 100
                if !alarmMsg.isEmpty { alarmMsg += "; " }
                alarmMsg += "上穿\(maName)(\(String(format: "%.2f,%.1f%%", ma, diff)))"
                StockStore.shared.recordMaAlarm(code: code, period: period, type: 1, today: todayStr)
            } else if crossDown {
                if StockStore.shared.isMaAlarmedToday(code: code, period: period, type: 2, today: todayStr) { continue }
                let diff = Double((ma - nowPrice) / ma) * 100
                if !alarmMsg.isEmpty { alarmMsg += "; " }
                alarmMsg += "下穿\(maName)(\(String(format: "%.2f,%.1f%%", ma, diff)))"
                StockStore.shared.recordMaAlarm(code: code, period: period, type: 2, today: todayStr)
            }
        }
        return alarmMsg.isEmpty ? nil : alarmMsg
    }
}