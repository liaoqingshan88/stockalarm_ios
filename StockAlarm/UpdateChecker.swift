import Foundation

/// 版本检查（与 Android Update 一致；iOS 版不下载安装，仅提示）
enum UpdateChecker {

    struct VersionInfo {
        var versionCode: Int
        var versionName: String
        var updateContent: String
        var downloadUrl: String
    }

    /// 查询服务器最新版本；失败返回 nil
    static func check() async -> VersionInfo? {
        guard let url = URL(string: AppConstants.apiVersionUrl) else { return nil }
        var request = URLRequest(url: url)
        request.timeoutInterval = 10

        do {
            let (data, resp) = try await URLSession.shared.data(for: request)
            guard let http = resp as? HTTPURLResponse, http.statusCode == 200 else { return nil }
            guard let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
            guard obj["success"] as? Bool == true else { return nil }
            let version = obj["version"] as? [String: Any] ?? obj
            return VersionInfo(
                versionCode: version["version_code"] as? Int ?? (version["versionCode"] as? Int ?? 0),
                versionName: version["version_name"] as? String ?? (version["versionName"] as? String ?? ""),
                updateContent: version["update_content"] as? String ?? (version["updateContent"] as? String ?? ""),
                downloadUrl: version["download_url"] as? String ?? (version["downloadUrl"] as? String ?? "")
            )
        } catch {
            return nil
        }
    }

    /// 当前 App 版本号
    static var localVersionCode: Int {
        Int(Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "0") ?? 0
    }

    static var localVersionName: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
    }
}