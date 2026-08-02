package dev.miyado.shogisupplement.strength

import dev.miyado.shogisupplement.blunder.BlunderJudge
import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.pipeline.PositionEval
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * [FeatureExtractorV2] の合成 [PositionEval] 系列を使った境界ケーステスト。
 * 実データでの突合は [dev.miyado.shogisupplement.pipeline.GoldenTest] で行う。
 */
class FeatureExtractorV2Test {

    private fun cpEval(cp: Int, pv: List<String> = emptyList()) = PositionEval(score = Score.Cp(cp), pv = pv)
    private fun mateEval(plies: Int, pv: List<String> = emptyList()) = PositionEval(score = Score.Mate(plies), pv = pv)
    private fun noEval() = PositionEval(score = null, pv = emptyList())

    @Test
    fun `詰み機会が無ければ0手扱いではなくmateMissRate1000は0`() {
        val moves = listOf("7g7f", "3c3d", "2g2f")
        val evals = listOf(
            cpEval(50, pv = listOf("7g7f")),   // t0 sente: pv1一致
            cpEval(-40, pv = listOf("3c3d")),  // t1 gote
            cpEval(30, pv = listOf("9g9f")),   // t2 sente: pv1不一致
            cpEval(20),                        // 最終局面
        )

        val features = FeatureExtractorV2.extract(moves, evals, ownSide = setOf("sente"))

        assertEquals(2, features.nMoves, "sente側の手は t0, t2 の2手")
        assertEquals(0.0, features.mateMissRate1000, "詰み機会が無い場合は欠損(null)ではなく発生率0")
        assertEquals(0.5, features.pv1MatchRate, "2手中1手のみpv1一致")
        assertNotNull(features.openingMeanLoss)
        assertNotNull(features.maxLeadDrop)

        // opening_mean_loss は実装が使うのと同じ winProb を直接使って独立に検算する
        val lossT0 = BlunderJudge.winProb(50) - BlunderJudge.winProb(40)
        val lossT2 = BlunderJudge.winProb(30) - BlunderJudge.winProb(-20)
        assertEquals((lossT0 + lossT2) / 2, features.openingMeanLoss!!, 1e-9)
    }

    @Test
    fun `短手数で最終局面の評価値が無い場合own系はnullだがpv1一致率は計算できる`() {
        val moves = listOf("7g7f")
        val evals = listOf(
            cpEval(50, pv = listOf("7g7f")),
            noEval(), // 最終局面の評価値なし(エンジン失敗等)
        )

        val features = FeatureExtractorV2.extract(moves, evals, ownSide = setOf("sente"))

        assertEquals(0, features.nMoves, "前後の評価値が揃っていないため対象手数は0")
        assertNull(features.openingMeanLoss)
        assertNull(features.middleMeanLoss)
        assertNull(features.ownLogRate)
        assertNull(features.maxLeadDrop)
        assertNull(features.mateMissRate1000)
        // pv1一致率は次局面の評価値の有無に依存しないので計算できる
        assertEquals(1.0, features.pv1MatchRate)
    }

    @Test
    fun `後手番の視点反転_own側を後手にすると奇数plyのみ集計する`() {
        val moves = listOf("7g7f", "3c3d", "2g2f", "8c8d")
        val evals = listOf(
            cpEval(50, pv = listOf("7g7f")),   // t0 sente（対象外）
            cpEval(-40, pv = listOf("3c3d")),  // t1 gote: pv1一致
            cpEval(30, pv = listOf("9g9f")),   // t2 sente（対象外）
            cpEval(-10, pv = listOf("1a1b")),  // t3 gote: pv1不一致
            cpEval(20),                        // 最終局面
        )

        val features = FeatureExtractorV2.extract(moves, evals, ownSide = setOf("gote"))

        assertEquals(2, features.nMoves, "gote側の手は t1, t3 の2手")
        assertEquals(0.5, features.pv1MatchRate)
    }

