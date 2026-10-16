import SwiftUI

/// 报警历史（对应 Android AlarmHistoryActivity）
struct AlarmHistoryView: View {
    @State private var logs: [AlarmLogEntry] = []

    var body: some View {
        List {
            if logs.isEmpty {
                Text("暂无报警记录")
                    .foregroundColor(.gray)
            }
            ForEach(logs) { log in
                VStack(alignment: .leading, spacing: 3) {
                    Text(log.name.isEmpty ? log.message : "\(log.name)")
                        .font(.system(size: 15, weight: .medium))
                    Text(log.message)
                        .font(.footnote)
                    Text(log.createTime)
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
                .padding(.vertical, 2)
            }
        }
        .navigationTitle("报警历史")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            logs = StockStore.shared.getAlarmLogs(limit: 100)
        }
    }
}