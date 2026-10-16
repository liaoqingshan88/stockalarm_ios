import Foundation

/// 行情数据（腾讯 API，~ 分隔字段，与 Android SinaStockInfo 一致）
struct StockQuote {
    var code: String
    var name: String
    var nowPrice: Float
    var yestodayPrice: Float
    var todayPrice: Float
    var highestPrice: Float
    var lowestPrice: Float
    var buy1Price: Float
    var sell1Price: Float
    var tradeCount: Int64
    var tradeMoney: Float
    var date: String
    var time: String

    /// 从一行 `v_sz000002="51~万  科Ａ~000002~4.64~..."` 解析
    static func parse(_ source: String) -> StockQuote? {
        guard let start = source.firstIndex(of: "\""),
              let end = source.lastIndex(of: "\""), end > start else { return nil }

        let target = String(source[source.index(after: start)..<end])
        guard target.contains("~") else { return nil }
        let f = target.components(separatedBy: "~")
        guard f.count >= 30 else { return nil }

        let code = f[2].trimmingCharacters(in: .whitespaces)
        let name = f[1].trimmingCharacters(in: .whitespaces)
        let nowPrice = parseFloat(f[3], 0)
        let yestodayPrice = parseFloat(f[4], nowPrice)
        let todayPrice = parseFloat(f[5], nowPrice)

        let highest = max(nowPrice, yestodayPrice)
        let lowest = min(nowPrice, yestodayPrice)

        var date = ""
        var time = ""
        if f.count > 30, !f[30].isEmpty {
            let dt = f[30]
            if dt.count >= 8 {
                date = String(dt.prefix(8))
                time = dt.count >= 14 ? String(dt.dropFirst(8).prefix(6)) : "000000"
            } else {
                date = dt
                time = "000000"
            }
        }

        return StockQuote(
            code: code,
            name: name,
            nowPrice: nowPrice,
            yestodayPrice: yestodayPrice,
            todayPrice: todayPrice,
            highestPrice: highest,
            lowestPrice: lowest,
            buy1Price: parseFloat(f[9], 0),
            sell1Price: parseFloat(f[19], 0),
            tradeCount: Int64(parseDouble(f[6], 0)),
            tradeMoney: Float(parseDouble(f[6], 0)) * nowPrice,
            date: date,
            time: time
        )
    }

    static func parseFloat(_ s: String, _ def: Float) -> Float {
        let t = s.trimmingCharacters(in: .whitespaces)
        if t.isEmpty { return def }
        return Float(t) ?? def
    }

    static func parseDouble(_ s: String, _ def: Double) -> Double {
        let t = s.trimmingCharacters(in: .whitespaces)
        if t.isEmpty { return def }
        return Double(t) ?? def
    }

    /// 涨跌幅 %（相对昨收）
    var changePercent: Double {
        guard yestodayPrice > 0 else { return 0 }
        return Double((nowPrice - yestodayPrice) / yestodayPrice) * 100
    }
}

// MARK: - 代码归一化（与 Android Compare.normalizeCodeKey / SinaStockClient.getQueryCode 一致）

enum CodeNormalizer {

    /// 统一键：港股 → hk+5位数字；6位数字 → sh/sz/bj+数字
    static func normalizeKey(_ code: String?) -> String? {
        guard var c = code?.trimmingCharacters(in: .whitespaces) else { return nil }
        c = c.replacingOccurrences(of: "．", with: ".")
        if c.isEmpty { return nil }

        var lower = c.lowercased()
        if lower.hasPrefix("r_") { lower.removeFirst(2) }

        var prefix = ""
        var digitsPart = lower

        if lower.hasPrefix("hk") || lower.hasPrefix("sh") || lower.hasPrefix("sz") || lower.hasPrefix("bj") {
            prefix = String(lower.prefix(2))
            digitsPart = String(lower.dropFirst(2))
        } else if lower.contains(".") {
            let parts = lower.components(separatedBy: ".")
            digitsPart = parts.first ?? lower
            if parts.count >= 2, parts.last == "hk" {
                prefix = "hk"
            }
        }

        let digits = digitsPart.filter { $0.isNumber }
        if digits.isEmpty { return nil }

        if prefix == "hk" || digits.count <= 5 {
            var d = digits
            if d.count < 5, let n = Int(d) {
                d = String(format: "%05d", n)
            } else if d.count > 5 {
                d = String(d.suffix(5))
            }
            return "hk" + d
        }

        if prefix.isEmpty {
            if digits.hasPrefix("6") { prefix = "sh" }
            else if digits.hasPrefix("4") || digits.hasPrefix("8") || digits.hasPrefix("920") { prefix = "bj" }
            else { prefix = "sz" }
        }
        return prefix + digits
    }

    /// 行情查询代码；港股加 r_ 前缀取实时价
    static func quoteQueryCode(_ code: String) -> String? {
        guard let base = queryCode(code) else { return nil }
        if base.lowercased().hasPrefix("hk") {
            return "r_" + base
        }
        return base
    }

    /// K线查询代码（sh/sz/hk 前缀，无 r_）
    static func queryCode(_ code: String?) -> String? {
        guard let raw = code?.trimmingCharacters(in: .whitespaces), !raw.isEmpty else { return nil }
        var c = raw.replacingOccurrences(of: "．", with: ".")
        if c.contains(".") {
            let parts = c.components(separatedBy: ".")
            if parts.count >= 2 {
                let left = parts[0]
                let right = parts[parts.count - 1]
                if right.lowercased() == "hk" {
                    c = "hk" + left
                } else {
                    c = left
                }
            }
        }

        let lower = c.lowercased()
        if lower.hasPrefix("hk") {
            var digits = String(lower.dropFirst(2)).filter { $0.isNumber }
            if digits.isEmpty { return nil }
            if digits.count < 5, let n = Int(digits) {
                digits = String(format: "%05d", n)
            } else if digits.count > 5 {
                digits = String(digits.suffix(5))
            }
            return "hk" + digits
        }

        let digits = lower.filter { $0.isNumber }
        if digits.count == 6 {
            if digits.hasPrefix("6") { return "sh" + digits }
            if digits.hasPrefix("4") || digits.hasPrefix("8") || digits.hasPrefix("920") { return "bj" + digits }
            return "sz" + digits
        }
        if digits.count >= 1 && digits.count <= 5 {
            let padded = digits.count == 5 ? digits : String(format: "%05d", Int(digits) ?? 0)
            return "hk" + padded
        }

        return lower
    }
}