package dev.miyado.shogisupplement.strength

import dev.miyado.shogisupplement.blunder.BlunderJudge
import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.pipeline.PositionEval
import kotlin.math.ln

/**
 * 推定器v2（G-sparse線形式）の生特徴量。
 *
 * null は算出不能（分母0、対象手がゼロ等）を表す。[StrengthEstimator.predict] 側で
 * 標準化平均を代入して寄与をゼロにする。
 */
data class RawFeaturesV2(
    val pv1MatchRate: Double?,
    val openingMeanLoss: Double?,
    val ownLogRate: Double?,
    val middleMeanLoss: Double?,
    val maxLeadDrop: Double?,
    val mateMissRate1000: Double?,
    val nMoves: Int,
)

/**
 * 局面ごとのエンジン解析結果（[PositionEval] 列）から推定器v2の6特徴量を計算する純関数。
 *
 * 悪手判定は既存の [BlunderJudge] をそのまま使う。Why not own_log_rateだけ別ロジックに分ける:
 * 既存の相応判定パイプラインと二重に定義を持つとずれるリスクがあるため。
 */
object FeatureExtractorV2 {

    /** 序盤/中盤の境界（0-indexed ply）。終盤(ply80+)は本推定器では未使用。 */
    private const val OPENING_END_PLY = 40
    private const val MIDDLE_END_PLY = 80

    /**
     * @param evals 各局面のエンジン評価（サイズ = moves.size + 1。evals[t] が moves[t] を指す前の局面）
     * @param ownSide 特徴量算出の対象手番集合（両側指定時はまとめて「own」とみなす）
     */
    fun extract(moves: List<String>, evals: List<PositionEval>, ownSide: Set<String>): RawFeaturesV2 {
        require(evals.size == moves.size + 1) {
            "evals.size (${evals.size}) must equal moves.size + 1 (${moves.size + 1})"
        }

        var nMoves = 0
        var nBlunders = 0
        var nComparable = 0
        var nMatch = 0
        var openingLossSum = 0.0
        var openingCount = 0
        var middleLossSum = 0.0
        var middleCount = 0
        var mateOpportunities = 0
        var mateMisses = 0
        val ownEvalBeforeWp = mutableListOf<Double>()

        for (t in moves.indices) {
            val mover = if (t % 2 == 0) "sente" else "gote"
            if (mover !in ownSide) continue

            val cur = evals[t]
            val nxt = evals[t + 1]
            val bestUsi = cur.pv.firstOrNull()

            // pv1一致率は対局全体の手が対象（前後局面の評価値の有無に依存しない）。
            if (bestUsi != null) {
                nComparable++
                if (bestUsi == moves[t]) nMatch++
            }

            val curScore = cur.score
            val nxtScore = nxt.score
            // 損失・悪手率・詰み対応は前後局面の評価値が揃っている手のみ対象にする。
            if (curScore == null || nxtScore == null) continue

            nMoves++

            val cpBefore = BlunderJudge.toCp(curScore)
            val cpAfter = BlunderJudge.toCp(nxtScore)
            val wpBefore = BlunderJudge.winProb(cpBefore)
            ownEvalBeforeWp.add(wpBefore)

            val lossWp = wpBefore - BlunderJudge.winProb(-cpAfter)
            when {
                t < OPENING_END_PLY -> {
                    openingLossSum += lossWp
                    openingCount++
                }
                t < MIDDLE_END_PLY -> {
                    middleLossSum += lossWp
                    middleCount++
                }
                // 終盤(ply80+)は選定された6特徴量に含まれないため集計しない
            }

            // 詰み対応: 次局面もmateスコアの場合のみ「機会」とみなす（cpに切り替わった場合は数えない）。
            if (curScore is Score.Mate && curScore.plies > 0 && nxtScore is Score.Mate) {
                mateOpportunities++
                if (nxtScore.plies > 0) mateMisses++
            }

            val verdict = BlunderJudge.judge(curScore, nxtScore, moveUsi = moves[t], bestUsi = bestUsi)
            if (verdict.isBlunder) nBlunders++
        }

        val pv1MatchRate = if (nComparable > 0) nMatch.toDouble() / nComparable else null
        val openingMeanLoss = if (openingCount > 0) openingLossSum / openingCount else null
        val middleMeanLoss = if (middleCount > 0) middleLossSum / middleCount else null
        val ownLogRate = if (nMoves > 0) ln(1.0 + nBlunders * 1000.0 / nMoves) else null
        val mateMissRate1000 = if (nMoves > 0) mateMisses * 1000.0 / nMoves else null
        val maxLeadDrop = maxLeadDrop(ownEvalBeforeWp)

        return RawFeaturesV2(
            pv1MatchRate = pv1MatchRate,
            openingMeanLoss = openingMeanLoss,
            ownLogRate = ownLogRate,
            middleMeanLoss = middleMeanLoss,
            maxLeadDrop = maxLeadDrop,
            mateMissRate1000 = mateMissRate1000,
            nMoves = nMoves,
        )
    }

    /** 勝率スケールの max drawdown（最大リードからの喪失幅）。対象手が1件も無ければ null。 */
    private fun maxLeadDrop(seq: List<Double>): Double? {
        if (seq.isEmpty()) return null
        var runningMax = seq[0]
        var maxDrop = 0.0
        for (v in seq) {
            if (v > runningMax) runningMax = v
            val drop = runningMax - v
            if (drop > maxDrop) maxDrop = drop
        }
        return maxDrop
    }
}
