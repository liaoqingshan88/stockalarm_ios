import Foundation

/// 上冲前高失败回落 / 下探前低失败反弹 状态追踪（与 Android PeakValleyTracker 一致）
///
/// 上涨>3% → 浅回调(38.2%) → 接近前高(≥前高*0.995) → 3分钟/36采样未破 → 报警
/// 下跌>3% → 浅反弹(38.2%) → 接近前低(≤前低*1.005) → 3分钟/36采样未破 → 报警
/// 每只股票每天只报一次（高/低侧各一次）
final class PeakValleyTracker {

    static let shared = PeakValleyTracker()

    private var states: [String: [Int]] = [:]
    private let queue = DispatchQueue(label: "stockalarm.peakvalley")

    private let riseThreshold: Float = 0.03
    private let retraceRatio: Float = 0.382
    private let nearHigh: Float = 0.995
    private let nearLow: Float = 1.005
    private let confirmSamples = 36
    private let maxAlerts = 1
    private let scale = 10000

    private var onRecord: ((String, String, String, Float, Float, Float) -> Void)?

    func setRecorder(_ handler: @escaping (String, String, String, Float, Float, Float) -> Void) {
        queue.sync { onRecord = handler }
    }

    func resetForNewDay() {
        queue.sync {
            let today = StockTime.dateStr()
            states = states.filter { $0.key.hasPrefix(today) }
        }
    }

    private func makeKey(_ code: String) -> String {
        StockTime.dateStr() + ":" + code
    }

    /// 返回报警文本（nil = 无报警）
    func checkApproachFailure(code: String, name: String, nowPrice: Float) -> String? {
        queue.sync {
            let key = makeKey(code)
            if states[key] == nil { states[key] = [Int](repeating: 0, count: 12) }
            var s = states[key]!

            let phase = s[0]
            var result: String? = nil

            let riseStartPrice = Float(s[1]) / Float(scale)
            var waveHigh = Float(s[2]) / Float(scale)
            var valleyPrice = Float(s[3]) / Float(scale)
            var waveLow = Float(s[4]) / Float(scale)
            var peakPrice = Float(s[5]) / Float(scale)

            func recordAlarm(side: String, refPrice: Float) {
                if s[side == "high" ? 8 : 9] < maxAlerts {
                    s[side == "high" ? 8 : 9] += 1
                    onRecord?(code, name, side, nowPrice, refPrice, nowPrice)
                }
            }

            func resetToIdle() {
                s[0] = 0
                s[1] = Int(nowPrice * Float(scale))
                s[2] = 0
                s[3] = 0
                s[4] = 0
                s[5] = 0
                s[6] = 0
                s[7] = 0
                s[10] = 0
                s[11] = 0
            }

            switch phase {
            case 0: // idle
                if s[1] == 0 {
                    s[1] = Int(nowPrice * Float(scale))
                } else if nowPrice > riseStartPrice * (1 + riseThreshold) {
                    s[2] = Int(nowPrice * Float(scale))
                    s[0] = 1
                } else if nowPrice < riseStartPrice * (1 - riseThreshold) {
                    s[4] = Int(nowPrice * Float(scale))
                    s[0] = 10
                }

            case 1: // up_wave
                waveHigh = Float(s[2]) / Float(scale)
                if nowPrice > waveHigh {
                    s[2] = Int(nowPrice * Float(scale))
                } else {
                    let riseAmount = waveHigh - riseStartPrice
                    let dropAmount = waveHigh - nowPrice
                    if riseAmount > 0.0001 && dropAmount >= riseAmount * retraceRatio {
                        s[3] = Int(nowPrice * Float(scale))
                        s[0] = 2
                    }
                }

            case 2: // pullback
                valleyPrice = Float(s[3]) / Float(scale)
                waveHigh = Float(s[2]) / Float(scale)
                if nowPrice < valleyPrice {
                    s[3] = Int(nowPrice * Float(scale))
                } else if nowPrice > valleyPrice * 1.003 {
                    if nowPrice >= waveHigh * nearHigh {
                        s[6] = 1
                        s[0] = 3
                    }
                }

            case 3: // re_approach_high
                waveHigh = Float(s[2]) / Float(scale)
                if nowPrice > waveHigh * 1.002 {
                    // 真突破前高，重置为新上涨波段
                    s[0] = 1
                    s[1] = s[2]
                    s[2] = Int(nowPrice * Float(scale))
                    s[3] = 0
                    s[6] = 0
                    s[10] = 0
                    break
                }
                if nowPrice >= waveHigh * nearHigh {
                    s[10] += 1
                    if s[6] == 1 && s[10] >= confirmSamples {
                        recordAlarm(side: "high", refPrice: waveHigh)
                        result = String(format: "上冲前高未破(高%.2f现%.2f)", waveHigh, nowPrice)
                        resetToIdle()
                    }
                    break
                }
                if s[6] == 1 || s[10] > 0 {
                    recordAlarm(side: "high", refPrice: waveHigh)
                    result = String(format: "上冲前高失败(高%.2f现%.2f)", waveHigh, nowPrice)
                    resetToIdle()
                }

            case 10: // down_wave
                waveLow = Float(s[4]) / Float(scale)
                if nowPrice < waveLow {
                    s[4] = Int(nowPrice * Float(scale))
                } else {
                    let dropAmount = riseStartPrice - waveLow
                    let riseAmount = nowPrice - waveLow
                    if dropAmount > 0.0001 && riseAmount >= dropAmount * retraceRatio {
                        s[5] = Int(nowPrice * Float(scale))
                        s[0] = 20
                    }
                }

            case 20: // rebound
                peakPrice = Float(s[5]) / Float(scale)
                waveLow = Float(s[4]) / Float(scale)
                if nowPrice > peakPrice {
                    s[5] = Int(nowPrice * Float(scale))
                } else if nowPrice < peakPrice * 0.997 {
                    if nowPrice <= waveLow * nearLow {
                        s[7] = 1
                        s[0] = 30
                    }
                }

            case 30: // re_approach_low
                waveLow = Float(s[4]) / Float(scale)
                if nowPrice < waveLow * 0.998 {
                    // 真跌破前低，重置为新下跌波段
                    s[0] = 10
                    s[1] = s[4]
                    s[4] = Int(nowPrice * Float(scale))
                    s[5] = 0
                    s[7] = 0
                    s[11] = 0
                    break
                }
                if nowPrice <= waveLow * nearLow {
                    s[11] += 1
                    if s[7] == 1 && s[11] >= confirmSamples {
                        recordAlarm(side: "low", refPrice: waveLow)
                        result = String(format: "下探前低未破(低%.2f现%.2f)", waveLow, nowPrice)
                        resetToIdle()
                    }
                    break
                }
                if s[7] == 1 || s[11] > 0 {
                    recordAlarm(side: "low", refPrice: waveLow)
                    result = String(format: "下探前低失败(低%.2f现%.2f)", waveLow, nowPrice)
                    resetToIdle()
                }

            default:
                break
            }

            states[key] = s
            return result
        }
    }
}