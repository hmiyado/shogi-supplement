import SharedUi
import UIKit
import WebKit

/// iOS端末内WKWebView×WASM版やねうら王による解析のUIKit/WebKit側実装。
///
/// Compose（`WasmAnalysisRunner`。:shared iosMain）は WKWebView を直接扱えない
/// （KifFilePickerCoordinator と同じ理由）ため、UIKit/WebKit実装はSwift側に置き、
/// `SharedUi` の `WasmAnalysisBridge`（Kotlin object）経由でCompose側へブリッジする。
/// 境界はプレーンな関数呼び出し／クロージャ代入のみ（AppCheckTokenBridge・
/// IosFileImportBridgeと同じ方針。WasmAnalysisBridge.kt KDoc参照）。
///
/// Engineless構成でも動く（この実装はWebKit/UIKitのみに依存し、cinterop経由の
/// in-processエンジンとは無関係。SWIFT_ACTIVE_COMPILATION_CONDITIONS による
/// `#if !ENGINELESS` 等のガードを一切使わない）。
///
/// WKWebViewはユーザーに見せる必要が無いため、キーウィンドウへほぼ透明（alpha 0.01）な
/// 1x1ptのサブビューとして追加する。
/// Why not 完全に非表示(isHidden=true)のまま画面外に置く: 一部iOSバージョンでは
/// ビューヒエラルキーに属さない/非表示のWKWebViewはWorker実行が不安定になることが
/// あるため、実在するが実質不可視という状態を保つ。
final class WasmAnalysisHost: NSObject {
    static let shared = WasmAnalysisHost()

    private var currentWebView: WKWebView?
    private var activeRunId: String?
    private var isPageReady = false
    private var pendingRun: (movesJson: String, assetBaseUrl: String)?

    private override init() {
        super.init()
        WasmAnalysisBridge.shared.startHandler = { [weak self] runId, movesJson, assetBaseUrl in
            // WasmAnalysisRunner（Kotlin）はIOディスパッチャから呼ぶため、WKWebView操作は
            // 必ずメインスレッドへホップしてから行う。
            DispatchQueue.main.async {
                self?.start(runId: runId, movesJson: movesJson, assetBaseUrl: assetBaseUrl)
            }
        }
        WasmAnalysisBridge.shared.cancelHandler = { [weak self] runId in
            DispatchQueue.main.async {
                self?.cancel(runId: runId)
            }
        }
    }

    private func start(runId: String, movesJson: String, assetBaseUrl: String) {
        // 前回の実行がまだ残っていれば（通常は起きない。起きるのは前回がキャンセル漏れの時のみ）
        // 道連れで破棄してから新規に始める。1回の解析実行につきWKWebViewは1本。
        teardownWebView()

        activeRunId = runId
        isPageReady = false
        pendingRun = (movesJson, assetBaseUrl)

        let controller = WKUserContentController()
        controller.add(self, name: Self.messageHandlerName)
        let config = WKWebViewConfiguration()
        config.userContentController = controller

        let webView = WKWebView(frame: CGRect(x: 0, y: 0, width: 1, height: 1), configuration: config)
        webView.navigationDelegate = self
        webView.alpha = 0.01
        currentWebView = webView

        if let window = Self.keyWindow() {
            window.addSubview(webView)
        }

        webView.load(URLRequest(url: Self.hostPageURL))
    }

    private func cancel(runId: String) {
        guard runId == activeRunId else { return }
        currentWebView?.evaluateJavaScript("window.__cancelAnalysis && window.__cancelAnalysis();")
        teardownWebView()
    }

    private func handleReady() {
        isPageReady = true
        guard let pending = pendingRun, let webView = currentWebView, let runId = activeRunId else { return }
        pendingRun = nil
        webView.callAsyncJavaScript(
            "window.__startAnalysis(movesJson, assetBaseUrl);",
            arguments: ["movesJson": pending.movesJson, "assetBaseUrl": pending.assetBaseUrl],
            in: nil,
            in: .page,
        ) { [weak self] result in
            if case .failure(let error) = result {
                self?.finishWithError(runId: runId, message: "解析開始に失敗: \(error.localizedDescription)")
            }
        }
    }

