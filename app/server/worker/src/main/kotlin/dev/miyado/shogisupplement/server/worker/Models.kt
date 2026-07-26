package dev.miyado.shogisupplement.server.worker

import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.engine.PvInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * POST /v1/analyses のリクエストボディ。
 *
 * moves_usi（1局まるごと・局面0..Nを順次解析）と sfen+moves（単発局面。ドリルの
 * 読み筋延長用）のどちらか一方を受け付ける。
 */
@Serializable
data class AnalysisRequest(
    @SerialName("moves_usi") val movesUsi: List<String>? = null,
    val sfen: String? = null,
    val moves: List<String>? = null,
)

/** [AnalysisRequest] をエンジン入力へ正規化した形。 */
sealed class EngineInput {
    /** 冪等キー（moves_hash）算出用の元文字列。 */
    abstract val hashSeed: String

    data class Game(val movesUsi: List<String>) : EngineInput() {
        override val hashSeed: String get() = movesUsi.joinToString(" ")
    }

    data class Position(val sfen: String, val moves: List<String>) : EngineInput() {
        override val hashSeed: String get() = "$sfen|${moves.joinToString(" ")}"
    }
}

/**
 * [AnalysisRequest] を [EngineInput] へ変換する。
 * moves_usi が優先。どちらも無ければ null（呼び出し側は400として扱う）。
 */
fun AnalysisRequest.toEngineInput(): EngineInput? = when {
    movesUsi != null -> EngineInput.Game(movesUsi)
    sfen != null -> EngineInput.Position(sfen, moves ?: emptyList())
    else -> null
}

@Serializable
data class ScoreJson(val type: String, val value: Int)

fun Score.toJson(): ScoreJson = when (this) {
    is Score.Cp -> ScoreJson(type = "cp", value = value)
    is Score.Mate -> ScoreJson(type = "mate", value = plies)
}

@Serializable
data class PvInfoJson(
    val multipv: Int,
    val score: ScoreJson,
    val pv: List<String>,
    val nodes: Long,
)

fun PvInfo.toJson(): PvInfoJson = PvInfoJson(multipv = multipv, score = score.toJson(), pv = pv, nodes = nodes)

/** engine_meta: エンジン来歴＋解析条件（不変条件の記録。uploaded_games.coef_versionと同じ思想）。 */
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

/** NDJSON進捗行（局面10ごと）。 */
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
