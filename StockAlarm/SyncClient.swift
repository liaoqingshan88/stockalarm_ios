import Foundation

/// 多端同步 HTTP 客户端（与 Android SyncClient 一致：主/备地址自动切换，Bearer token）
final class SyncClient {

    static let shared = SyncClient()

    private let session: URLSession
    private var baseUrl = AppConstants.syncApiPrimary
    private let defaults = UserDefaults.standard

    private let prefPhone = "sync_phone"
    private let prefToken = "sync_token"
    private let prefLoggedIn = "sync_logged_in"

    var onUnauthorized: (() -> Void)?

    private init() {
        let cfg = URLSessionConfiguration.ephemeral
        cfg.timeoutIntervalForRequest = 10
        cfg.timeoutIntervalForResource = 15
        session = URLSession(configuration: cfg)
    }

    // MARK: - 登录态持久化

    var isLoggedIn: Bool { defaults.bool(forKey: prefLoggedIn) }
    var phone: String { defaults.string(forKey: prefPhone) ?? "" }
    var token: String { defaults.string(forKey: prefToken) ?? "" }

    func saveToken(phone: String, token: String) {
        defaults.set(phone, forKey: prefPhone)
        defaults.set(token, forKey: prefToken)
        defaults.set(true, forKey: prefLoggedIn)
    }

    func clearToken() {
        defaults.removeObject(forKey: prefPhone)
        defaults.removeObject(forKey: prefToken)
        defaults.set(false, forKey: prefLoggedIn)
    }

    // MARK: - 异常

    struct SyncError: LocalizedError {
        let message: String
        var errorDescription: String? { message }
    }

    // MARK: - 数据模型

    struct SyncStock: Codable {
        var code: String = ""
        var name: String = ""
        var priceH: Float = 0
        var priceL: Float = 0
        var rise: Int = 0
        var drop: Int = 0
        var valid: Int = 1
        var deleted: Bool = false
        var lastModified: Int64 = 0

        enum CodingKeys: String, CodingKey {
            case code, name
            case priceH = "price_h"
            case priceL = "price_l"
            case rise, drop, valid, deleted
            case lastModified = "last_modified"
        }
    }

    struct SyncStockResult {
        var stocks: [SyncStock]
        var clearStock: Int
    }

    struct SyncMaPrice: Codable {
        var code: String = ""
        var period: Int = 0
        var price: Float = 0
        var lastModified: Int64 = 0

        enum CodingKeys: String, CodingKey {
            case code, period, price
            case lastModified = "last_modified"
        }
    }

    struct MaAlarmSettings {
        var enabled: Bool
        var lastModified: Int64
    }

    struct SyncAlarmLog: Codable {
        var id: String = ""
        var type: String = "alarm"
        var content: String = ""
        var code: String = ""
        var createTime: String = ""

        enum CodingKeys: String, CodingKey {
            case id, type, content, code
            case createTime = "create_time"
        }
    }

    // MARK: - 认证接口

    func register(phone: String) async throws -> String {
        let resp = try await post(path: "/api/sync/register", body: ["phone": phone], token: nil)
        guard resp["success"] as? Bool == true else {
            throw SyncError(message: resp["error"] as? String ?? "注册失败")
        }
        saveToken(phone: phone, token: resp["token"] as? String ?? "")
        return resp["message"] as? String ?? ""
    }

    func login(phone: String, password: String) async throws -> String {
        let resp = try await post(path: "/api/sync/login", body: ["phone": phone, "password": password], token: nil)
        guard resp["success"] as? Bool == true else {
            throw SyncError(message: resp["error"] as? String ?? "登录失败")
        }
        saveToken(phone: phone, token: resp["token"] as? String ?? "")
        return resp["message"] as? String ?? ""
    }

    func logout() { clearToken() }

    func changePassword(oldPassword: String, newPassword: String) async throws -> String {
        let resp = try await post(path: "/api/sync/change-password", body: ["oldPassword": oldPassword, "newPassword": newPassword], token: token)
        guard resp["success"] as? Bool == true else {
            throw SyncError(message: resp["error"] as? String ?? "修改密码失败")
        }
        clearToken()
        return resp["message"] as? String ?? ""
    }

    // MARK: - 股票

    func getStocks() async throws -> SyncStockResult {
        let resp = try await get(path: "/api/sync/stocks", token: token)
        guard resp["success"] as? Bool == true else {
            throw SyncError(message: resp["error"] as? String ?? "获取股票失败")
        }
        var list: [SyncStock] = []
        if let arr = resp["stocks"] as? [[String: Any]] {
            for o in arr { list.append(Self.stockFrom(o)) }
        }
        return SyncStockResult(stocks: list, clearStock: resp["clear_stock"] as? Int ?? 0)
    }

