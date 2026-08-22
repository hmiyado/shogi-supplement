package dev.miyado.shogisupplement.drill

import dev.miyado.shogisupplement.db.BlunderRecord

/** ドリルの次問を決定する。解答回数、判定、priorityの順に優先する。 */
object DrillRotation {

    /** 次の出題を選ぶ。 @param candidates 出題候補。 @param attemptCounts 解答回数。 @return 次の問題またはnull。 */
    fun selectNext(
        candidates: List<BlunderRecord>,
        attemptCounts: Map<Long, Int>,
    ): BlunderRecord? {
        if (candidates.isEmpty()) return null
        return candidates.minWithOrNull(
            compareBy<BlunderRecord> { attemptCounts[it.id] ?: 0 }
                .thenBy { if (it.verdict.startsWith("◎")) 0 else 1 }
                .thenByDescending { it.priority },
        )
    }
}
