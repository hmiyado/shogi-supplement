import Foundation

/// バンドル内 SentryConfig.properties から Sentry DSN を読む。
///
/// ファイルはビルド時に app/local.properties から生成される（project.yml の
/// preBuildScripts 参照）。リポジトリには秘密を置かない（Androidの
/// local.properties→BuildConfig、iOS側Supabase設定と同じ方針）。ファイルが無い・
/// 値が空のときは nil を返し、呼び出し側（iosAppApp.swift）はSentry初期化自体をスキップする。
enum IosSentryConfig {

    static func loadDsn() -> String? {
        guard let path = Bundle.main.path(forResource: "SentryConfig", ofType: "properties"),
              let text = try? String(contentsOfFile: path, encoding: .utf8) else {
            return nil
        }
        for line in text.split(separator: "\n") {
            guard let eq = line.firstIndex(of: "=") else { continue }
            let key = line[line.startIndex..<eq].trimmingCharacters(in: .whitespaces)
            guard key == "SENTRY_DSN" else { continue }
            let value = line[line.index(after: eq)...].trimmingCharacters(in: .whitespaces)
            return value.isEmpty ? nil : value
        }
        return nil
    }
}
