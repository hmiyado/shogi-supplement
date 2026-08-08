import SharedUi
import UIKit
import WebKit

/// 検討モード・読み筋延長・ドリル二次判定向け、対話的単発局面解析の常駐WKWebViewホスト。
///
/// バッチ解析用の `WasmAnalysisHost`（1回の解析実行につきWKWebViewを1本生成し使い捨てる）とは
/// 別クラスにする: 対話的解析は反復のたびにWKWebView生成・ページ読み込みのコストを払わないことが
/// 目的そのもの（初回利用時に生成し、アプリ存命中は保持する）。境界・`SharedUi`
/// （`WasmStudyBridge`）経由で橋渡しする。
///
/// エンジン資産・ホストページはローカルキャッシュ（`KentoAssetCache`）から
/// `WKURLSchemeHandler` で配信する（ネットワーク不要・オフライン検討が成立する）。
/// 資産が準備できていなければ [analyzeHandler] はその場で false を返し（fail-fast）、
/// 準備が済んでいなければ待たせず即座に失敗を返す（fail-fast）。
final class WasmStudyHost: NSObject {
    static let shared = WasmStudyHost()

    private var webView: WKWebView?
    private var loadedRootURL: URL?
    private var isPageReady = false
    private var busyRequestId: String?

    private override init() {
        super.init()
        WasmStudyBridge.shared.analyzeHandler = { [weak self] requestId, baseSfenArg, movesJson in
            self?.beginAnalyze(requestId: requestId, baseSfenArg: baseSfenArg, movesJson: movesJson)
                ?? KotlinBoolean(bool: false)
        }
        // 検討モードの自動発火（StudyController.maybeAutoAnalyze）向け見込み判定。
        // KentoAssetCache.state はどのスレッドからでも読める（NSLock保護）ため、
        // メインスレッド外（ioDispatcher）からの呼び出しでも安全。
        WasmStudyBridge.shared.localReadyProvider = {
            if case .ready = KentoAssetCache.shared.state {
                return KotlinBoolean(bool: true)
            }
            return KotlinBoolean(bool: false)
        }
    }

    /// 受理判定〜JS呼び出しの発火までを1つの `DispatchQueue.main.sync` の中で行う。
    /// [WasmStudyEngine] は別スレッド（ioDispatcher）から呼ぶため、判定と発火の間に
    /// 別リクエストが割り込んで [busyRequestId] を二重に埋めることを防ぐ
    /// （メインスレッドで直列化する。同期実行にする狙いは、呼び出し元へ受理可否を
    /// その場で返せるようにするため——非同期にすると受理可否の通知に別経路が要る）。
    private func beginAnalyze(requestId: String, baseSfenArg: String, movesJson: String) -> KotlinBoolean {
        var accepted = false
        DispatchQueue.main.sync {
            guard busyRequestId == nil else { return }
            guard case .ready(let rootURL, _) = KentoAssetCache.shared.state else { return }
            guard let webView = ensureWebView(rootURL: rootURL), isPageReady else { return }

            busyRequestId = requestId
            accepted = true
            webView.callAsyncJavaScript(
                "window.__analyzePosition(requestId, baseSfenArg, movesJson);",
                arguments: ["requestId": requestId, "baseSfenArg": baseSfenArg, "movesJson": movesJson],
                in: nil,
                in: .page
            ) { [weak self] result in
                if case .failure(let error) = result {
                    self?.finish(requestId: requestId) {
                        WasmStudyBridge.shared.onError(
                            requestId: requestId, message: "対話的解析の呼び出しに失敗: \(error.localizedDescription)"
                        )
                    }
                }
            }
        }
        return KotlinBoolean(bool: accepted)
    }

    /// 常駐WebViewを（無ければ）生成する。[rootURL] が前回と異なる場合
    /// （資産バージョン更新でキャッシュが差し替わった場合）は作り直す。
    private func ensureWebView(rootURL: URL) -> WKWebView? {
        if let webView, loadedRootURL == rootURL {
            return webView
        }
        teardownWebView()

        let controller = WKUserContentController()
        controller.add(self, name: Self.messageHandlerName)
        let config = WKWebViewConfiguration()
        config.userContentController = controller
        config.setURLSchemeHandler(KentoLocalSchemeHandler(rootURL: rootURL), forURLScheme: Self.scheme)

        let newWebView = WKWebView(frame: CGRect(x: 0, y: 0, width: 1, height: 1), configuration: config)
        newWebView.navigationDelegate = self
        newWebView.alpha = 0.01
        webView = newWebView
        loadedRootURL = rootURL
        isPageReady = false

        if let window = Self.keyWindow() {
            window.addSubview(newWebView)
        }

        var comps = URLComponents()
        comps.scheme = Self.scheme
        comps.host = "local"
        comps.path = "/kento/wasm-analysis-host.html"
        newWebView.load(URLRequest(url: comps.url!))
        return newWebView
    }