    func clearStock() async throws -> Int {
        let resp = try await post(path: "/api/sync/clear_stock", body: [:], token: token)
        guard resp["success"] as? Bool == true else {
            throw SyncError(message: resp["error"] as? String ?? "清空股票失败")
        }
        return resp["clear_stock"] as? Int ?? 0
    }

    @discardableResult
    func uploadStocks(_ stocks: [SyncStock]) async throws -> [SyncStock] {
        let arr = stocks.map { Self.stockToDict($0) }
        let resp = try await post(path: "/api/sync/stocks", body: ["stocks": arr], token: token)
        guard resp["success"] as? Bool == true else {
            throw SyncError(message: resp["error"] as? String ?? "上传股票失败")
        }
        var list: [SyncStock] = []
        if let result = resp["stocks"] as? [[String: Any]] {
            for o in result { list.append(Self.stockFrom(o)) }
        }
        return list
    }

    func deleteStock(code: String) async throws {
        let resp = try await delete(path: "/api/sync/stocks/" + code, token: token)
        guard resp["success"] as? Bool == true else {
            throw SyncError(message: resp["error"] as? String ?? "删除股票失败")
        }
    }

    // MARK: - MA 数据

    func getMaPrices() async throws -> [SyncMaPrice] {
        let resp = try await get(path: "/api/sync/ma-prices", token: token)
        guard resp["success"] as? Bool == true else {
            throw SyncError(message: resp["error"] as? String ?? "获取MA数据失败")
        }
        var list: [SyncMaPrice] = []
        if let arr = resp["maPrices"] as? [[String: Any]] {
            for o in arr {
                var m = SyncMaPrice()
                m.code = o["code"] as? String ?? ""
                m.period = o["period"] as? Int ?? 0
                m.price = Float(o["price"] as? Double ?? 0)
                m.lastModified = Int64(o["last_modified"] as? Double ?? 0)
                list.append(m)
            }
        }
        return list
    }

    func uploadMaPrices(_ maPrices: [SyncMaPrice]) async throws {
        let arr = maPrices.map { m -> [String: Any] in
            ["code": m.code, "period": m.period, "price": m.price, "last_modified": m.lastModified]
        }
        let resp = try await post(path: "/api/sync/ma-prices", body: ["maPrices": arr], token: token)
        guard resp["success"] as? Bool == true else {
            throw SyncError(message: resp["error"] as? String ?? "上传MA数据失败")
        }
    }

    // MARK: - 设置（均线预警总开关，LWW）

    func getMaAlarmSettings() async throws -> MaAlarmSettings? {
        let resp = try await get(path: "/api/sync/settings", token: token)
        guard resp["success"] as? Bool == true else {
            throw SyncError(message: resp["error"] as? String ?? "获取设置失败")
        }
        guard let s = resp["settings"] as? [String: Any] else { return nil }
        return MaAlarmSettings(
            enabled: s["ma_alarm_enabled"] as? Bool ?? true,
            lastModified: Int64(s["last_modified"] as? Double ?? 0)
        )
    }

    func uploadMaAlarmSettings(enabled: Bool, ts: Int64) async throws -> MaAlarmSettings {
        let resp = try await post(path: "/api/sync/settings", body: ["ma_alarm_enabled": enabled, "last_modified": ts], token: token)
        guard resp["success"] as? Bool == true else {
            throw SyncError(message: resp["error"] as? String ?? "上传设置失败")
        }
        if let s = resp["settings"] as? [String: Any] {
            return MaAlarmSettings(
                enabled: s["ma_alarm_enabled"] as? Bool ?? enabled,
                lastModified: Int64(s["last_modified"] as? Double ?? Double(ts))
            )
        }
        return MaAlarmSettings(enabled: enabled, lastModified: ts)
    }

    // MARK: - 报警日志

    func getAlarmLogs() async throws -> [SyncAlarmLog] {
        let resp = try await get(path: "/api/sync/alarm-logs", token: token)
        guard resp["success"] as? Bool == true else {
            throw SyncError(message: resp["error"] as? String ?? "获取报警日志失败")
        }
        var list: [SyncAlarmLog] = []
        if let arr = resp["alarmLogs"] as? [[String: Any]] {
            for o in arr {
                var l = SyncAlarmLog()
                l.id = o["id"] as? String ?? ""
                l.type = o["type"] as? String ?? "alarm"
                l.content = o["content"] as? String ?? ""
                l.code = o["code"] as? String ?? ""
                l.createTime = o["create_time"] as? String ?? ""
                list.append(l)
            }
        }
        return list
    }

