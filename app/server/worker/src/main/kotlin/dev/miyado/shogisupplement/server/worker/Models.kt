package dev.miyado.shogisupplement.server.worker

import dev.miyado.shogisupplement.api.analysis.AnalysisRequest

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
fun AnalysisRequest.toEngineInput(): EngineInput? {
    val movesUsi = movesUsi
    val sfen = sfen
    return when {
        movesUsi != null -> EngineInput.Game(movesUsi)
        sfen != null -> EngineInput.Position(sfen, moves ?: emptyList())
        else -> null
    }
}
