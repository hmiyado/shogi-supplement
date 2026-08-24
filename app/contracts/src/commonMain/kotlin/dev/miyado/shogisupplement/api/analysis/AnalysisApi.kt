package dev.miyado.shogisupplement.api.analysis

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

@Serializable
data class PvInfoJson(
    val multipv: Int,
    val score: ScoreJson,
    val pv: List<String>,
    val nodes: Long,
)

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

/**
 * NDJSON局面結果行（プログレッシブ解析表示向け）。局面の解析が完了するたびに送る中間結果で、
 * 最終行（[AnalysisResultJson]）とは独立に送る（両方を送る。並列ワーカーの完了順に依存するため
 * ply順の到着は保証しない）。
 *
 * Why not クライアントのバージョンを見て送信を出し分ける: NDJSON行の未知トップレベルキーは
 * 受信側が無条件にスキップする契約になっているため、出し分けを持ち込む必要が無い。
 */
@Serializable
data class PositionResultJson(val position: PositionPayloadJson)

@Serializable
data class PositionPayloadJson(val ply: Int, val pvs: List<PvInfoJson>)

/** NDJSON/JSONエラー行・エラー応答共通。 */
@Serializable
data class ErrorJson(val error: String)

/** 429応答本文（翌日リセット時刻つき）。 */
@Serializable
data class QuotaExceededJson(
    val error: String = "quota_exceeded",
    @SerialName("reset_at") val resetAt: String,
)
