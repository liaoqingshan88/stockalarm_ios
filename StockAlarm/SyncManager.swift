import Foundation

/// 同步协调器（与 Android SyncManager 一致：登录全量同步 / 变更上传 / 定时拉取合并 / 跨端清空 / 墓碑防复活）
final class SyncManager {

    static let shared = SyncManager()

    private let client = SyncClient.shared
    private let store = StockStore.shared
    private var serverDeletedCodes: Set<String> = []
    private let queue = DispatchQueue(label: "stockalarm.sync")

    var onSyncMessage: ((String) -> Void)?
    var onDataChanged: (() -> Void)?
    var onSessionExpired: ((String) -> Void)?
    var onRequireReloginAndRetry: (() -> Void)?

    private var pollTask: Task<Void, Never>?

    private init() {
        client.onUnauthorized = { [weak self] in
            self?.onSessionExpired?("登录已失效，请重新登录")
        }
    }

    private func postMsg(_ m: String) {
        DispatchQueue.main.async { self.onSyncMessage?(m) }
    }

    // MARK: - 登录后全量同步

    /// 本地无数据 → 下载全量；本地有数据 → 上传合并后以服务器结果覆盖
    func syncOnLogin() async throws {
        guard client.isLoggedIn else { return }

        let local = store.getStocks()
        if local.isEmpty {
            let result = try await client.getStocks()
            _ = handleClearStockIfNeeded(result.clearStock)
            let added = applyServerStocks(result.stocks, preserveLocalTs: false)
            let mas = try await client.getMaPrices()
            applyServerMas(mas)
            syncMaAlarmSettings()
            postMsg("已从云端同步 \(result.stocks.filter { !$0.deleted }.count) 支股票")
            if added > 0 { CompareEngine.markSyncNewStock() }
        } else {
            let check = try await client.getStocks()
            refreshServerDeleted(check.stocks)
            if handleClearStockIfNeeded(check.clearStock) {
                _ = applyServerStocks(check.stocks, preserveLocalTs: false)
                let mas = try await client.getMaPrices()
                applyServerMas(mas)
                postMsg("其它端已清空股票，本地已同步清空")
            } else {
                let toUpload = collectLocalStocks()
                let merged = try await client.uploadStocks(toUpload)
                _ = applyServerStocks(merged, preserveLocalTs: true)
                syncMaAlarmSettings()
                postMsg("同步完成：\(merged.filter { !$0.deleted }.count) 支股票")
            }
        }
    }

    // MARK: - 修改后自动上传

    /// 标记本地修改时间（必须在修改股票时调用，否则 LWW 会判定为旧数据）
    func markStockModified(code: String) {
        store.saveLocalTimestamp(code: code, ts: Int64(Date().timeIntervalSince1970 * 1000))
    }

    func notifyStocksChanged() async throws {
        guard client.isLoggedIn else { return }
        do {
            // 上传前先刷新墓碑，防止复活已删除股票
            if let pre = try? await client.getStocks() {
                _ = handleClearStockIfNeeded(pre.clearStock)
                refreshServerDeleted(pre.stocks)
            }
            let toUpload = collectLocalStocks()
            let merged = try await client.uploadStocks(toUpload)
            _ = applyServerStocks(merged, preserveLocalTs: true)
        } catch let e as SyncClient.SyncError where e.message == "登录已失效，请重新登录" {
            // 401：登录态已清除，等待重新登录后 retryPending
            throw e
        }
    }

    func notifyStockDeleted(code: String) async throws {
        guard client.isLoggedIn else { return }
        try await client.deleteStock(code: code)
        store.removeLocalTimestamp(code: code)
        queue.sync { serverDeletedCodes.insert(code) }
    }

    // MARK: - 定时拉取

    func startPolling() {
        guard client.isLoggedIn else { return }
        stopPolling()
        pollTask = Task { [weak self] in
            while let self = self, !Task.isCancelled {
                let interval: TimeInterval = StockTime.isTradeTime(hasHk: self.listHasHk())
                    ? AppConstants.syncPollIntervalTrade
                    : AppConstants.syncPollIntervalIdle
                try? await Task.sleep(nanoseconds: UInt64(interval * 1_000_000_000))
                guard !Task.isCancelled else { break }
                if self.client.isLoggedIn {
                    _ = await self.pullAndMerge()
                } else {
                    break
                }
            }
        }
    }

    func stopPolling() {
        pollTask?.cancel()
        pollTask = nil
    }

    private func listHasHk() -> Bool {
        StockTime.hasHkStock(codes: store.getStocks().map { $0.code })
    }

    @discardableResult
    func pullAndMerge() async -> Bool {
        guard client.isLoggedIn else { return false }
        do {
            let result = try await client.getStocks()
            _ = handleClearStockIfNeeded(result.clearStock)
            let changed = applyServerStocks(result.stocks, preserveLocalTs: true)
            syncMaAlarmSettings()
            if changed > 0 {
                DispatchQueue.main.async { self.onDataChanged?() }
            }
            return true
        } catch {
            postMsg("同步失败：无法从服务器拉取最新数据，请检查网络或重新登录")
            return false
        }
    }

