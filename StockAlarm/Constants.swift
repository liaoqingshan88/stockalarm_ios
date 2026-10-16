import Foundation
import SwiftUI

enum AppConstants {
    static let riseColorHex = 0xD0021B
    static let dropColorHex = 0x1AA260
    static let highlightColorHex = 0xFFF4CC

    static var riseColor: Color { Color(hex: riseColorHex) }
    static var dropColor: Color { Color(hex: dropColorHex) }
    static var highlightColor: Color { Color(hex: highlightColorHex) }

    static let maxStockCount = 64

    static let maPeriods: [Int] = [5, 10, 20, 30, 60, 120, 250]
    static let maNames: [String] = ["MA5", "MA10", "MA20", "MA30", "MA60", "MA120", "MA250"]

    // CMP 位标志（与 Android 版一致）
    static let cmpResultNone: Int = 0
    static let cmpResultPriceH: Int = 0x0001
    static let cmpResultPriceL: Int = 0x0010
    static let cmpResultRise: Int = 0x0100
    static let cmpResultDrop: Int = 0x1000

    // 同步服务器（主/备用自动切换）
    static let syncApiPrimary = "http://www.yanhexing.com:9000"
    static let syncApiFallback = "http://47.107.180.174:9000"

    // 版本检查
    static let apiVersionUrl = "http://47.107.180.174:9000/api/version"

    // 行情轮询间隔
    static let quotePollIntervalTrade: TimeInterval = 5
    static let syncPollIntervalTrade: TimeInterval = 10
    static let syncPollIntervalIdle: TimeInterval = 30 * 60

    static let helpInfo = "        声动金来是一个股价预警软件。您先设置好预警条件，无需盯盘。当股价到达您设置的条件时，软件自动发出提示！\n说明：本软件只提供报警功能，不能买卖股票！"
    static let freeInfo = "\n本软件尽最大努力提供准确可靠的信息。但对于使用本软件给您造成经济或精神上的损失，本软件及其作者不负任何法律责任！\n版权所有 深圳言和行软件 www.yanhexing.cn"
}

extension Color {
    init(hex: UInt, alpha: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: alpha
        )
    }
}