    func uploadAlarmLogs(_ logs: [SyncAlarmLog]) async throws {
        let arr = logs.map { l -> [String: Any] in
            ["id": l.id, "type": l.type, "content": l.content, "code": l.code, "create_time": l.createTime]
        }
        let resp = try await post(path: "/api/sync/alarm-logs", body: ["alarmLogs": arr], token: token)
        guard resp["success"] as? Bool == true else {
            throw SyncError(message: resp["error"] as? String ?? "上传报警日志失败")
        }
    }

    // MARK: - JSON 转换

    private static func stockFrom(_ o: [String: Any]) -> SyncStock {
        var s = SyncStock()
        s.code = o["code"] as? String ?? ""
        s.name = o["name"] as? String ?? ""
        s.priceH = Float(o["price_h"] as? Double ?? 0)
        s.priceL = Float(o["price_l"] as? Double ?? 0)
        s.rise = o["rise"] as? Int ?? 0
        s.drop = o["drop"] as? Int ?? 0
        s.valid = o["valid"] as? Int ?? 1
        s.deleted = o["deleted"] as? Bool ?? false
        s.lastModified = Int64(o["last_modified"] as? Double ?? 0)
        return s
    }

    private static func stockToDict(_ s: SyncStock) -> [String: Any] {
        [
            "code": s.code,
            "name": s.name,
            "price_h": s.priceH,
            "price_l": s.priceL,
            "rise": s.rise,
            "drop": s.drop,
            "valid": s.valid,
            "deleted": s.deleted,
            "last_modified": s.lastModified,
        ]
    }

    // MARK: - 底层 HTTP（主/备切换）

    private func get(path: String, token: String?) async throws -> [String: Any] {
        try await request("GET", path: path, body: nil, token: token)
    }

    private func post(path: String, body: [String: Any], token: String?) async throws -> [String: Any] {
        try await request("POST", path: path, body: body, token: token)
    }

    private func delete(path: String, token: String?) async throws -> [String: Any] {
        try await request("DELETE", path: path, body: nil, token: token)
    }

    private func request(_ method: String, path: String, body: [String: Any]?, token: String?) async throws -> [String: Any] {
        var candidates: [String]
        if baseUrl == AppConstants.syncApiPrimary {
            candidates = [AppConstants.syncApiPrimary, AppConstants.syncApiFallback]
        } else {
            candidates = [baseUrl, AppConstants.syncApiPrimary, AppConstants.syncApiFallback]
        }

        var lastError: Error = SyncError(message: "网络请求失败")
        for base in candidates {
            do {
                let r = try await doRequest(method, urlStr: base + path, body: body, token: token)
                baseUrl = base
                return r
            } catch let e as SyncError where e.message == "__UNAUTHORIZED__" {
                clearToken()
                onUnauthorized?()
                throw SyncError(message: "登录已失效，请重新登录")
            } catch {
                lastError = error
            }
        }
        throw lastError
    }

    private func doRequest(_ method: String, urlStr: String, body: [String: Any]?, token: String?) async throws -> [String: Any] {
        guard let url = URL(string: urlStr) else { throw SyncError(message: "URL 无效") }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("close", forHTTPHeaderField: "Connection")

        if let token = token, !token.isEmpty {
            request.setValue("Bearer " + token, forHTTPHeaderField: "Authorization")
        }

        if let body = body, !body.isEmpty {
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
            request.setValue("application/json; charset=UTF-8", forHTTPHeaderField: "Content-Type")
        }

        let (data, resp): (Data, URLResponse)
        do {
            (data, resp) = try await session.data(for: request)
        } catch {
            throw SyncError(message: "网络连接失败")
        }

        guard let http = resp as? HTTPURLResponse else {
            throw SyncError(message: "响应无效")
        }

        var respStr = String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""

        guard (200..<300).contains(http.statusCode) else {
            if respStr.hasPrefix("{") {
                if let obj = try? JSONSerialization.jsonObject(with: Data(respStr.utf8)) as? [String: Any],
                   let err = obj["error"] as? String {
                    respStr = err
                }
            }
            if http.statusCode == 401 {
                throw SyncError(message: "__UNAUTHORIZED__")
            }
            throw SyncError(message: respStr.isEmpty ? "HTTP \(http.statusCode)" : respStr)
        }

        if respStr.isEmpty { return [:] }
        guard let obj = try? JSONSerialization.jsonObject(with: Data(respStr.utf8)) as? [String: Any] else {
            return [:]
        }
        return obj
    }
}