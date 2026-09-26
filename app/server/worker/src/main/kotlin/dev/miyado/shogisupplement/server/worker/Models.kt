package dev.miyado.shogisupplement.server.worker

import dev.miyado.shogisupplement.engine.Engine
import dev.miyado.shogisupplement.engine.EngineInvariants
import dev.miyado.shogisupplement.api.analysis.PositionAnalysisPurpose

sealed class EngineInput {
    /** 冪等キー（moves_hash）算出用の元文字列。 */
    abstract val hashSeed: String

    data class Game(
        val movesUsi: List<String>,
        val forceReanalysis: Boolean = false,
        val requestId: String? = null,
    ) : EngineInput() {
        override val hashSeed: String
            get() = movesUsi.joinToString(" ") +
                if (forceReanalysis) "|request_id=${requestId.orEmpty()}" else ""
    }

    data class Position(val sfen: String, val moves: List<String>, val multiPv: Int,
        val purpose: PositionAnalysisPurpose? = null,
    ) : EngineInput() {
        init { require(purpose != PositionAnalysisPurpose.DRILL || multiPv == EngineInvariants.DRILL_SECONDARY_MULTI_PV) }
        // multiPvは結果の本数を変えるため、冪等キーへ入れないと本数の違う結果を取り違える。
        // 既定値だけ従来の形を保つ理由: 変えると公開済みクライアントの既存ジョブが全て取り直しになり、
        // 日次上限に達している利用者はキャッシュの代わりに429を受け取る。
        override val hashSeed: String
            get() = "$sfen|${moves.joinToString(" ")}" +
                (if (multiPv == Engine.MULTI_PV) "" else "|$multiPv") +
                (if (purpose == PositionAnalysisPurpose.DRILL) "|purpose=drill" else "")
    }
}
