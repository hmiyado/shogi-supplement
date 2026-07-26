package dev.miyado.shogisupplement.api.analysis

import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.engine.PvInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// `POST /v1/analyses`（server/worker）のワイヤ形式。依存方向はサーバー→:sharedなので、
// サーバー・クライアント双方から参照できるようこの共通DTOを:sharedに置く
// （逆にサーバー固有の型を:sharedから参照することはできない）。
// サーバー側の実装は app/server/worker の Routes.kt（`post("/v1/analyses")`）。

/**
 * `POST /v1/analyses` のリクエストボディ。
 * moves_usi（1局まるごと）と sfen+moves（単発局面）のどちらか一方を受け付ける。
 */
@Serializable
data class AnalysisRequest(
    @SerialName("moves_usi") val movesUsi: List<String>? = null,
    val sfen: String? = null,
    val moves: List<String>? = null,
)

@Serializable
data class ScoreJson(val type: String, val value: Int)

fun Score.toJson(): ScoreJson = when (this) {
    is Score.Cp -> ScoreJson(type = "cp", value = value)
    is Score.Mate -> ScoreJson(type = "mate", value = plies)
}

fun ScoreJson.toScore(): Score = when (type) {
    "mate" -> Score.Mate(value)
    else -> Score.Cp(value)
}

@Serializable
data class PvInfoJson(
    val multipv: Int,
    val score: ScoreJson,
    val pv: List<String>,
    val nodes: Long,
)

fun PvInfo.toJson(): PvInfoJson = PvInfoJson(multipv = multipv, score = score.toJson(), pv = pv, nodes = nodes)

fun PvInfoJson.toPvInfo(): PvInfo = PvInfo(multipv = multipv, score = score.toScore(), pv = pv, nodes = nodes)

/** エンジン来歴＋解析条件（不変条件）の記録。 */
@Serializable
data class EngineMetaJson(
    @SerialName("engine_rev") val engineRev: String,
    @SerialName("eval_sha256") val evalSha256: String,
    val nodes: Int,
    val threads: Int,
    @SerialName("multi_pv") val multiPv: Int,
    @SerialName("usi_hash") val usiHash: Int,
    @SerialName("fv_scale") val fvScale: Int,
)

/** NDJSON最終行。 */
@Serializable
data class AnalysisResultJson(
    val result: List<List<PvInfoJson>>,
    @SerialName("engine_meta") val engineMeta: EngineMetaJson,
)

/** NDJSON進捗行。 */
@Serializable
data class ProgressJson(val progress: Int, val total: Int)

/** NDJSON/JSONエラー行・エラー応答共通。 */
@Serializable
data class ErrorJson(val error: String)

/** 429応答本文（翌日リセット時刻つき）。 */
@Serializable
data class QuotaExceededJson(
    val error: String = "quota_exceeded",
    @SerialName("reset_at") val resetAt: String,
)
