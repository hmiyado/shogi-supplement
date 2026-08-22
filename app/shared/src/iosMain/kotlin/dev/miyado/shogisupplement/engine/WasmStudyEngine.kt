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
 * iOSの常駐WKWebViewで単発WASM解析を行うEngine実装。
 * バッチ解析とはホストを分離し、position形式をネイティブ実装と一致させる。
 * 未準備またはビジーなら即時に例外を返し、サーバー経路へ切り替えられるようにする。
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
