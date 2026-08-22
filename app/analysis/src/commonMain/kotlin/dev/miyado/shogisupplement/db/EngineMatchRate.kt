package dev.miyado.shogisupplement.db

/**
 * 自分の手がエンジンの最善手（pv1）または次善手（pv2）と一致した割合（top2一致率）。
 *
 * Why not 既存の推定器特徴量にあるpv1一致率を流用しない:
 * あちらはpv1のみの一致率で、top2一致率とは定義が異なる。
 * このオブジェクトは表示専用に position_eval（保存済み）から直接計算する。
 */
object EngineMatchRate {

    /** @param rate 一致率。 @param matched 一致した手数。 */
    data class Result(val rate: Double, val matched: Int, val sampleMoves: Int)

    /**
     * @param positionEvals 保存済み局面評価（ply = movesUsi のインデックスに対応する着手前局面）
     * @param userSide ユーザーの側（"sente"/"gote"）
     */
    fun compute(
        movesUsi: List<String>,
        positionEvals: List<PositionEvalRow>,
        userSide: String?,
    ): Result? {
        if (userSide == null) return null
        val byPly = positionEvals.associateBy { it.ply }
        var matched = 0
        var total = 0
        for (t in movesUsi.indices) {
            val mover = if (t % 2 == 0) "sente" else "gote"
            if (mover != userSide) continue
            val row = byPly[t] ?: continue
            val best = row.bestUsi ?: continue
            total++
            val moveUsi = movesUsi[t]
            if (moveUsi == best || (row.secondUsi != null && moveUsi == row.secondUsi)) {
                matched++
            }
        }
        if (total == 0) return null
        return Result(rate = matched.toDouble() / total, matched = matched, sampleMoves = total)
    }
}
