package dev.miyado.shogisupplement.server.worker

import dev.miyado.shogisupplement.api.analysis.AnalysisRequest
import dev.miyado.shogisupplement.engine.Engine

/** 解析リクエストの入力上限。超える入力は解析にもDBアクセスにも進ませない。 */
object AnalysisInputLimits {
    const val MAX_MOVES = 1024
    const val MAX_SFEN_LENGTH = 256
    const val MAX_BODY_BYTES = 64L * 1024

    /** 単発局面解析で受け付ける候補手の本数の上限。探索時間が本数に比例して伸びるため頭を抑える。 */
    const val MAX_MULTI_PV = Engine.STUDY_MULTI_PV
}

sealed class EngineInputResult {
    data class Valid(val input: EngineInput) : EngineInputResult()
    data class Invalid(val reason: String) : EngineInputResult()
}

private val USI_MOVE = Regex("[1-9][a-i][1-9][a-i]\\+?|[PLNSGBR]\\*[1-9][a-i]")
private val SFEN_RANK = Regex("[1-9plnsgkrbPLNSGKRB+]{1,18}")
private val SFEN_HAND = Regex("-|(?:[0-9]{0,2}[plnsgrbPLNSGRB]){1,14}")

private const val INVALID_SFEN = "sfen の形式が不正です"

private const val MULTI_PV_ON_GAME = "multi_pv は moves_usi と同時に指定できません"

/** AnalysisRequestを検証しつつEngineInputへ変換する。moves_usiを優先する。 */
fun AnalysisRequest.toEngineInput(): EngineInputResult {
    val movesUsi = movesUsi
    val sfen = sfen
    if (movesUsi != null) {
        // 1局まるごとの結果は保存して指標の元になるため、解析条件を動かさせない。
        val error = multiPv?.let { MULTI_PV_ON_GAME } ?: movesError(movesUsi, field = "moves_usi")
        return if (error != null) EngineInputResult.Invalid(error) else {
            EngineInputResult.Valid(EngineInput.Game(movesUsi))
        }
    }
    if (sfen != null) {
        val moves = moves ?: emptyList()
        val error = sfenError(sfen) ?: movesError(moves, field = "moves") ?: multiPvError(multiPv)
        return if (error != null) EngineInputResult.Invalid(error) else {
            EngineInputResult.Valid(EngineInput.Position(sfen, moves, multiPv ?: Engine.MULTI_PV))
        }
    }
    return EngineInputResult.Invalid("moves_usi または sfen のいずれかが必要です")
}

private fun multiPvError(multiPv: Int?): String? = when {
    multiPv == null -> null
    multiPv !in 1..AnalysisInputLimits.MAX_MULTI_PV ->
        "multi_pv は1以上${AnalysisInputLimits.MAX_MULTI_PV}以下である必要があります"
    else -> null
}

private fun movesError(moves: List<String>, field: String): String? = when {
    moves.size > AnalysisInputLimits.MAX_MOVES ->
        "$field の手数が上限（${AnalysisInputLimits.MAX_MOVES}手）を超えています"
    moves.any { !USI_MOVE.matches(it) } -> "$field に不正なUSI指し手が含まれています"
    else -> null
}

// 盤面の合法性までは見ない（詰将棋のような通常対局に現れない配置もエンジンは解析できるため）。
private fun sfenError(sfen: String): String? {
    if (sfen.length > AnalysisInputLimits.MAX_SFEN_LENGTH) {
        return "sfen が上限（${AnalysisInputLimits.MAX_SFEN_LENGTH}文字）を超えています"
    }
    val fields = sfen.split(" ")
    if (fields.size != 3 && fields.size != 4) return INVALID_SFEN
    val ranks = fields[0].split("/")
    if (ranks.size != 9 || ranks.any { !SFEN_RANK.matches(it) }) return INVALID_SFEN
    if (fields[1] != "b" && fields[1] != "w") return INVALID_SFEN
    if (!SFEN_HAND.matches(fields[2])) return INVALID_SFEN
    if (fields.size == 4 && fields[3].toIntOrNull() == null) return INVALID_SFEN
    return null
}
