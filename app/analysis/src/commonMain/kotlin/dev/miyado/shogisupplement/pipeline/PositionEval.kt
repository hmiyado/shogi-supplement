package dev.miyado.shogisupplement.pipeline

import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.engine.PvInfo

/** 1局面の解析結果。 @property score 手番側スコア。 @property pv 最善手のPV。 @property pv2Score 次善手のスコア。 @property pv2MoveUsi 次善手。 */
data class PositionEval(
    val score: Score?,
    val pv: List<String>,
    val pv2Score: Score? = null,
    val pv2MoveUsi: String? = null,
)

/** 1局面のMultiPV結果をPositionEvalへ変換する。multipv=1/2の抽出を一箇所に集約する。 */
fun List<PvInfo>.toPositionEval(): PositionEval {
    val pv1 = firstOrNull { it.multipv == 1 }
    val pv2 = firstOrNull { it.multipv == 2 }
    return PositionEval(
        score = pv1?.score,
        pv = pv1?.pv ?: emptyList(),
        pv2Score = pv2?.score,
        pv2MoveUsi = pv2?.pv?.firstOrNull(),
    )
}
