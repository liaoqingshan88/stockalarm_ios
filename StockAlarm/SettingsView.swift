import SwiftUI

/// 设置页（对应 Android SettingsActivity）
struct SettingsView: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var settings: AppSettings

    @State private var showLogin = false
    @State private var showLogoutConfirm = false
    @State private var showClearConfirm = false
    @State private var showClearCloudConfirm = false
    @State private var checkingUpdate = false
    @State private var updateMsg = ""

    var body: some View {
        Form {
            Section(header: Text("预警设置")) {
                Toggle("报警时振动", isOn: $settings.vibrate)
                Toggle("彩色列表底色", isOn: $settings.colorfulList)
                Toggle("均线预警（MA5~MA250 穿越提醒）", isOn: $settings.maAlarmEnabled)
            }

            Section(header: Text("多端同步"), footer: Text("登录同一账号，手机/PC 预警数据自动同步（每 10 秒）")) {
                if SyncClient.shared.isLoggedIn {
                    HStack {
                        Text("已登录")
                        Spacer()
                        Text(SyncClient.shared.phone)
                            .foregroundColor(.secondary)
                    }
                    Button("立即同步一次") {
                        Task {
                            let ok = await SyncManager.shared.pullAndMerge()
                            model.lastSyncMessage = ok ? "手动同步完成" : "同步失败，请检查网络"
                            model.reload()
                        }
                    }
                    Button("修改密码") {
                        showLogin = true
                    }
                    Button("退出登录", role: .destructive) {
                        showLogoutConfirm = true
                    }
                } else {
                    Button("登录 / 注册") {
                        showLogin = true
                    }
                }
            }

            Section(header: Text("数据")) {
                Button("清空本机股票", role: .destructive) {
                    showClearConfirm = true
                }
                if SyncClient.shared.isLoggedIn {
                    Button("清空全部（含其它客户端）", role: .destructive) {
                        showClearCloudConfirm = true
                    }
                }
            }

            Section(header: Text("关于")) {
                HStack {
                    Text("版本")
                    Spacer()
                    Text("\(UpdateChecker.localVersionName) (\(UpdateChecker.localVersionCode))")
                        .foregroundColor(.secondary)
                }
                Button {
                    Task { await checkUpdate() }
                } label: {
                    HStack {
                        Text("检查更新")
                        if checkingUpdate { Spacer(); ProgressView() }
                    }
                }
                if !updateMsg.isEmpty {
                    Text(updateMsg)
                        .font(.footnote)
                        .foregroundColor(.secondary)
                }
                Text("声动金来 · 深圳言和行软件\nwww.yanhexing.cn")
                    .font(.footnote)
                    .foregroundColor(.secondary)
            }
        }
        .navigationTitle("设置")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showLogin) {
            SyncLoginView()
        }
        .confirmationDialog("确认退出登录？", isPresented: $showLogoutConfirm, titleVisibility: .visible) {
            Button("退出登录", role: .destructive) {
                model.onLoggedOut()
                model.lastSyncMessage = ""
            }
        }
        .confirmationDialog("确认清空本机全部股票及预警设置？", isPresented: $showClearConfirm, titleVisibility: .visible) {
            Button("清空本机", role: .destructive) {
                model.clearAllStocks()
            }
        }
        .confirmationDialog("将清空服务器与所有已登录客户端的股票数据，不可恢复！", isPresented: $showClearCloudConfirm, titleVisibility: .visible) {
            Button("全部清空", role: .destructive) {
                Task {
                    do {
                        try await SyncManager.shared.clearStocksAcrossClients()
                        model.reload()
                    } catch {
                        model.lastSyncMessage = "清空失败：\((error as? SyncClient.SyncError)?.message ?? "网络错误")"
                    }
                }
            }
        }
    }

    private func checkUpdate() async {
        checkingUpdate = true
        defer { checkingUpdate = false }
        if let info = await UpdateChecker.check() {
            if info.versionCode > UpdateChecker.localVersionCode {
                updateMsg = "发现新版本 v\(info.versionName)：\(info.updateContent)"
            } else {
                updateMsg = "已是最新版本"
            }
        } else {
            updateMsg = "检查更新失败，请稍后重试"
        }
    }
}