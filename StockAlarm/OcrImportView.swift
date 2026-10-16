import SwiftUI
import Vision
import VisionKit
import PhotosUI
import UIKit

/// OCR 截图导入（对应 Android ML Kit OCR + 6 种代码识别正则 + 默认阈值）
struct OcrImportView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss

    struct Candidate: Identifiable {
        let id = UUID()
        var code: String
        var name: String = ""
        var priceH: Float = 0
        var priceL: Float = 0
        var rise: Int = 5
        var drop: Int = 5
        var selected: Bool = true
        var quote: StockQuote?
    }

    @State private var candidates: [Candidate] = []
    @State private var recognizedText = ""
    @State private var importing = false
    @State private var resultMsg = ""
    @State private var showCamera = false
    @State private var showLibrary = false

    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                if candidates.isEmpty {
                    VStack(spacing: 20) {
                        Image(systemName: "camera.viewfinder")
                            .font(.system(size: 56))
                            .foregroundColor(.gray)
                        Text("从股票软件截图中识别股票代码并批量导入")
                            .font(.footnote)
                            .foregroundColor(.secondary)
                        if importing {
                            ProgressView("识别中…")
                        }
                        HStack(spacing: 40) {
                            Button {
                                showCamera = true
                            } label: {
                                VStack {
                                    Image(systemName: "camera").font(.title2)
                                    Text("拍照").font(.caption)
                                }
                            }
                            Button {
                                showLibrary = true
                            } label: {
                                VStack {
                                    Image(systemName: "photo.on.rectangle").font(.title2)
                                    Text("相册").font(.caption)
                                }
                            }
                        }
                        if !resultMsg.isEmpty {
                            Text(resultMsg)
                                .font(.footnote)
                                .foregroundColor(.orange)
                                .padding(.horizontal)
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    List {
                        Section(footer: Text("默认预警：高于现价10% / 低于现价10% / 涨跌幅5%。识别后可逐条确认。")) {
                            ForEach($candidates) { $c in
                                HStack {
                                    Toggle("", isOn: $c.selected).labelsHidden()
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(c.name.isEmpty ? c.code : "\(c.name) (\(c.code))")
                                            .font(.system(size: 15, weight: .medium))
                                        if let q = c.quote, q.nowPrice > 0 {
                                            Text(String(format: "现价 %.2f → 高%.2f 低%.2f 涨%d%% 跌%d%%", q.nowPrice, c.priceH, c.priceL, c.rise, c.drop))
                                                .font(.caption2)
                                                .foregroundColor(.secondary)
                                        } else {
                                            Text("未获取到行情（仍可添加）")
                                                .font(.caption2)
                                                .foregroundColor(.orange)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    .listStyle(.insetGrouped)
                }
            }
            .navigationTitle("截图导入")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(candidates.isEmpty ? "" : "导入") {
                        Task { await importSelected() }
                    }
                    .disabled(candidates.isEmpty || importing)
                }
            }
            .sheet(isPresented: $showCamera) {
                ImagePicker(sourceType: .camera) { img in handleImage(img) }
            }
            .sheet(isPresented: $showLibrary) {
                ImagePicker(sourceType: .photoLibrary) { img in handleImage(img) }
            }
        }
        .navigationViewStyle(.stack)
    }

    // MARK: - OCR 识别

    private func handleImage(_ image: UIImage?) {
        guard let image = image else { return }
        importing = true
        resultMsg = ""
        Task {
            let text = await recognizeText(in: image)
            recognizedText = text
            let codes = OcrCodeExtractor.extractCodes(from: text)
            await buildCandidates(codes: codes)
            importing = false
            if candidates.isEmpty {
                resultMsg = "未能从图片中识别出股票代码，请换一张截图试试"
            }
        }
    }

    private func recognizeText(in image: UIImage) async -> String {
        guard let cgImage = image.cgImage else { return "" }
        return await withCheckedContinuation { continuation in
            let request = VNRecognizeTextRequest { req, _ in
                var lines: [String] = []
                let results = req.results as? [VNRecognizedTextObservation] ?? []
                for obs in results {
                    if let line = obs.topCandidates(1).first {
                        lines.append(line.string)
                    }
                }
                continuation.resume(returning: lines.joined(separator: "\n"))
            }
            request.recognitionLevel = .accurate
            request.usesLanguageCorrection = true
            if let langs = try? request.supportedRecognitionLanguages() {
                if langs.contains("zh-Hans") {
                    request.recognitionLanguages = ["zh-Hans", "en-US"]
                } else {
                    request.recognitionLanguages = ["en-US"]
                }
            }
            let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
            do {
                try handler.perform([request])
            } catch {
                continuation.resume(returning: "")
            }
        }
    }

    private func buildCandidates(codes: [String]) async {
        var result: [Candidate] = []
        let existingCodes = Set(model.stocks.map { CodeNormalizer.normalizeKey($0.code) ?? $0.code })

        for code in codes {
            let key = CodeNormalizer.normalizeKey(code) ?? code
            guard !existingCodes.contains(key) else { continue }
            guard result.first(where: { ($0.code == code) }) == nil else { continue }

            var c = Candidate(code: code)
            if let quote = await QuoteClient.shared.getSingleQuote(code: code) {
                c.name = quote.name
                c.quote = quote
                if quote.nowPrice > 0 {
                    c.priceH = quote.nowPrice * 1.10
                    c.priceL = quote.nowPrice * 0.90
                }
            }
            result.append(c)
        }
        candidates = result
    }

    private func importSelected() async {
        importing = true
        defer { importing = false }

        var added = 0
        for c in candidates where c.selected {
            let item = StockItem(
                code: c.code,
                name: c.name,
                priceH: c.priceH,
                priceL: c.priceL,
                rise: c.rise,
                drop: c.drop,
                valid: true
            )
            if model.addStock(item) {
                added += 1
            }
        }
        resultMsg = "已导入 \(added) 支股票"
        candidates = []
        await MainActor.run {
            model.reload()
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { dismiss() }
    }
}

// MARK: - 代码提取（与 Android 6 种正则方法一致）

enum OcrCodeExtractor {

    /// 从 OCR 文本中提取股票代码（去重保序）
    static func extractCodes(from text: String) -> [String] {
        var found: [String] = []

        // 1. HK 显式：hk00700 / 0700.HK
        for m in matches(text, pattern: "(?i)hk[ ]?(\\d{1,5})") {
            found.append("hk" + m)
        }
        for m in matches(text, pattern: "(?i)(\\d{1,5})[ ]?\\.HK") {
            found.append("hk" + m)
        }

        // 2. A股词边界 6 位
        for m in matches(text, pattern: "(?<![0-9])([0-9]{6})(?![0-9])") {
            found.append(m)
        }

        // 3. 纠错版：O→0 I/l→1 S→5 Z→2 后再找 6 位
        let corrected = text
            .replacingOccurrences(of: "O", with: "0")
            .replacingOccurrences(of: "o", with: "0")
            .replacingOccurrences(of: "I", with: "1")
            .replacingOccurrences(of: "l", with: "1")
            .replacingOccurrences(of: "S", with: "5")
            .replacingOccurrences(of: "Z", with: "2")
        for m in matches(corrected, pattern: "(?<![0-9])([0-9]{6})(?![0-9])") {
            found.append(m)
        }

        // 4. 分隔符容错：600 519 / 600-519
        for m in matches(text, pattern: "([0-9]{3})[- ]([0-9]{3})") {
            found.append(m)
        }

        // 归一化去重（保持顺序），过滤明显非股票代码（全 0 等）
        var seen = Set<String>()
        var out: [String] = []
        for code in found {
            guard let key = CodeNormalizer.normalizeKey(code) else { continue }
            if Int(key.filter { $0.isNumber }) == 0 { continue }
            if !seen.contains(key) {
                seen.insert(key)
                out.append(code)
            }
        }
        return out
    }

    /// 返回 pattern 第 1 捕获组的全部匹配（分隔符容错时拼接两段）
    private static func matches(_ text: String, pattern: String) -> [String] {
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return [] }
        let ns = text as NSString
        var out: [String] = []
        for m in regex.matches(in: text, range: NSRange(location: 0, length: ns.length)) {
            if m.numberOfRanges >= 2 {
                let g1 = ns.substring(with: m.range(at: 1))
                if m.numberOfRanges >= 3 {
                    let g2 = ns.substring(with: m.range(at: 2))
                    out.append(g1 + g2)
                } else {
                    out.append(g1)
                }
            }
        }
        return out
    }
}

// MARK: - UIImagePicker 包装（iOS 15 无 PhotosPicker）

struct ImagePicker: UIViewControllerRepresentable {
    var sourceType: UIImagePickerController.SourceType
    var onPick: (UIImage?) -> Void

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = sourceType
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(onPick: onPick)
    }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let onPick: (UIImage?) -> Void
        init(onPick: @escaping (UIImage?) -> Void) { self.onPick = onPick }

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            onPick(info[.originalImage] as? UIImage)
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            onPick(nil)
        }
    }
}