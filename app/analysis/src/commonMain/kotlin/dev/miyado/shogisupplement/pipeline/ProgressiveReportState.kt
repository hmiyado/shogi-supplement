package dev.miyado.shogisupplement.pipeline

import dev.miyado.shogisupplement.blunder.BlunderJudge
import dev.miyado.shogisupplement.engine.PvInfo

/**
 * 順不同で届く局面解析を、ply=0から連続する確定区間として公開する。
 * @param moves 棋譜のUSI手列。 @param total 局面数。 @param confirmedThrough 確定済みwatermark。 @param confirmed 到着済み局面。
 */
data class ProgressiveReportState(
    val moves: List<String>,
    val total: Int = moves.size + 1,
    val confirmedThrough: Int = 0,
    private val confirmed: Map<Int, PositionEval> = emptyMap(),
) {
    /** 到着済み局面数。連続確定区間のwatermarkとは別に数える。 */
    val doneCount: Int get() = confirmed.size

    /** 全局面が確定済みか。 */
    val isComplete: Boolean get() = confirmedThrough >= total

    /** 局面plyの解析結果を反映する。範囲外または既知のplyは無視する。 */
    fun withPosition(ply: Int, pvs: List<PvInfo>): ProgressiveReportState {
        if (ply !in 0 until total || confirmed.containsKey(ply)) return this
        val next = confirmed + (ply to pvs.toPositionEval())
        var watermark = confirmedThrough
        while (watermark < total && next.containsKey(watermark)) watermark++
        return copy(confirmed = next, confirmedThrough = watermark)
    }

    /** 反映済み区間（[0, confirmedThrough)）の評価。 */
    val revealedEvals: List<PositionEval>
        get() = (0 until confirmedThrough).map { confirmed.getValue(it) }

    /** 反映済み区間内で悪手と判定された手のply。バンド判定は行わず、隣接局面の検出だけを返す。 */
    val revealedBlunderPlies: Set<Int>
        get() = buildSet {
            for (ply in 1 until confirmedThrough) {
                val cur = confirmed.getValue(ply - 1)
                val nxt = confirmed.getValue(ply)
                val curScore = cur.score ?: continue
                val nxtScore = nxt.score ?: continue
                val moveUsi = moves.getOrNull(ply - 1) ?: continue
                val verdict = BlunderJudge.judge(curScore, nxtScore, moveUsi, cur.pv.firstOrNull())
                if (verdict.isBlunder) add(ply)
            }
        }

    companion object {
        /** 解析開始直後の初期状態（何も反映されていない）。 */
        fun initial(moves: List<String>): ProgressiveReportState = ProgressiveReportState(moves = moves)
    }
}
