package dev.miyado.shogisupplement.engine

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSUUID

/**
 * 端末内WKWebView×WASM版やねうら王による [GameAnalyzer] 実装。
 *
 * 実体はSwift側（iosApp/iosApp/WasmAnalysisHost.swift）がホストするWKWebViewで、
 * 2Workerで並列に解析するJS実装（docs/kento/webapp-bridge.js。局面ごとisready→usinewgameで
 * 置換表を切り、エンジンプロセスを温存したまま孤立解析と同等の決定性を保つ）をそのまま動かす。
 * 境界は [WasmAnalysisBridge] の「文字列とコールバック関数」のみ。
 *
 * Engineless構成（ストア版。cinterop経由のin-processエンジンを一切リンクしない）でも動く:
 * ここはWebKit（システムフレームワーク）とWKWebView内JSのみに依存し、
 * engine_wrapper（IosEngineHost.ENGINE_LINKED）とは無関係。
 *
 * 解析条件（400kノード/Threads=1/USI_Hash=128/MultiPV=2/FV_SCALE=20・局面ごとisready→
 * usinewgame）はdocs/kento/analysis-worker.jsが担保する（このクラスは変更しない）。
 */
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
        /**
         * WASMエンジン資産（VERSION・yaneuraou-*.wasm・nn.bin）の本番配信先。
         * docs/copy-kento-assets.sh が生成し、Pages（[docs/CNAME]のカスタムドメイン）配下へ
         * デプロイする（docs/kento-assets/。バージョン付きサブディレクトリの解決は
         * docs/kento/webapp-bridge.js の resolveAssetDirUrl が担う）。
         *
         * Why not ANALYSIS_BASE_URLのようにビルド時設定にする: サーバー解析ベースURLは
         * dev/prodで環境が分かれるが、Pages資産は本番配信のみ（開発用の別配信は無い）ため
         * 設定項目を増やす理由が無い。
         */
        private const val ASSET_BASE_URL = "https://shogi-supplement.miyado.dev/kento-assets"
        private val json = Json
    }
}
