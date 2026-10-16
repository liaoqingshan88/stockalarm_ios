import Foundation

/// 腾讯行情客户端（GBK 编码，与 Android SinaStockClient 一致）
final class QuoteClient {

    static let shared = QuoteClient()
    private let session: URLSession

    private init() {
        let cfg = URLSessionConfiguration.ephemeral
        cfg.timeoutIntervalForRequest = 10
        cfg.timeoutIntervalForResource = 30
        session = URLSession(configuration: cfg)
    }

    /// GBK 解码
    static let gbkEncoding: String.Encoding = {
        let enc = CFStringConvertEncodingToNSStringEncoding(CFStringEncoding(CFStringEncodings.GB_18030_2000.rawValue))
        return String.Encoding(rawValue: enc)
    }()

    /// 批量获取行情。codes 可为任意写法（600519 / sh600519 / hk00700 / 00700.HK）
    func getStockInfo(codes: [String]) async -> [StockQuote] {
        let queryCodes = codes.compactMap { CodeNormalizer.quoteQueryCode($0) }
        guard !queryCodes.isEmpty else { return [] }

        let urlStr = "http://qt.gtimg.cn/q=" + queryCodes.joined(separator: ",")
        guard let url = URL(string: urlStr) else { return [] }

        var request = URLRequest(url: url)
        request.setValue("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36", forHTTPHeaderField: "User-Agent")
        request.setValue("https://finance.qq.com/", forHTTPHeaderField: "Referer")
        request.setValue("*/*", forHTTPHeaderField: "Accept")

        do {
            let (data, resp) = try await session.data(for: request)
            guard let http = resp as? HTTPURLResponse, http.statusCode == 200 else { return [] }
            guard let text = String(data: data, encoding: Self.gbkEncoding) else { return [] }

            var quotes: [StockQuote] = []
            for line in text.components(separatedBy: "\n") {
                let t = line.trimmingCharacters(in: .whitespacesAndNewlines)
                if t.isEmpty { continue }
                if let q = StockQuote.parse(t) {
                    quotes.append(q)
                }
            }
            return quotes
        } catch {
            return []
        }
    }

    /// 单只股票行情（用于添加时校验代码有效性并取名称）
    func getSingleQuote(code: String) async -> StockQuote? {
        let list = await getStockInfo(codes: [code])
        guard let q = list.first else { return nil }
        // 无效代码返回的行情：名称为空或现价为 0 且昨收为 0
        if q.name.isEmpty || (q.nowPrice <= 0 && q.yestodayPrice <= 0) { return nil }
        return q
    }
}