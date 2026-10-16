import Foundation
import SwiftUI

/// 应用主引擎：行情轮询 + 预警检查 + 同步调度（替代 Android StockAlertService/AlarmActivity 轮询）
@MainActor
final class AppModel: ObservableObject {

    static let shared = AppModel()

    @Published var stocks: [StockItem] = []
    @Published var quotes: [String: StockQuote] = [:]
    @Published var alarmMessages: [String] = []
    @Published var isHttpTimeout = false
    @Published var scrollToCode: String?
    @Published var lastSyncMessage = ""

    private var pollTimer: Timer?
    private var isChecking = false
    private var syncStarted = false

    private init() {
        PeakValleyTracker.shared.setRecorder { code, name, side, now, ref, _ in
            let entry = AlarmLogEntry(
                id: "shl_\(UUID().uuidString)",
                code: code,
                name: name,
                message: side == "high"
                    ? String(format: "上冲前高未破(高%.2f现%.2f)", ref, now)
                    : String(format: "下探前低未破(低%.2f现%.2f)", ref, now),
                type: side,
                createTime: StockTime.dateTimeStr()
            )
            StockStore.shared.addAlarmLog(entry)
        }

        SyncManager.shared.onSyncMessage = { [weak self] msg in
            guard let self = self else { return }
            Task { @MainActor in self.lastSyncMessage = msg }
        }
        SyncManager.shared.onDataChanged = { [weak self] in
            guard let self = self else { return }
            Task { @MainActor in self.reload() }
        }
        SyncManager.shared.onSessionExpired = { [weak self] msg in
            guard let self = self else { return }
            Task { @MainActor in
                self.lastSyncMessage = msg
                self.syncStarted = false
            }
        }
    }

    func start() {
        reload()
        startPolling()
        if SyncClient.shared.isLoggedIn && !syncStarted {
            syncStarted = true
            Task {
                try? await SyncManager.shared.syncOnLogin()
                SyncManager.shared.startPolling()
                await SyncManager.shared.uploadLocalAlarmLogs()
            }
        }
        Task { await checkUpdateOnce() }
    }

    func reload() {
        stocks = StockStore.shared.getStocks()
    }

    // MARK: - 行情轮询

    private func startPolling() {
        pollTimer?.invalidate()
        pollTimer = Timer.scheduledTimer(withTimeInterval: AppConstants.quotePollIntervalTrade, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            Task { @MainActor in self.pollOnce() }
        }
    }

    private func pollOnce() {
        guard !isChecking else { return }
        isChecking = true

        Task {
            defer { isChecking = false }

            let codes = StockStore.shared.getActiveCodes()
            guard !codes.isEmpty else { return }

            // 港股延长时段：只检查处于交易时段的股票
            let inTradeCodes: [String]
            if StockTime.isTradeTime(hasHk: StockTime.hasHkStock(codes: codes)) {
                inTradeCodes = codes.filter { StockTime.isTradeTimeFor(code: $0) }
            } else {
                // 非交易时段也定时刷新一次（保持最新价显示），但不报警
                inTradeCodes = codes
            }

            let quotes = await QuoteClient.shared.getStockInfo(codes: inTradeCodes)
            if quotes.isEmpty {
                isHttpTimeout = true
                return
            }
            isHttpTimeout = false

            var map: [String: StockQuote] = [:]
            for q in quotes {
                if let key = CodeNormalizer.normalizeKey(q.code) {
                    map[key] = q
                }
            }
            self.quotes = map

            // 预警检查（仅交易时段）
            let hasHk = StockTime.hasHkStock(codes: codes)
            if StockTime.isTradeTime(hasHk: hasHk) && !CompareEngine.shouldHoldBackgroundAlarm() {
                let localStocks = StockStore.shared.getStocks()
                if let alarm = CompareEngine.compareAllStocks(stocks: localStocks, quotes: quotes) {
                    alarmMessages.append(alarm.message)
                    if alarmMessages.count > 50 {
                        alarmMessages = Array(alarmMessages.suffix(30))
                    }
                    scrollToCode = alarm.stockCode
                    AlarmNotifier.notify(message: alarm.message)
                }
            }
        }
    }

    // MARK: - 股票操作（登录时自动上传）

    func addStock(_ item: StockItem) -> Bool {
        guard StockStore.shared.add(item) else { return false }
        SyncManager.shared.markStockModified(code: item.code)
        reload()
        Task {
            // 后台下载均线
            let mas = await MaDownloader.shared.downloadAndCalculateMAs(code: item.code)
            if mas.contains(where: { $0 > 0 }) {
                StockStore.shared.saveMaPrices(code: item.code, prices: mas)
            }
            try? await SyncManager.shared.notifyStocksChanged()
        }
        return true
    }

    func updateStock(_ item: StockItem) {
        StockStore.shared.update(item)
        SyncManager.shared.markStockModified(code: item.code)
        reload()
        Task { try? await SyncManager.shared.notifyStocksChanged() }
    }

    func deleteStock(code: String) {
        StockStore.shared.delete(code: code)
        StockStore.shared.deleteMaPrices(code: code)
        reload()
        Task { try? await SyncManager.shared.notifyStockDeleted(code: code) }
    }

    func toggleValid(code: String, valid: Bool) {
        StockStore.shared.setValid(code: code, valid: valid)
        SyncManager.shared.markStockModified(code: code)
        reload()
        Task { try? await SyncManager.shared.notifyStocksChanged() }
    }

    func clearAllStocks() {
        if SyncClient.shared.isLoggedIn {
            Task { try? await SyncManager.shared.clearStocksAcrossClients() }
        } else {
            StockStore.shared.clearAll()
        }
        quotes = [:]
        reload()
    }

    // MARK: - 登录/登出后调用

    func onLoggedIn() {
        guard !syncStarted else { return }
        syncStarted = true
        Task {
            try? await SyncManager.shared.syncOnLogin()
            SyncManager.shared.startPolling()
        }
    }

    func onLoggedOut() {
        syncStarted = false
        SyncManager.shared.stopPolling()
        SyncClient.shared.logout()
    }

    // MARK: - 版本检查

    private var updateChecked = false

    private func checkUpdateOnce() async {
        guard !updateChecked else { return }
        updateChecked = true
        guard let info = await UpdateChecker.check(),
              info.versionCode > UpdateChecker.localVersionCode else { return }
        let msg = "发现新版本 v\(info.versionName)：\n\(info.updateContent)\n（iOS 版请通过 TestFlight/侧载更新）"
        alarmMessages.append(msg)
    }
}