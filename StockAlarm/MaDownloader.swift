import Foundation

/// 均线价格下载器（腾讯 K 线 API，与 Android MaDownloader 一致）
final class MaDownloader {

    static let shared = MaDownloader()
    private let session: URLSession

    private init() {
        let cfg = URLSessionConfiguration.ephemeral
        cfg.timeoutIntervalForRequest = 10
        cfg.timeoutIntervalForResource = 30
        session = URLSession(configuration: cfg)
    }

    /// 下载并计算某股票的全部均线，返回 [MA5, MA10, MA20, MA30, MA60, MA120, MA250]（不足为 -1）
    func downloadAndCalculateMAs(code: String) async -> [Float] {
        var result = [Float](repeating: -1, count: AppConstants.maPeriods.count)

        guard let queryCode = CodeNormalizer.queryCode(code) else { return result }

        let urlStr = "https://ifzq.gtimg.cn/appstock/app/kline/kline?param=\(queryCode),day,,,320"
        guard let url = URL(string: urlStr) else { return result }

        var request = URLRequest(url: url)
        request.setValue("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36", forHTTPHeaderField: "User-Agent")
        request.setValue("https://finance.qq.com/", forHTTPHeaderField: "Referer")

        do {
            let (data, resp) = try await session.data(for: request)
            guard let http = resp as? HTTPURLResponse, http.statusCode == 200 else { return result }
            guard let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { return result }

            let closes = parseKlineCloses(obj: obj, queryCode: queryCode)
            guard !closes.isEmpty else { return result }

            let size = closes.count
            for i in 0..<AppConstants.maPeriods.count {
                let period = AppConstants.maPeriods[i]
                if size >= period {
                    let sum = closes[(size - period)...].reduce(0, +)
                    result[i] = sum / Float(period)
                }
            }
        } catch {
            // 网络失败保持 -1
        }
        return result
    }

    /// JSON: {"data":{"sz000002":{"day":[["2025-03-18","7.570","7.530","7.590","7.490","893408"],...]}}}
    /// day 每行：[date, open, close, high, low, volume]，收盘价取 index 2
    private func parseKlineCloses(obj: [String: Any], queryCode: String) -> [Float] {
        guard let data = obj["data"] as? [String: Any] else { return [] }

        var stockData = data[queryCode] as? [String: Any]
        if stockData == nil {
            for (_, v) in data {
                if let sd = v as? [String: Any] {
                    stockData = sd
                    break
                }
            }
        }
        guard let sd = stockData, let dayArray = sd["day"] as? [[Any]] else { return [] }

        var closes: [Float] = []
        for row in dayArray {
            guard row.count >= 3 else { continue }
            if let closeStr = row[2] as? String, let v = Float(closeStr) {
                closes.append(v)
            } else if let v = row[2] as? Double {
                closes.append(Float(v))
            }
        }
        return closes
    }
}