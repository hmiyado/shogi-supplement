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

    /**
     * Swift側（WasmStudyHost）が起動時に代入する、単発局面解析の開始要求ハンドラ。
     *
     * @param requestId 呼び出し元（[WasmStudyEngine]）が発行する一意なリクエストID
     * @param baseSfenArg USIの `position` コマンドへそのまま連結する文字列
     *   （`"sfen <SFEN文字列>"` 形式。呼び出し元がネイティブエンジン実装と同じ組み立てを行う）
     * @param movesJson [baseSfenArg] の局面からさらに進める USI 手列をJSON配列文字列化したもの
     * @return 受理できたら true（後で[onResult]/[onError]のどちらかが呼ばれる）。
     *   ホスト未初期化・WASMバイナリ未準備・別リクエストが処理中などで即座に受理できない場合は false
     *   （呼び出し側はその場でサーバー解析へ切り替える。数十秒待たせないためのfail-fast）。
     */
    var analyzeHandler: ((requestId: String, baseSfenArg: String, movesJson: String) -> Boolean)? = null

    /**
     * Swift側（WasmStudyHost）が起動時に代入する、ローカルWASM解析が使える見込みかの判定。
     *
     * [analyzeHandler] は常に登録済み（起動時に一度だけ代入される）なため「登録の有無」は
     * 見込み判定に使えない。実体はWASMバイナリキャッシュの準備状態とWKWebViewページの読み込み状態の
     * 両方（WASMバイナリready かつ ページready）——[analyzeHandler] の受理条件と同じところまで
     * 見て true を返す。WASMバイナリreadyだがページ未readyの間は、false を返しつつページ読み込みを
     * 裏で開始する（別途の明示的な起動経路を持たず、false を返している間は繰り返し
     * 評価され続けるという前提のもとでこの評価自体を起点にする）。true でも
     * 実際の呼び出しが fail-fast で false へ倒れる可能性はなお残る（他リクエストが
     * ビジー中等。[WasmStudyEngine] のKDoc参照）。
     */
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
