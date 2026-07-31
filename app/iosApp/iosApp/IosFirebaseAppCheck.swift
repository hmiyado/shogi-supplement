import FirebaseAppCheck
import FirebaseCore
import SharedUi

/// Firebase App Check初期化（匿名アカウント量産への防御）。
///
/// `local/GoogleService-Info.plist`（git管理外）が無いビルドではFirebase初期化自体を
/// スキップする（graceful degradation。IosSentryConfig/IosSupabaseConfigと同じ方針）。
/// ワーカー側の検証も`FIREBASE_PROJECT_NUMBER`未設定なら無効の段階導入のため、
/// このファイルが一切呼ばれなくても既存ビルドは壊れない。
///
/// シミュレータではApp Attestが動作しないため、DebugビルドはDebugプロバイダ
/// （[AppCheckDebugProviderFactory]）を使う。実機Release/TestFlight/App Storeは
/// [AppAttestProviderFactory]（Firebase側でApp Attest有効化済み）。
///
/// プロバイダファクトリは`FirebaseApp.configure()`より前に設定する必要がある
/// （Firebase公式ドキュメントの要求。順序を守らないと登録が反映されない）。
enum IosFirebaseAppCheck {

    static func configureIfAvailable() {
        guard Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil else {
            return
        }

        #if DEBUG
        // Xcodeスキームの環境変数にしないのは、ホーム画面からの単体起動では効かないため。
        // ソースへのハードコードにしないのは、公開リポジトリではトークン所持者が
        // App Checkを素通りできてしまうため。ファイルが無いビルドはSDK既定の
        // 端末生成トークンにフォールバックする（インストールごとに要再登録）。
        if let url = Bundle.main.url(forResource: "AppCheckDebugToken", withExtension: "txt"),
           let fixedToken = (try? String(contentsOf: url, encoding: .utf8))?
               .trimmingCharacters(in: .whitespacesAndNewlines),
           !fixedToken.isEmpty {
            setenv("AppCheckDebugToken", fixedToken, 1)
            // 環境変数はNSProcessInfoのスナップショットタイミング次第で読まれない
            // 可能性が残るため、SDKの保存キーにも直接書いて確実にする。
            UserDefaults.standard.set(fixedToken, forKey: "FIRAAppCheckDebugToken")
        }
        let providerFactory: AppCheckProviderFactory = AppCheckDebugProviderFactory()
        #else
        let providerFactory: AppCheckProviderFactory = AppAttestProviderFactory()
        #endif
        AppCheck.setAppCheckProviderFactory(providerFactory)

        FirebaseApp.configure()

        // Kotlin側（RemoteAnalysisRunner.appCheckTokenProvider）が呼ぶcompletion形式の
        // ブリッジに、実際のトークン取得処理を配線する（IosFileImportBridgeと同じ
        // 「Swift initでハンドラを代入する」パターン。AppCheckTokenBridge.kt参照）。
        AppCheckTokenBridge.shared.tokenHandler = { completion in
            AppCheck.appCheck().token(forcingRefresh: false) { token, _ in
                completion(token?.token)
            }
        }
    }
}