    private func handlePosition(_ dict: [String: Any]) {
        guard let runId = activeRunId, let result = dict["result"] else { return }
        guard JSONSerialization.isValidJSONObject(result),
              let data = try? JSONSerialization.data(withJSONObject: result),
              let json = String(data: data, encoding: .utf8) else { return }
        WasmAnalysisBridge.shared.onPosition(runId: runId, resultJson: json)
    }

    private func handleDone() {
        guard let runId = activeRunId else { return }
        teardownWebView()
        WasmAnalysisBridge.shared.onDone(runId: runId)
    }

    private func handleError(_ dict: [String: Any]) {
        guard let runId = activeRunId else { return }
        let message = dict["message"] as? String ?? "unknown wasm host error"
        finishWithError(runId: runId, message: message)
    }

    private func finishWithError(runId: String, message: String) {
        guard runId == activeRunId else { return }
        teardownWebView()
        WasmAnalysisBridge.shared.onError(runId: runId, message: message)
    }

    private func teardownWebView() {
        currentWebView?.configuration.userContentController.removeScriptMessageHandler(forName: Self.messageHandlerName)
        currentWebView?.stopLoading()
        currentWebView?.removeFromSuperview()
        currentWebView = nil
        activeRunId = nil
        isPageReady = false
        pendingRun = nil
    }

    private static let messageHandlerName = "wasmAnalysis"

    /// ホストページのURL。DEBUGビルドに限り環境変数で差し替え可能
    /// （ホストページだけローカル配信に差し替え、エンジン資産(assetBaseUrl)は
    /// 本番配信のまま検証するためのフック）。
    private static var hostPageURL: URL {
        #if DEBUG
        if let override = ProcessInfo.processInfo.environment["WASM_ANALYSIS_HOST_URL_OVERRIDE"],
           let url = URL(string: override) {
            return url
        }
        #endif
        return URL(string: "https://shogi-supplement.miyado.dev/kento/wasm-analysis-host.html")!
    }

    private static func keyWindow() -> UIWindow? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first(where: { $0.isKeyWindow })
    }
}

// MARK: - WKScriptMessageHandler

extension WasmAnalysisHost: WKScriptMessageHandler {
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        guard let dict = message.body as? [String: Any], let type = dict["type"] as? String else { return }
        switch type {
        case "ready":
            handleReady()
        case "position":
            handlePosition(dict)
        case "done":
            handleDone()
        case "error", "page-error", "worker-error":
            handleError(dict)
        default:
            break // 前方互換: 未知のtypeは無視する。
        }
    }
}

// MARK: - WKNavigationDelegate

extension WasmAnalysisHost: WKNavigationDelegate {
    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        guard webView === currentWebView, let runId = activeRunId else { return }
        finishWithError(runId: runId, message: "ページ読み込み失敗: \(error.localizedDescription)")
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        guard webView === currentWebView, let runId = activeRunId else { return }
        finishWithError(runId: runId, message: "ページ読み込み失敗(provisional): \(error.localizedDescription)")
    }

    /// WebContentプロセスが（メモリ逼迫等で）強制終了した場合。実機検証では2並列時に
    /// 約900MB近くまで使う（並列数を2固定にしている理由そのもの）ため、低メモリ機では
    /// 起こりうる。ここを実装しないと継続（Kotlin側のsuspendCancellableCoroutine）が
    /// 二度と再開せず解析が無限に固まる。
    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        guard webView === currentWebView, let runId = activeRunId else { return }
        finishWithError(runId: runId, message: "WKWebViewのコンテンツプロセスが終了しました")
    }
}