    private func handleStudyReady() {
        isPageReady = true
        // evaluateJavaScript(_:)は固定のJS式のみでKotlin側のようなarguments辞書を取れないため、
        // assetBaseUrl（JSON配列等を含まない単純な絶対URL文字列）を直接埋め込む。
        let assetBaseUrl = "\(Self.scheme)://local/kento-assets"
        webView?.evaluateJavaScript("window.__initStudy(\"\(assetBaseUrl)\");")
    }

    private func handleResult(_ dict: [String: Any]) {
        guard let requestId = dict["requestId"] as? String, let result = dict["result"] else { return }
        guard JSONSerialization.isValidJSONObject(result),
              let data = try? JSONSerialization.data(withJSONObject: result),
              let json = String(data: data, encoding: .utf8) else { return }
        finish(requestId: requestId) {
            WasmStudyBridge.shared.onResult(requestId: requestId, resultJson: json)
        }
    }

    private func handleError(_ dict: [String: Any]) {
        guard let requestId = dict["requestId"] as? String else { return }
        let message = dict["message"] as? String ?? "unknown wasm study error"
        finish(requestId: requestId) {
            WasmStudyBridge.shared.onError(requestId: requestId, message: message)
        }
    }

    /// [requestId] がビジー状態と一致する場合のみクリアし、[onFinished] を実行する
    /// （一致しなければ既に別経路で終了済み——例えばWebContentプロセス死での一括破棄——なので無視）。
    private func finish(requestId: String, onFinished: () -> Void) {
        guard requestId == busyRequestId else { return }
        busyRequestId = nil
        onFinished()
    }

    private func teardownWebView() {
        webView?.configuration.userContentController.removeScriptMessageHandler(forName: Self.messageHandlerName)
        webView?.stopLoading()
        webView?.removeFromSuperview()
        webView = nil
        loadedRootURL = nil
        isPageReady = false
        if let requestId = busyRequestId {
            finish(requestId: requestId) {
                WasmStudyBridge.shared.onError(requestId: requestId, message: "常駐WKWebViewホストが破棄されました")
            }
        }
    }

    private static let scheme = "kentolocal"
    private static let messageHandlerName = "wasmStudy"

    private static func keyWindow() -> UIWindow? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first(where: { $0.isKeyWindow })
    }
}

// MARK: - WKScriptMessageHandler

extension WasmStudyHost: WKScriptMessageHandler {
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        guard let dict = message.body as? [String: Any], let type = dict["type"] as? String else { return }
        switch type {
        case "study-ready":
            handleStudyReady()
        case "study-result":
            handleResult(dict)
        case "study-error", "study-page-error", "study-init-error":
            handleError(dict)
        default:
            break // 前方互換: 未知のtypeは無視する。
        }
    }
}

// MARK: - WKNavigationDelegate

extension WasmStudyHost: WKNavigationDelegate {
    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        guard webView === self.webView else { return }
        teardownWebView()
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        guard webView === self.webView else { return }
        teardownWebView()
    }

    /// メモリ圧でWebContentプロセスが落ちたら常駐ホストを破棄し、次回利用時に再生成する。
    /// 常駐ホストを破棄し、次回利用時に再生成する（[ensureWebView] が入り口）。
    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        guard webView === self.webView else { return }
        teardownWebView()
    }
}

/// ローカル資産キャッシュ（`KentoAssetCache`）配下のディレクトリを `kentolocal://local/...` で
/// 配信する（WKWebViewはシステムのHTTP(S)キャッシュ・CORSと無関係にディスク上のファイルを
/// そのまま返せる）。
private final class KentoLocalSchemeHandler: NSObject, WKURLSchemeHandler {
    private let rootURL: URL

    init(rootURL: URL) {
        self.rootURL = rootURL
    }

    func webView(_ webView: WKWebView, start urlSchemeTask: WKURLSchemeTask) {
        guard let requestURL = urlSchemeTask.request.url else {
            urlSchemeTask.didFailWithError(URLError(.badURL))
            return
        }
        let fileURL = rootURL.appendingPathComponent(requestURL.path)

        guard let data = try? Data(contentsOf: fileURL, options: .mappedIfSafe) else {
            urlSchemeTask.didFailWithError(URLError(.fileDoesNotExist))
            return
        }

        // fetch()のresp.okはHTTPレスポンスのstatusCodeで判定される
        // （素のURLResponseだとstatusCodeが常に0扱いになりresp.okがfalseになる）。
        let response = HTTPURLResponse(
            url: requestURL,
            statusCode: 200,
            httpVersion: "HTTP/1.1",
            headerFields: [
                "Content-Type": Self.mimeType(for: fileURL.pathExtension),
                "Content-Length": String(data.count),
            ]
        )!
        urlSchemeTask.didReceive(response)
        urlSchemeTask.didReceive(data)
        urlSchemeTask.didFinish()
    }

    func webView(_ webView: WKWebView, stop urlSchemeTask: WKURLSchemeTask) {
        // 読み込みは同期的にdidFinishまで完了させているため、中断すべき非同期処理はない。
    }

    private static func mimeType(for ext: String) -> String {
        switch ext.lowercased() {
        case "html": return "text/html"
        case "js": return "application/javascript"
        case "wasm": return "application/wasm"
        case "bin": return "application/octet-stream"
        default: return "application/octet-stream"
        }
    }
}
