package dev.miyado.shogisupplement.server.worker

import dev.miyado.shogisupplement.api.analysis.AnalysisRequest

/** 解析リクエストの入力上限。超える入力は解析にもDBアクセスにも進ませない。 */
object AnalysisInputLimits {
    const val MAX_MOVES = 1024
    const val MAX_SFEN_LENGTH = 256
    const val MAX_BODY_BYTES = 64L * 1024
}

sealed class EngineInputResult {
    data class Valid(val input: EngineInput) : EngineInputResult()
    data class Invalid(val reason: String) : EngineInputResult()
}

private val USI_MOVE = Regex("[1-9][a-i][1-9][a-i]\\+?|[PLNSGBR]\\*[1-9][a-i]")
private val SFEN_RANK = Regex("[1-9plnsgkrbPLNSGKRB+]{1,18}")
private val SFEN_HAND = Regex("-|(?:[0-9]{0,2}[plnsgrbPLNSGRB]){1,14}")

private const val INVALID_SFEN = "sfen の形式が不正です"

/** AnalysisRequestを検証しつつEngineInputへ変換する。moves_usiを優先する。 */
fun AnalysisRequest.toEngineInput(): EngineInputResult {
    val movesUsi = movesUsi
    val sfen = sfen
    if (movesUsi != null) {
        val error = movesError(movesUsi, field = "moves_usi")
        return if (error != null) EngineInputResult.Invalid(error) else {
            EngineInputResult.Valid(EngineInput.Game(movesUsi))
        }
    }
    if (sfen != null) {
        val moves = moves ?: emptyList()
        val error = sfenError(sfen) ?: movesError(moves, field = "moves")
        return if (error != null) EngineInputResult.Invalid(error) else {
            EngineInputResult.Valid(EngineInput.Position(sfen, moves))
        }
    }
    return EngineInputResult.Invalid("moves_usi または sfen のいずれかが必要です")
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
