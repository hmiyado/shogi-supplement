package dev.miyado.shogisupplement.server.worker

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
