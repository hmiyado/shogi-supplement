package dev.miyado.shogisupplement.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** エンジンの実行条件と成果物を識別する来歴。取得できない経路ではnullになる。 */
@Serializable
data class AnalysisEngineMeta(
    @SerialName("engine_rev") val engineRev: String,
    @SerialName("eval_sha256") val evalSha256: String,
    val nodes: Int,
    val threads: Int,
    @SerialName("multi_pv") val multiPv: Int,
    @SerialName("usi_hash") val usiHash: Int,
    @SerialName("fv_scale") val fvScale: Int,
)

/** 1局の全局面解析と、その解析に使った来歴。 */
data class GameAnalysisResult(
    val positions: List<List<PvInfo>>,
    val engineMeta: AnalysisEngineMeta? = null,
)

/**
 * 1局の全局面を解析して局面ごとの結果を返す契約。
 *
 * 実装は端末解析の [AnalysisRunner] とサーバー解析の [RemoteAnalysisRunner] の2つで、
 * どちらを渡しても [AnalysisOrchestrator] 以下（悪手判定・強さ推定・DB保存）は同じ経路を通る。
 * 解析条件（go nodes 400000 / Threads=1 / MultiPV=2 / FV_SCALE=20）は両実装で一致させてあり、
 * 同じ棋譜からは同じ結果が返る。
 */
interface GameAnalyzer {

    /** @param moves 棋譜のUSI手列。 @param onPositionResult 局面ごとの結果。 @param onProgress 進捗。 @return 局面順のMultiPV結果と解析来歴。 */
    suspend fun analyzeGame(
        moves: List<String>,
        onPositionResult: ((ply: Int, pvs: List<PvInfo>) -> Unit)? = null,
        onProgress: ((done: Int, total: Int) -> Unit)? = null,
    ): GameAnalysisResult
}
