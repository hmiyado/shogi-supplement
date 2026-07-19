import Sentry
import SwiftUI

/// アプリのエントリポイント。本体UIはCompose Multiplatform（MainViewController）が担い、
/// Swift側は起動時初期化（Sentry・ファイルピッカー登録）とタブ切替のみを持つ。
@main
struct iosAppApp: App {
    // KifFilePickerCoordinator の init で IosFileImportBridge へ
    // ピッカー提示ハンドラを登録する。起動時に一度参照して確実に初期化させる
    // （lazy static のため、参照するまでは init が走らない）。
    init() {
        Self.startSentry()
        _ = KifFilePickerCoordinator.shared
        Self.seedPasteboardForUITestIfNeeded()
    }

    /// クラッシュレポート初期化。DSNはビルド時にapp/local.propertiesから生成される
    /// SentryConfig.properties（IosSentryConfig.swift）経由で読み、リポジトリには
    /// 直書きしない（公開するとイベントを外部から投げ込まれるリスクがあるため）。
    /// 未設定ビルドでは初期化自体をスキップする。environmentでdebug/releaseを
    /// 区別し、デフォルトPIIは送信しない（Android側と同じ設定）。
    private static func startSentry() {
        guard let dsn = IosSentryConfig.loadDsn() else { return }
        SentrySDK.start { options in
            options.dsn = dsn
            #if DEBUG
            options.environment = "debug"
            #else
            options.environment = "release"
            #endif
            options.sendDefaultPii = false
            // release名をデフォルト（bundle id由来）に任せない理由: AndroidとiOSは
            // 同一Sentryプロジェクトに送っており、bundle id由来の名前だと両OSの
            // releaseが衝突して集計が混ざる。iOS固有のrelease名を明示して区別する。
            let info = Bundle.main.infoDictionary
            let version = info?["CFBundleShortVersionString"] as? String ?? "0"
            let build = info?["CFBundleVersion"] as? String ?? "0"
            options.releaseName = "dev.miyado.shogisupplement.ios@\(version)+\(build)"
        }
    }

    /// 実機ではXCUITestランナー（バックグラウンドプロセス）からのUIPasteboard書き込みが
    /// PBErrorDomain Code=10/11 で拒否されるため、スモークテストはlaunchEnvironment経由で
    /// KIFを渡し、フォアグラウンドのアプリ自身にクリップボードへ書き込ませる。
    private static func seedPasteboardForUITestIfNeeded() {
        #if DEBUG
        guard let b64 = ProcessInfo.processInfo.environment["UITEST_PASTEBOARD_KIF_BASE64"],
              let data = Data(base64Encoded: b64),
              let text = String(data: data, encoding: .utf8) else {
            return
        }
        UIPasteboard.general.string = text
        // init時点ではまだforeground activeでない可能性があるため、active化後にも書き込む。
        NotificationCenter.default.addObserver(
            forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main
        ) { _ in
            UIPasteboard.general.string = text
        }
        #endif
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