    @Test
    fun `序盤と中盤の境界はply40で切り替わる_0indexed`() {
        // ply39(0-indexed)までが序盤、ply40以降が中盤。gote側(奇数ply)のみを対象に、
        // ply39とply41の2手だけを損失計算対象にする（間は評価値なしで除外）。
        val n = 42
        val moves = List(n) { "m$it" }
        val evals = MutableList(n + 1) { noEval() }
        // ply39(gote, 序盤側境界) と ply41(gote, 中盤側) の前後だけ評価値を入れる
        evals[39] = cpEval(0, pv = listOf("m39"))
        evals[40] = cpEval(100) // ply39のnxt
        evals[41] = cpEval(0, pv = listOf("m41"))
        evals[42] = cpEval(-100) // ply41のnxt

        val features = FeatureExtractorV2.extract(moves, evals, ownSide = setOf("gote"))

        assertEquals(2, features.nMoves)
        val expectedOpeningLoss = BlunderJudge.winProb(0) - BlunderJudge.winProb(-100)
        val expectedMiddleLoss = BlunderJudge.winProb(0) - BlunderJudge.winProb(100)
        assertEquals(expectedOpeningLoss, features.openingMeanLoss!!, 1e-9, "ply39は序盤(0-39)")
        assertEquals(expectedMiddleLoss, features.middleMeanLoss!!, 1e-9, "ply41は中盤(40-79)")
    }

    @Test
    fun `詰み見逃しは次局面もmateスコアの場合のみ機会として数える`() {
        val moves = listOf("7g7f", "3c3d")
        val evals = listOf(
            mateEval(3),               // t0(sente): 自分に3手詰め
            cpEval(200),               // t1(gote視点)。mateでなくcpに切り替わった → 機会として数えない
            cpEval(-190),
        )
        val features = FeatureExtractorV2.extract(moves, evals, ownSide = setOf("sente"))
        assertEquals(1, features.nMoves)
        assertEquals(0.0, features.mateMissRate1000, "next側がmateスコアでないため機会自体が0件")
    }

    @Test
    fun `詰みを逃すとmateMissRate1000に反映される`() {
        val moves = listOf("7g7f", "3c3d")
        val evals = listOf(
            mateEval(3),   // t0(sente): 自分に3手詰め
            mateEval(5),   // t1(gote視点)でもmateかつplies>0 → 自分が詰まされる継続 = 見逃し
            cpEval(0),
        )
        val features = FeatureExtractorV2.extract(moves, evals, ownSide = setOf("sente"))
        assertEquals(1, features.nMoves)
        assertEquals(1000.0, features.mateMissRate1000, "1手中1件見逃し → 1000件/1000手")
    }

    @Test
    fun `悪手1件はown_log_rateに反映される`() {
        // スイング悪手: 指す前勝率0.05-0.95 かつ loss_cp>=500 かつ 指した後マイナス(cpAfter>0)
        val moves = listOf("7g7f")
        val evals = listOf(
            cpEval(100),
            cpEval(450), // lossCp = 100+450=550 >= 500
        )
        val features = FeatureExtractorV2.extract(moves, evals, ownSide = setOf("sente"))
        assertEquals(1, features.nMoves)
        assertEquals(ln(1.0 + 1 * 1000.0 / 1), features.ownLogRate!!, 1e-9)
    }

    @Test
    fun `最大リード喪失幅は勝率の running max からの落差`() {
        val moves = listOf("m0", "m1", "m2")
        val evals = listOf(
            cpEval(600),  // wp高い(リード)
            cpEval(-600), // 大きく下落
            cpEval(0),
            cpEval(0),
        )
        val features = FeatureExtractorV2.extract(moves, evals, ownSide = setOf("sente", "gote"))
        val wp0 = BlunderJudge.winProb(600)
        val wp1 = BlunderJudge.winProb(-600)
        val wp2 = BlunderJudge.winProb(0)
        val runningMax = maxOf(wp0, wp1, wp2)
        val expectedDrop = maxOf(wp0 - wp0, runningMax - wp1, runningMax - wp2)
        assertEquals(expectedDrop, features.maxLeadDrop!!, 1e-9)
    }
}
