package dev.miyado.shogisupplement.drill

import kotlin.math.abs

/**
 * 局面が拮抗しているか（有力な代替手が複数あり、読み筋の不一致を悪手として扱えないか）を判定する。
 *
 * 実測に基づく閾値（|cp|≥800 または gap≥400 は「差がある」局面、それ以外は拮抗）。
 * 詰み絡みの局面は対象外（拮抗とはみなさない）。
 */
object DrillContestType {

    private const val DECISIVE_CP_ABS = 800L
    private const val DECISIVE_GAP_ABS = 400L
    private const val MATE_SCORE_ABS = 29_000L

    /** @param cpBefore 出題局面の評価値（先手視点ではなく手番視点、絶対値のみ使う）。 @param secondCp 次善手の評価値。 @param missedMateIn 見落とした詰み手数。 */
    fun isCloseContest(cpBefore: Long?, secondCp: Long?, missedMateIn: Long?): Boolean {
        if (missedMateIn != null) return false
        val cp = cpBefore ?: return false
        if (abs(cp) >= MATE_SCORE_ABS) return false
        if (abs(cp) >= DECISIVE_CP_ABS) return false
        val gap = secondCp?.let { abs(cp - it) }
        if (gap != null && gap >= DECISIVE_GAP_ABS) return false
        return true
    }
}
