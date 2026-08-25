package dev.miyado.shogisupplement.engine

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSUUID

/** WKWebView上のWASMエンジンでGameAnalyzerを実装する。WebKitだけに依存するためengineless構成でも動作する。 */
class WasmAnalysisRunner : GameAnalyzer {

    override suspend fun analyzeGame(
        moves: List<String>,
        onPositionResult: ((ply: Int, pvs: List<PvInfo>) -> Unit)?,
        onProgress: ((done: Int, total: Int) -> Unit)?,
    ): List<List<PvInfo>> {
        val start = WasmAnalysisBridge.startHandler
            ?: throw WasmAnalysisException("WKWebViewホスト（WasmAnalysisHost）が未初期化です")

        val total = moves.size + 1
        val results = arrayOfNulls<List<PvInfo>>(total)
        var done = 0
        val runId = NSUUID().UUIDString()

        return suspendCancellableCoroutine { cont ->
            WasmAnalysisBridge.beginRun(
                runId = runId,
                onPosition = { ply, pvs ->
                    if (ply in results.indices) results[ply] = pvs
                    onPositionResult?.invoke(ply, pvs)
                    done += 1
                    onProgress?.invoke(done, total)
                },
                onDone = { cont.resume(results.map { it ?: emptyList() }) },
                onError = { message -> cont.resumeWithException(WasmAnalysisException(message)) },
            )
            cont.invokeOnCancellation {
                WasmAnalysisBridge.endRun(runId)
                WasmAnalysisBridge.cancelHandler?.invoke(runId)
            }
            start(runId, json.encodeToString(moves), ASSET_BASE_URL)
        }
    }

    companion object {
        /** エンジンWASM資産の本番配信先。Why notビルド時設定にしない: Pagesの本番配信だけを使うため。 */
        private const val ASSET_BASE_URL = "https://shogi-supplement.miyado.dev/kento-assets"
        private val json = Json
    }
}
