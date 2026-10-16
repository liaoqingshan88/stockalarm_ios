import SwiftUI

/// 添加 / 编辑预警股票（对应 Android 添加股票对话框）
struct StockEditView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss

    let existing: StockItem?

    @State private var code = ""
    @State private var name = ""
    @State private var priceH = ""
    @State private var priceL = ""
    @State private var rise = ""
    @State private var drop = ""
    @State private var valid = true
    @State private var checking = false
    @State private var errorText = ""
    @State private var resolvedName = ""
    @State private var fetched = false

    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("股票代码")) {
                    HStack {
                        TextField("如 600519 / 000002 / 00700", text: $code)
                            .keyboardType(.asciiCapable)
                            .autocorrectionDisabled()
                            .onChange(of: code) { _ in
                                fetched = false
                                resolvedName = ""
                            }
                        if checking {
                            ProgressView()
                        } else if fetched, !resolvedName.isEmpty {
                            Image(systemName: "checkmark.circle.fill").foregroundColor(.green)
                        }
                    }
                    if !resolvedName.isEmpty {
                        Text("识别结果：\(resolvedName)")
                            .font(.footnote)
                            .foregroundColor(.secondary)
                    }
                    Button {
                        Task { await fetchName() }
                    } label: {
                        Text(existing == nil ? "校验代码并获取名称" : "重新获取名称")
                    }
                    .disabled(code.isEmpty || checking)
                }

                Section(header: Text("预警条件（0 或留空表示不启用该条件）")) {
                    HStack {
                        Text("价格高于")
                        TextField("0", text: $priceH)
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.trailing)
                    }
                    HStack {
                        Text("价格低于")
                        TextField("0", text: $priceL)
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.trailing)
                    }
                    HStack {
                        Text("涨幅大于")
                        TextField("0", text: $rise)
                            .keyboardType(.numberPad)
                            .multilineTextAlignment(.trailing)
                        Text("%")
                    }
                    HStack {
                        Text("跌幅大于")
                        TextField("0", text: $drop)
                            .keyboardType(.numberPad)
                            .multilineTextAlignment(.trailing)
                        Text("%")
                    }
                }

                Section {
                    Toggle("启用预警", isOn: $valid)
                }

                if !errorText.isEmpty {
                    Section {
                        Text(errorText).foregroundColor(.red).font(.footnote)
                    }
                }
            }
            .navigationTitle(existing == nil ? "添加股票" : "编辑股票")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存") { save() }
                }
            }
            .onAppear(perform: fillExisting)
        }
        .navigationViewStyle(.stack)
    }

    private func fillExisting() {
        guard let e = existing else { return }
        code = e.code
        name = e.name
        priceH = e.priceH > 0 ? trimNum(e.priceH) : ""
        priceL = e.priceL > 0 ? trimNum(e.priceL) : ""
        rise = e.rise > 0 ? String(e.rise) : ""
        drop = e.drop > 0 ? String(e.drop) : ""
        valid = e.valid
    }

    private func trimNum(_ v: Float) -> String {
        v == v.rounded() ? String(Int(v)) : String(format: "%.2f", v)
    }

    private func fetchName() async {
        checking = true
        errorText = ""
        defer { checking = false }
        if let q = await QuoteClient.shared.getSingleQuote(code: code) {
            resolvedName = q.name
            fetched = true
            if name.isEmpty { name = q.name }
        } else {
            errorText = "无法识别该代码，请检查后重试"
        }
    }

    private func save() {
        let trimmedCode = code.trimmingCharacters(in: .whitespaces)
        guard !trimmedCode.isEmpty else {
            errorText = "请输入股票代码"
            return
        }
        guard CodeNormalizer.normalizeKey(trimmedCode) != nil else {
            errorText = "股票代码格式不正确"
            return
        }

        let h = Float(priceH) ?? 0
        let l = Float(priceL) ?? 0
        let r = Int(rise) ?? 0
        let d = Int(drop) ?? 0

        if h > 0 && l > 0 && h <= l {
            errorText = "“高于”必须大于“低于”"
            return
        }
        if h <= 0 && l <= 0 && r <= 0 && d <= 0 {
            errorText = "请至少设置一个预警条件"
            return
        }

        let finalName = name.isEmpty ? resolvedName : name
        let item = StockItem(
            code: trimmedCode,
            name: finalName,
            priceH: h,
            priceL: l,
            rise: r,
            drop: d,
            valid: valid
        )

        if let e = existing {
            model.updateStock(item)
            dismiss()
        } else {
            if model.addStock(item) {
                dismiss()
            } else {
                errorText = "该股票已存在，或数量已达上限（\(AppConstants.maxStockCount) 支）"
            }
        }
    }
}