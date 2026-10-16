import SwiftUI

enum SortColumn {
    case none, name, price, chg
}

enum SortOrder {
    case none, desc, asc
}

struct MainView: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var settings: AppSettings

    @State private var sortColumn: SortColumn = .none
    @State private var sortOrder: SortOrder = .none
    @State private var editingItem: StockItem?
    @State private var showAdd = false
    @State private var showOcr = false

    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                headerRow
                if model.isHttpTimeout {
                    Text("网络连接超时，正在重试…")
                        .font(.caption)
                        .foregroundColor(.orange)
                        .padding(.vertical, 3)
                }
                if !model.lastSyncMessage.isEmpty {
                    Text(model.lastSyncMessage)
                        .font(.caption)
                        .foregroundColor(.gray)
                        .lineLimit(1)
                        .padding(.horizontal, 8)
                }
                stockList
                alarmBar
            }
            .navigationTitle("声动金来")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItemGroup(placement: .navigationBarLeading) {
                    NavigationLink(destination: AlarmHistoryView()) {
                        Image(systemName: "clock.arrow.circlepath")
                    }
                    NavigationLink(destination: HelpView()) {
                        Image(systemName: "questionmark.circle")
                    }
                }
                ToolbarItemGroup(placement: .navigationBarTrailing) {
                    Button {
                        showOcr = true
                    } label: {
                        Image(systemName: "camera.viewfinder")
                    }
                    Button {
                        showAdd = true
                    } label: {
                        Image(systemName: "plus")
                    }
                    NavigationLink(destination: SettingsView()) {
                        Image(systemName: "gearshape")
                    }
                }
            }
            .sheet(isPresented: $showAdd) {
                StockEditView(existing: nil)
            }
            .sheet(isPresented: $showOcr) {
                OcrImportView()
            }
            .sheet(item: $editingItem) { item in
                StockEditView(existing: item)
            }
        }
        .navigationViewStyle(.stack)
    }

    // MARK: - 列表头（点击排序：降序→升序→取消）

    private var headerRow: some View {
        HStack {
            Button { onSortClick(.name) } label: {
                headerText("名称", col: .name)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            Button { onSortClick(.price) } label: {
                headerText("现价", col: .price)
                    .frame(width: 72, alignment: .trailing)
            }
            Button { onSortClick(.chg) } label: {
                headerText("涨跌幅", col: .chg)
                    .frame(width: 72, alignment: .trailing)
            }
        }
        .font(.system(size: 14, weight: .medium))
        .foregroundColor(.primary)
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(Color(.systemGray5))
    }

    private func headerText(_ base: String, col: SortColumn) -> Text {
        let arrow: String
        if col == sortColumn {
            arrow = sortOrder == .desc ? " ↓" : (sortOrder == .asc ? " ↑" : "")
        } else {
            arrow = ""
        }
        return Text(base + arrow)
    }

    private func onSortClick(_ col: SortColumn) {
        if col != sortColumn {
            sortColumn = col
            sortOrder = .desc
        } else if sortOrder == .desc {
            sortOrder = .asc
        } else if sortOrder == .asc {
            sortOrder = .none
            sortColumn = .none
        } else {
            sortOrder = .desc
        }
    }

    // MARK: - 排序后的展示数据

    private var displayStocks: [StockItem] {
        let base = model.stocks
        switch sortColumn {
        case .none:
            return base
        case .name:
            return base.sorted { sortOrder == .desc ? $0.name > $1.name : $0.name < $1.name }
        case .price:
            return base.sorted {
                let a = model.quotes[CodeNormalizer.normalizeKey($0.code) ?? ""]?.nowPrice ?? -1
                let b = model.quotes[CodeNormalizer.normalizeKey($1.code) ?? ""]?.nowPrice ?? -1
                return sortOrder == .desc ? a > b : a < b
            }
        case .chg:
            return base.sorted {
                let a = model.quotes[CodeNormalizer.normalizeKey($0.code) ?? ""]?.changePercent ?? -999
                let b = model.quotes[CodeNormalizer.normalizeKey($1.code) ?? ""]?.changePercent ?? -999
                return sortOrder == .desc ? a > b : a < b
            }
        }
    }

    // MARK: - 股票列表

    private var stockList: some View {
        List {
            if displayStocks.isEmpty {
                VStack {
                    Text("暂无预警股票")
                        .foregroundColor(.gray)
                        .padding(.top, 60)
                    Text("点击右上角 + 添加，或用相机图标从截图导入")
                        .font(.caption)
                        .foregroundColor(.gray)
                }
                .frame(maxWidth: .infinity)
                .listRowBackground(Color.clear)
            }
            ForEach(displayStocks) { item in
                StockRowView(item: item, quote: model.quotes[CodeNormalizer.normalizeKey(item.code) ?? ""])
                    .contentShape(Rectangle())
                    .onTapGesture { toggleValid(item) }
                    .onLongPressGesture { editingItem = item }
                    .listRowInsets(EdgeInsets(top: 4, leading: 8, bottom: 4, trailing: 8))
                    .background(
                        Group {
                            if item.code == model.scrollToCode {
                                AppConstants.highlightColor
                            } else {
                                Color.clear
                            }
                        }
                    )
            }
        }
        .listStyle(.plain)
        .onChange(of: model.scrollToCode) { code in
            if let code = code {
                DispatchQueue.main.asyncAfter(deadline: .now() + 5) {
                    if model.scrollToCode == code { model.scrollToCode = nil }
                }
            }
        }
    }

    private func toggleValid(_ item: StockItem) {
        model.toggleValid(code: item.code, valid: !item.valid)
    }

    // MARK: - 底部报警信息栏

    private var alarmBar: some View {
        Group {
            if let last = model.alarmMessages.last {
                ScrollView {
                    Text(last)
                        .font(.footnote)
                        .foregroundColor(.red)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(6)
                }
                .frame(maxHeight: 56)
                .background(Color(.systemGray6))
            }
        }
    }
}

