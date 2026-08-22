package dev.miyado.shogisupplement.drill

import dev.miyado.shogisupplement.db.BlunderRecord
import kotlin.random.Random

/** ドリルの次問を決定する。解答回数、判定、priorityの順に優先する。 */
object DrillRotation {

    /**
     * 次の出題を選ぶ。同一棋譜は priority が近く連続しやすいため、同点候補群はランダムに崩す。
     * @param candidates 出題候補。 @param attemptCounts 解答回数。 @param random 同点タイブレーク用。 @return 次の問題またはnull。
     */
    fun selectNext(
        candidates: List<BlunderRecord>,
        attemptCounts: Map<Long, Int>,
        random: Random = Random.Default,
    ): BlunderRecord? {
        if (candidates.isEmpty()) return null
        val tieBreak = candidates.associate { it.id to random.nextInt() }
        return candidates.minWithOrNull(
            compareBy<BlunderRecord> { attemptCounts[it.id] ?: 0 }
                .thenBy { if (it.verdict.startsWith("◎")) 0 else 1 }
                .thenByDescending { it.priority }
                .thenBy { tieBreak.getValue(it.id) },
        )
    }
}
