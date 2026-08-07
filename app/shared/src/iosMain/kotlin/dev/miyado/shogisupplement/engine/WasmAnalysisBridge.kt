package dev.miyado.shogisupplement.engine

/**
 * iOS WASM解析のSwift⇄Kotlin橋渡し。
 *
 * WKWebViewの生成・読み込み・JS実行・WKScriptMessageHandlerでの受信は Swift 側
 * （iosApp/iosApp/WasmAnalysisHost.swift）が担う。Kotlin/Native ⇄ Swift の境界は
 * [dev.miyado.shogisupplement.ui.AppCheckTokenBridge]・
 * [dev.miyado.shogisupplement.ui.IosFileImportBridge] と同じく「プレーンな関数呼び出し」と
 * 「クロージャ型プロパティへの代入」のみで構成する。
 *
 * 起動時の配線: `WasmAnalysisHost.shared` の init（iosAppApp.swift から起動時に一度参照される）が
 * [startHandler]/[cancelHandler] へ実処理のクロージャを代入する。
 *
 * runIdによる正当性確認: 1回の [WasmAnalysisRunner.analyzeGame] 呼び出しが1つのrunIdを持つ。
 * Swift側は新しい実行を始めるたびに前のWKWebViewを破棄するが、破棄中のWorkerが後から
 * メッセージを送ってくる余地が完全にゼロとは言い切れないため、[onPosition]/[onDone]/[onError] は
 * 渡された runId が現在アクティブな実行と一致する場合のみ処理する（一致しなければ無視）。
 */
object WasmAnalysisBridge {

    /**
     * Swift側（WasmAnalysisHost の init）が起動時に代入する、「WKWebViewで解析を開始する」
     * 実処理へのクロージャ。
     * @param runId 呼び出し元（[WasmAnalysisRunner]）が発行する一意な実行ID
     * @param movesJson 棋譜のUSI手列をJSON配列文字列化したもの
     * @param assetBaseUrl WASMエンジン資産（VERSION・wasm本体・評価関数）のベースURL（絶対URL）
     */
    var startHandler: ((runId: String, movesJson: String, assetBaseUrl: String) -> Unit)? = null

    /** Swift側が起動時に代入する、「進行中の解析を中断する」実処理へのクロージャ。 */
    var cancelHandler: ((runId: String) -> Unit)? = null

    private var activeRunId: String? = null
    private var onPositionCallback: ((ply: Int, pvs: List<PvInfo>) -> Unit)? = null
    private var onDoneCallback: (() -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null

    /** [WasmAnalysisRunner] 側: 新しい実行のコールバックを登録する。 */
    internal fun beginRun(
        runId: String,
        onPosition: (ply: Int, pvs: List<PvInfo>) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit,
    ) {
        activeRunId = runId
        onPositionCallback = onPosition
        onDoneCallback = onDone
        onErrorCallback = onError
    }

    /** [WasmAnalysisRunner] 側: [runId] がアクティブな実行と一致する場合のみ状態をクリアする。 */
    internal fun endRun(runId: String) {
        if (activeRunId != runId) return
        activeRunId = null
        onPositionCallback = null
        onDoneCallback = null
        onErrorCallback = null
    }

    /**
     * Swift側: 局面の解析結果を1件通知する。
     * @param resultJson analysis-worker.js の "position" メッセージの result フィールドを
     *   JSON文字列化したもの（[parseWasmPositionResult] が解釈する）
     */
    fun onPosition(runId: String, resultJson: String) {
        if (runId != activeRunId) return
        val parsed = runCatching { parseWasmPositionResult(resultJson) }.getOrNull() ?: return
        onPositionCallback?.invoke(parsed.ply, parsed.pvs)
    }

    /** Swift側: 全局面の解析が完了した（analysis-worker.js の "done"）。 */
    fun onDone(runId: String) {
        if (runId != activeRunId) return
        val callback = onDoneCallback
        endRun(runId)
        callback?.invoke()
    }

    /** Swift側: 解析が失敗した（資産取得失敗・ページ読み込み失敗・WKWebViewプロセス終了等）。 */
    fun onError(runId: String, message: String) {
        if (runId != activeRunId) return
        val callback = onErrorCallback
        endRun(runId)
        callback?.invoke(message)
    }
}
