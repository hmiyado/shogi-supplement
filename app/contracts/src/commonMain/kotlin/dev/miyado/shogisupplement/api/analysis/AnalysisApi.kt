package dev.miyado.shogisupplement.api.analysis

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PositionAnalysisPurpose {
    @SerialName("drill") DRILL,
}

/**
 * `POST /v1/analyses` のリクエストボディ。
 * moves_usi（1局まるごと）と sfen+moves（単発局面）のどちらか一方を受け付ける。
 *
 * @property multiPv 候補手の本数。省略時はサーバー既定（解析の不変条件）。
 *   1局まるごとの解析では指定できない（保存する解析結果の条件を動かさないため）。
 */
@Serializable
data class AnalysisRequest(
    @SerialName("moves_usi") val movesUsi: List<String>? = null,
    val sfen: String? = null,
    val moves: List<String>? = null,
    @SerialName("multi_pv") val multiPv: Int? = null,
    /** 保存済みの同一棋譜を、ユーザー要求として再解析する。 */
    @SerialName("force_reanalysis") val forceReanalysis: Boolean = false,
    /** 再送時に同じジョブを再利用するための、1回の再解析要求に固有なID。 */
    @SerialName("request_id") val requestId: String? = null,
    /** 省略は従来の単発検討。ドリルはサーバー側で解析の不変条件へ固定する。 */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val purpose: PositionAnalysisPurpose? = null,
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
    /** 表示用の短い条件名。旧workerの結果では空文字になり得る。 */
    @SerialName("condition_name") val conditionName: String = "",
)

/**
 * 解析結果を画面・ログで識別するための短い条件名。
 * 詳細な値は [EngineMetaJson] の各フィールドを正本とし、この名前を判定キーには使わない。
 */
fun engineConditionName(
    engineRev: String,
    evalSha256: String,
    nodes: Int,
    threads: Int,
    multiPv: Int,
    usiHash: Int,
    fvScale: Int,
): String {
    val engine = engineRev.substringAfterLast('@').takeLast(12).ifBlank { "unknown" }
    val eval = evalSha256.take(8).ifBlank { "unknown" }
    return "$engine-$eval-n$nodes-t$threads-p$multiPv-h$usiHash-f$fvScale"
}

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
