import SwiftUI

/// 帮助页（对应 Android Help）
struct HelpView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(AppConstants.helpInfo)
                    .font(.system(size: 15))
                Text(AppConstants.freeInfo)
                    .font(.system(size: 13))
                    .foregroundColor(.secondary)

                Group {
                    Text("使用说明")
                        .font(.headline)
                    Text("""
                    1. 点击右上角 + 添加预警股票；
                    2. 设置价格高于/低于、涨幅/跌幅条件；
                    3. 交易时段内自动每 5 秒检查一次行情；
                    4. 点击股票行可停用/启用预警，长按编辑；
                    5. 点击列表头可按名称/现价/涨跌幅排序；
                    6. 相机图标可从截图识别批量导入股票；
                    7. 设置中可登录账号，多端同步预警数据。

                    预警类型：
                    · 价格预警：高于/低于设定值
                    · 涨跌幅预警：涨幅/跌幅大于设定百分比
                    · 均线预警：上穿/下穿 MA5~MA250（每条均线每天只报一次）
                    · 次高点预警：上冲前高未破/下探前低未破（每天每方向最多一次）
                    """)
                    .font(.system(size: 14))
                }

                Text("版本 \(UpdateChecker.localVersionName) (\(UpdateChecker.localVersionCode))")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            .padding()
        }
        .navigationTitle("帮助")
        .navigationBarTitleDisplayMode(.inline)
    }
}