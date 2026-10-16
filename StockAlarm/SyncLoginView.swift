import SwiftUI

/// 同步账号：登录 / 注册 / 修改密码（对应 Android SyncLoginActivity）
struct SyncLoginView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss

    enum Mode: String, CaseIterable {
        case login = "登录"
        case register = "注册"
        case changePassword = "改密"
    }

    @State private var mode: Mode = SyncClient.shared.isLoggedIn ? .changePassword : .login
    @State private var phone = ""
    @State private var password = ""
    @State private var oldPassword = ""
    @State private var newPassword = ""
    @State private var busy = false
    @State private var errorText = ""
    @State private var infoText = ""

    var body: some View {
        NavigationView {
            Form {
                Picker("模式", selection: $mode) {
                    ForEach(Mode.allCases, id: \.self) { m in
                        Text(m.rawValue).tag(m)
                    }
                }
                .pickerStyle(.segmented)

                Section(header: Text(mode == .changePassword ? "账号" : "手机号")) {
                    if mode != .changePassword || !SyncClient.shared.isLoggedIn {
                        TextField("手机号", text: $phone)
                            .keyboardType(.phonePad)
                    } else {
                        Text(SyncClient.shared.phone)
                            .foregroundColor(.secondary)
                    }
                }

                if mode == .login {
                    Section(header: Text("密码")) {
                        SecureField("密码（注册后初始密码 123456）", text: $password)
                    }
                } else if mode == .register {
                    Section(footer: Text("注册后初始密码为 123456，请登录后及时修改")) {
                        EmptyView()
                    }
                } else {
                    Section(header: Text("修改密码")) {
                        SecureField("旧密码", text: $oldPassword)
                        SecureField("新密码", text: $newPassword)
                    }
                }

                if !errorText.isEmpty {
                    Text(errorText).foregroundColor(.red).font(.footnote)
                }
                if !infoText.isEmpty {
                    Text(infoText).foregroundColor(.green).font(.footnote)
                }
            }
            .navigationTitle("多端同步")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(mode == .login ? "登录" : (mode == .register ? "注册" : "确认")) {
                        Task { await submit() }
                    }
                    .disabled(busy || (mode != .register && phone.isEmpty && mode != .changePassword))
                }
            }
        }
        .navigationViewStyle(.stack)
    }

    private func submit() async {
        busy = true
        errorText = ""
        infoText = ""
        defer { busy = false }

        do {
            switch mode {
            case .register:
                let msg = try await SyncClient.shared.register(phone: phone.trimmingCharacters(in: .whitespaces))
                infoText = msg.isEmpty ? "注册成功，初始密码 123456" : msg
                mode = .login
                password = "123456"
            case .login:
                _ = try await SyncClient.shared.login(phone: phone.trimmingCharacters(in: .whitespaces), password: password)
                infoText = "登录成功"
                model.onLoggedIn()
                model.reload()
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) { dismiss() }
            case .changePassword:
                _ = try await SyncClient.shared.changePassword(oldPassword: oldPassword, newPassword: newPassword)
                infoText = "密码已修改，请重新登录"
                model.onLoggedOut()
                mode = .login
            }
        } catch let e as SyncClient.SyncError {
            errorText = e.message
        } catch {
            errorText = "网络错误，请稍后重试"
        }
    }
}