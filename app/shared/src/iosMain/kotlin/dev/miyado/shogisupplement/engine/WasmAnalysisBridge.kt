package dev.miyado.shogisupplement.engine

/**
 * iOS WASM解析のSwift/Kotlin橋渡し。WebViewの処理はSwift側が担い、Kotlinとは関数とクロージャで接続する。
 * runIdが現在の実行と一致する結果だけを受け付け、破棄中Workerの遅延通知を無視する。
 */
object WasmAnalysisBridge {

    /** WKWebView解析を開始するクロージャ。 @param runId 実行ID。 @param movesJson USI手列のJSON。 @param assetBaseUrl WASM資産のベースURL。 */
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

    /** Swift側: 解析が失敗した（WASMバイナリ取得失敗・ページ読み込み失敗・WKWebViewプロセス終了等）。 */
    fun onError(runId: String, message: String) {
        if (runId != activeRunId) return
        val callback = onErrorCallback
        endRun(runId)
        callback?.invoke(message)
    }
}
