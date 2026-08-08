package dev.miyado.shogisupplement.engine

import dev.miyado.shogisupplement.board.ShogiBoard
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSUUID

/**
 * iOS端末内WKWebView×WASM版やねうら王による、対話的単発局面解析
 * （検討モード・読み筋延長・ドリル二次判定）向け [Engine] 実装。
 *
 * 実体はSwift側（iosApp/iosApp/WasmStudyHost.swift）が保持する常駐WKWebView
 * （docs/kento/wasm-analysis-host.html の `window.__analyzePosition`）。バッチ解析用の
 * [WasmAnalysisRunner]/[WasmAnalysisBridge]（1局まるごと・実行ごとに2Workerを起こして破棄）
 * とはホスト・ブリッジ（[WasmStudyBridge]）を分ける設計判断: 単発局面はWorker1本の
 * スタンバイ方式で足り（メモリ約450MB）、2並列にする理由が無い。逆にバッチの2並列構成へ
 * 単発局面の出入りを混ぜると、常駐ホストのライフサイクルとバッチの実行ごと破棄が絡み合い
 * 複雑になるため、独立させたほうが単純になる。
 *
 * ネイティブエンジン実装（[UsiEngineInProcess.analyzeSfen]）と同じ `position sfen <SFEN>
 * [moves ...]` 形式でUSIコマンドを組み立てる（[analyzeSfen]参照。サーバー解析と結果を
 * 完全一致させる不変条件のため、フォーマットの食い違いは許されない）。
 *
 * [WasmStudyBridge.analyzeHandler] が未登録、または即座に受理できない
 * （ホスト未初期化・WASMバイナリ未準備・別リクエストが処理中）場合は即座に例外を投げる
 * （fail-fast。呼び出し側の合成——[FailoverEngine]——がサーバー経路へ即座に
 * 切り替えられるよう、WASMバイナリのダウンロード中などに数十秒待たせないため）。
 */
class WasmStudyEngine : Engine {

    override fun analyze(moves: List<String>, nodes: Int): List<PvInfo> =
        analyzeSfen(ShogiBoard().toSfen(), moves, nodes)

    // nodes: study-worker.js が本番不変条件のノード数を自前で固定するため受け取っても使わない
    // （[RemoteStudyEngine] と同じ理由）。
    override fun analyzeSfen(sfen: String, additionalMoves: List<String>, nodes: Int): List<PvInfo> {
        val start = WasmStudyBridge.analyzeHandler
            ?: throw WasmAnalysisException("常駐WKWebViewホスト（WasmStudyHost）が未初期化です")

        val requestId = NSUUID().UUIDString()
        val movesJson = json.encodeToString(additionalMoves)

        return runBlocking {
            suspendCancellableCoroutine { cont ->
                WasmStudyBridge.beginRequest(
                    requestId = requestId,
                    onResult = { resultJson ->
                        val parsed = runCatching { parseWasmPositionResult(resultJson) }
                        parsed.fold(
                            onSuccess = { cont.resume(it.pvs) },
                            onFailure = {
                                cont.resumeWithException(WasmAnalysisException("解析結果の解釈に失敗: ${it.message}"))
                            },
                        )
                    },
                    onError = { message -> cont.resumeWithException(WasmAnalysisException(message)) },
                )
                cont.invokeOnCancellation { WasmStudyBridge.endRequest(requestId) }

                val accepted = start(requestId, "sfen $sfen", movesJson)
                if (!accepted) {
                    WasmStudyBridge.endRequest(requestId)
                    cont.resumeWithException(WasmAnalysisException("対話的解析ホストが未準備です"))
                }
            }
        }
    }

    /** サーバー版（[RemoteStudyEngine]）と同じく、局の区切りという概念を持たない。 */
    override fun newGame() { /* no-op */ }

    /** ホストの生存期間はSwift側（WasmStudyHost）が管理するため、ここでは何もしない。 */
    override fun quit() { /* no-op */ }

    companion object {
        private val json = Json
    }
}
