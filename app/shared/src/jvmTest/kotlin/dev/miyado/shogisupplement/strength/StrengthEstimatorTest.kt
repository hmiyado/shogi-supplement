package dev.miyado.shogisupplement.strength

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * StrengthEstimator（推定器v2）のテスト。
 * 線形式そのものの数値的正しさは [EstimatorV2GoldenTest] で検証する。本テストは
 * 欠損時フォールバック・帯割り当て・誤差幅・複数局集約など周辺ロジックを対象にする。
 */
class StrengthEstimatorTest {

    /** 全特徴量が欠損（null）の特徴量。標準化平均が代入され寄与ゼロになるはず。 */
    private val allMissing = RawFeaturesV2(
        pv1MatchRate = null,
        openingMeanLoss = null,
        ownLogRate = null,
        middleMeanLoss = null,
        maxLeadDrop = null,
        mateMissRate1000 = null,
        nMoves = 0,
    )

    // ─── 欠損時のフォールバック ───────────────────────────────────────────────

    @Test
    fun `全特徴量が欠損の場合は切片そのものになる`() {
        val rating = StrengthEstimator.predict(allMissing)
        assertEquals(1724.495669870962, rating, 1e-9)
    }

    // ─── 帯割り当て ────────────────────────────────────────────────────────

    @Test
    fun `帯境界ちょうどは上の帯に入る`() {
        assertEquals(0, StrengthEstimator.bandIndex(0.0))
        assertEquals(0, StrengthEstimator.bandIndex(1604.272205993674 - 0.001))
        assertEquals(1, StrengthEstimator.bandIndex(1604.272205993674))
        assertEquals(1, StrengthEstimator.bandIndex(1702.7952271705592 - 0.001))
        assertEquals(2, StrengthEstimator.bandIndex(1702.7952271705592))
        assertEquals(2, StrengthEstimator.bandIndex(1801.3182483474445 - 0.001))
        assertEquals(3, StrengthEstimator.bandIndex(1801.3182483474445))
        assertEquals(3, StrengthEstimator.bandIndex(1899.8412695243296 - 0.001))
        assertEquals(4, StrengthEstimator.bandIndex(1899.8412695243296))
        assertEquals(4, StrengthEstimator.bandIndex(99998.0))
    }

    @Test
    fun `境界外の極端な値も端の帯indexに収まる`() {
        assertEquals(0, StrengthEstimator.bandIndex(-500.0))
        assertEquals(4, StrengthEstimator.bandIndex(999_999.0))
    }

    // ─── 誤差幅（errorMargin）境界 ─────────────────────────────────────────
    // 正となる資料: research/docs/strength-convergence.md
    // 「アプリの誤差表示への適用」表（保守側丸め）:
    //   〜300手 → ±700／〜1000手 → ±650／〜2000手 → ±600／2000手〜 → ±560

    @Test
    fun `誤差幅は集計対象手数の境界で切り替わる`() {
        assertEquals(290, StrengthEstimator.estimate(allMissing.copy(nMoves = 300)).errorMargin)
        assertEquals(280, StrengthEstimator.estimate(allMissing.copy(nMoves = 301)).errorMargin)
        assertEquals(280, StrengthEstimator.estimate(allMissing.copy(nMoves = 2001)).errorMargin)
        assertEquals(290, StrengthEstimator.estimate(allMissing.copy(nMoves = 0)).errorMargin)
    }

    @Test
    fun `estimateのclampedは常にNONE`() {
        assertEquals(ClampState.NONE, StrengthEstimator.estimate(allMissing).clamped)
    }

    // ─── aggregate（複数局の平均集約） ─────────────────────────────────────

    @Test
    fun `aggregateは各局レートの単純平均を丸める`() {
        val result = StrengthEstimator.aggregate(listOf(1700, 1750, 1800), totalMoves = 300)
        assertEquals(1750, result.rating)
        assertEquals(290, result.errorMargin)
        assertEquals(300, result.totalMoves)
        assertEquals(ClampState.NONE, result.clamped)
    }

    @Test
    fun `aggregateは1局のみでも動く`() {
        val result = StrengthEstimator.aggregate(listOf(1823), totalMoves = 55)
        assertEquals(1823, result.rating)
    }

    // ─── 表示形式（toDisplayString） ───────────────────────────────────────

    @Test
    fun `norm平均付近のレートは偏差値50になる`() {
        val result = StrengthEstimator.aggregate(listOf(1718), totalMoves = 1000)
        assertEquals("50 ±11", result.toDisplayString())
    }
}
