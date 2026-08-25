package dev.miyado.shogisupplement.engine

/**
 * iOS対話的単発局面解析（検討モード・読み筋延長・ドリル二次判定）のSwift⇄Kotlin橋渡し。
 *
 * バッチ解析用の[WasmAnalysisBridge]と役割は同型だが、ホスト（常駐WKWebView。
 * iosApp/iosApp/WasmStudyHost.swift）・プロトコル（1局全体ではなく1局面のみ）が別のため
 * 独立したブリッジにする。境界は[WasmAnalysisBridge]と同じく「プレーンな関数呼び出し」と
 * 「クロージャ型プロパティへの代入」のみ。
 *
 * requestIdによる正当性確認は[WasmAnalysisBridge]と同じ理由（クラスKDoc参照）。
 */
object WasmStudyBridge {

    /** 単発局面解析を開始する。 @param requestId リクエストID。 @param baseSfenArg position引数。 @param movesJson 追加手列のJSON。 @return 受理できたか。 */
    var analyzeHandler: ((requestId: String, baseSfenArg: String, movesJson: String) -> Boolean)? = null

    /** WASMバイナリとWebViewページが準備済みかを返す。未準備ならfalseで、解析開始はfail-fastする。 */
    var localReadyProvider: (() -> Boolean)? = null

    private var activeRequestId: String? = null
    private var onResultCallback: ((String) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null

    /** [WasmStudyEngine] 側: 新しいリクエストのコールバックを登録する。 */
    internal fun beginRequest(
        requestId: String,
        onResult: (resultJson: String) -> Unit,
        onError: (String) -> Unit,
    ) {
        activeRequestId = requestId
        onResultCallback = onResult
        onErrorCallback = onError
    }

    /** [WasmStudyEngine] 側: [requestId] がアクティブなリクエストと一致する場合のみ状態をクリアする。 */
    internal fun endRequest(requestId: String) {
        if (activeRequestId != requestId) return
        activeRequestId = null
        onResultCallback = null
        onErrorCallback = null
    }

    /**
     * Swift側: 局面の解析結果を通知する。
     * @param resultJson analysis-worker.js と同形の result フィールドをJSON文字列化したもの
     *   （[parseWasmPositionResult] が解釈する）
     */
    fun onResult(requestId: String, resultJson: String) {
        if (requestId != activeRequestId) return
        val callback = onResultCallback
        endRequest(requestId)
        callback?.invoke(resultJson)
    }

    /** Swift側: 解析が失敗した（ホスト未準備・Workerエラー・WASMバイナリ解決失敗等）。 */
    fun onError(requestId: String, message: String) {
        if (requestId != activeRequestId) return
        val callback = onErrorCallback
        endRequest(requestId)
        callback?.invoke(message)
    }
}