    // MARK: - 均线开关双向同步（LWW）

    func syncMaAlarmSettings() {
        Task {
            let settings = AppSettings.shared
            let localEnabled = settings.maAlarmEnabled
            let localTs = settings.maAlarmEnabledTs

            _ = try? await client.uploadMaAlarmSettings(enabled: localEnabled, ts: localTs)

            if let server = try? await client.getMaAlarmSettings(),
               server.lastModified > localTs {
                DispatchQueue.main.async {
                    settings.setMaAlarmEnabled(server.enabled, ts: server.lastModified)
                    self.onDataChanged?()
                }
            }
        }
    }

    // MARK: - 本地数据收集

    private func collectLocalStocks() -> [SyncClient.SyncStock] {
        var out: [SyncClient.SyncStock] = []
        for s in store.getStocks() {
            if queue.sync(operation: { serverDeletedCodes.contains(s.code) }) {
                continue
            }
            var sync = SyncClient.SyncStock()
            sync.code = s.code
            sync.name = s.name
            sync.priceH = s.priceH
            sync.priceL = s.priceL
            sync.rise = s.rise
            sync.drop = s.drop
            sync.valid = s.valid ? 1 : 0
            let ts = store.localTimestamp(code: s.code)
            sync.lastModified = ts > 0 ? ts : Int64(Date().timeIntervalSince1970 * 1000)
            out.add(sync)
        }
        return out
    }

    /// 应用服务器列表到本地，返回实际变化数（新增+修改+删除）
    @discardableResult
    private func applyServerStocks(_ server: [SyncClient.SyncStock], preserveLocalTs: Bool) -> Int {
        guard !server.isEmpty || !store.getStocks().isEmpty else { return 0 }

        var serverMap: [String: SyncClient.SyncStock] = [:]
        var deletedCodes: Set<String> = []
        for s in server {
            if s.deleted {
                deletedCodes.insert(s.code)
            } else {
                serverMap[s.code] = s
            }
        }
        queue.sync { serverDeletedCodes = deletedCodes }

        var changed = 0

        // 1. 删除服务器没有或已软删除的本地股票
        for s in store.getStocks() {
            if serverMap[s.code] == nil || deletedCodes.contains(s.code) {
                store.delete(code: s.code)
                store.removeLocalTimestamp(code: s.code)
                changed += 1
            }
        }

        // 2. 写入/更新
        var addedNew = false
        for (_, s) in serverMap {
            let exists = store.getStocks().contains { $0.code == s.code }
            let item = StockItem(code: s.code, name: s.name, priceH: s.priceH, priceL: s.priceL, rise: s.rise, drop: s.drop, valid: s.valid == 1)
            if exists {
                store.update(item)
                changed += 1
            } else {
                _ = store.add(item)
                addedNew = true
                changed += 1
            }

            let serverTs = s.lastModified > 0 ? s.lastModified : Int64(Date().timeIntervalSince1970 * 1000)
            let localTs = store.localTimestamp(code: s.code)
            if !(preserveLocalTs && localTs > 0 && localTs > serverTs) {
                store.saveLocalTimestamp(code: s.code, ts: serverTs)
            }
        }

        if addedNew {
            CompareEngine.markSyncNewStock()
        }
        return changed
    }

    private func refreshServerDeleted(_ server: [SyncClient.SyncStock]) {
        queue.sync {
            serverDeletedCodes = Set(server.filter { $0.deleted }.map { $0.code })
        }
    }

    // MARK: - MA 数据应用

    private func applyServerMas(_ mas: [SyncClient.SyncMaPrice]) {
        for m in mas where m.period > 0 {
            let idx = AppConstants.maPeriods.firstIndex(of: m.period)
            guard let i = idx else { continue }
            var cur = store.getMaPrices(code: m.code)
            cur[i] = m.price
            store.saveMaPrices(code: m.code, prices: cur)
        }
    }

    // MARK: - 跨端清空

    private func handleClearStockIfNeeded(_ serverClearStock: Int) -> Bool {
        if serverClearStock > store.clearStockVersion {
            store.clearAll()
            store.clearStockVersion = serverClearStock
            postMsg("其它端已清空股票，本地已同步清空")
            return true
        }
        return false
    }

    func clearStocksAcrossClients() async throws {
        guard client.isLoggedIn else { throw SyncClient.SyncError(message: "未登录") }
        let newVersion = try await client.clearStock()
        store.clearAll()
        store.clearStockVersion = newVersion
        DispatchQueue.main.async { self.onDataChanged?() }
    }

    // MARK: - 报警日志上传

    func uploadLocalAlarmLogs() async {
        guard client.isLoggedIn else { return }
        let logs = store.getAlarmLogs(limit: 100).map { l -> SyncClient.SyncAlarmLog in
            var s = SyncClient.SyncAlarmLog()
            s.id = "ios_\(l.id)"
            s.type = l.type
            s.content = "\(l.name) \(l.message)"
            s.code = l.code
            s.createTime = l.createTime
            return s
        }
        guard !logs.isEmpty else { return }
        try? await client.uploadAlarmLogs(logs)
    }
}