// MARK: - 单行股票视图（涨红跌绿，彩色模式底色 #FFF4CC）

struct StockRowView: View {
    @EnvironmentObject var settings: AppSettings
    let item: StockItem
    let quote: StockQuote?

    private var chgText: String {
        guard let q = quote else { return "--" }
        let p = q.changePercent
        return String(format: "%+.2f%%", p)
    }

    private var priceText: String {
        guard let q = quote, q.nowPrice > 0 else { return "--" }
        return String(format: "%.2f", q.nowPrice)
    }

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 4) {
                    Text(item.name.isEmpty ? item.code : item.name)
                        .font(.system(size: 16, weight: .medium))
                        .lineLimit(1)
                    if !item.valid {
                        Text("已停")
                            .font(.system(size: 10))
                            .foregroundColor(.white)
                            .padding(.horizontal, 4)
                            .padding(.vertical, 1)
                            .background(Color.gray)
                            .cornerRadius(3)
                    }
                }
                Text(conditionSummary)
                    .font(.system(size: 11))
                    .foregroundColor(.secondary)
                    .lineLimit(1)
            }
            Spacer()
            Text(priceText)
                .font(.system(size: 16, weight: .medium).monospacedDigit())
                .frame(width: 72, alignment: .trailing)
            Text(chgText)
                .font(.system(size: 15, weight: .medium).monospacedDigit())
                .foregroundColor(chgColor)
                .frame(width: 72, alignment: .trailing)
        }
        .padding(.vertical, 2)
        .padding(.horizontal, 4)
        .background(settings.colorfulList ? AppConstants.highlightColor.opacity(0.55) : Color.clear)
        .contentShape(Rectangle())
    }

    private var chgColor: Color {
        guard let q = quote else { return .primary }
        if q.nowPrice > q.yestodayPrice { return AppConstants.riseColor }
        if q.nowPrice < q.yestodayPrice { return AppConstants.dropColor }
        return .primary
    }

    private var conditionSummary: String {
        var parts: [String] = []
        if item.priceH > 0 { parts.append("高\(fmt(item.priceH))") }
        if item.priceL > 0 { parts.append("低\(fmt(item.priceL))") }
        if item.rise > 0 { parts.append("涨\(item.rise)%") }
        if item.drop > 0 { parts.append("跌\(item.drop)%") }
        let ma = StockStore.shared.getMaPrices(code: item.code)
        let maCount = ma.filter { $0 > 0 }.count
        if maCount > 0 { parts.append("MA×\(maCount)") }
        if parts.isEmpty { return "未设条件（点击停/启，长按编辑）" }
        return parts.joined(separator: " ") + "（长按编辑）"
    }

    private func fmt(_ v: Float) -> String {
        v == v.rounded() ? String(Int(v)) : String(format: "%.2f", v)
    }
}