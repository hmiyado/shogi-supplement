package dev.miyado.shogisupplement.drill

import dev.miyado.shogisupplement.db.BlunderRecord

/**
 * ドリル周回の出題決定ロジック。
 *
 * 決定則:
 * 1. 解答回数（drill_attempt 件数）が少ない問題を優先
 * 2. 同数なら ◎ → ○ の順（verdict 先頭文字でソート）
 * 3. さらに同数なら priority 降順
 *
 * この決定則により、全問を自然に1周ずつ消化する周回動作になる。
 * 全問の解答回数が同数になった時点で次の周が開始される。
 */
object DrillRotation {

    /**
     * 次の出題を選ぶ。
     *
     * @param candidates     出題候補リスト（verdict が ◎ または ○ のもの）
     * @param attemptCounts  各候補の解答回数マップ（blunder_report_id → 解答回数）
     * @return 次の問題。candidates が空なら null
     */
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
