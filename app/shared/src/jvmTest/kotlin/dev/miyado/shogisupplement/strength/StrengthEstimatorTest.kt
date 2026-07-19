package dev.miyado.shogisupplement.strength

import dev.miyado.shogisupplement.judge.CoefficientTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * StrengthEstimator のテスト。
 *
 * 係数表の帯別悪手率合計（coefficients_hao_v1.json より）:
 *   <1300     → 92.8 件/1000手 (中点 1150)
 *   1300-1599 → 71.6           (中点 1450)
 *   1600-1899 → 61.9           (中点 1750)
 *   1900-2199 → 49.1           (中点 2050)
 *   2200+     → 26.0           (中点 2350)
 */
class StrengthEstimatorTest {

    private fun loadTable(): CoefficientTable {
        val text = checkNotNull(
            javaClass.classLoader.getResourceAsStream("coefficients_hao_v1.json"),
        ) { "coefficients_hao_v1.json not found in test resources" }
            .readBytes().decodeToString()
        return CoefficientTable.fromJson(text)
    }

    // ─── アンカー値の復元 ─────────────────────────────────────────────────────

    @Test
    fun `アンカー値92_8を入力すると中点1150が返る`() {
        val table = loadTable()
        val result = StrengthEstimator.estimate(92.8, 2000, table)
        // 最低帯アンカーちょうどなのでクランプまたは境界補間で 1150 付近になる
        assertTrue(
            result.rating in 1100..1200,
            "92.8件は最低帯アンカー付近、rating=${result.rating}",
        )
    }

    @Test
    fun `アンカー値26_0を入力すると中点2350が返る`() {
        val table = loadTable()
        val result = StrengthEstimator.estimate(26.0, 2000, table)
        assertTrue(
            result.rating in 2300..2400,
            "26.0件は最高帯アンカー付近、rating=${result.rating}",
        )
    }

    @Test
    fun `1600帯の合計悪手率を入力すると1750が返る`() {
        val table = loadTable()
        // 帯別全カテゴリ合計を使う（浮動小数点精度のためハードコード値ではなく実計算値を使用）
        val rate1750 = table.rates_per_1000_moves["1600-1899"]!!.values.sum()
        val result = StrengthEstimator.estimate(rate1750, 2000, table)
        assertEquals(ClampState.NONE, result.clamped)
        assertEquals(1750, result.rating)
    }

    @Test
    fun `1900帯の合計悪手率を入力すると2050が返る`() {
        val table = loadTable()
        val rate2050 = table.rates_per_1000_moves["1900-2199"]!!.values.sum()
        val result = StrengthEstimator.estimate(rate2050, 2000, table)
        assertEquals(ClampState.NONE, result.clamped)
        assertEquals(2050, result.rating)
    }

    // ─── 中間値の単調性 ───────────────────────────────────────────────────────

    @Test
    fun `悪手率が小さくなるほどレートが高くなる（単調性）`() {
        val table = loadTable()
        val rates = listOf(90.0, 75.0, 65.0, 55.0, 40.0, 28.0)
        val ratings = rates.map { StrengthEstimator.estimate(it, 2000, table).rating }
        for (i in 1 until ratings.size) {
            assertTrue(
                ratings[i] >= ratings[i - 1],
                "rate=${rates[i]}件の方がrate=${rates[i-1]}件より強いはず: ${ratings[i]} < ${ratings[i-1]}",
            )
        }
    }

    // ─── クランプ ─────────────────────────────────────────────────────────────

    @Test
    fun `悪手率が最高帯より低い場合CLAMPED_HIGH`() {
        val table = loadTable()
        val result = StrengthEstimator.estimate(10.0, 2000, table)
        assertEquals(ClampState.CLAMPED_HIGH, result.clamped)
        assertEquals(2350, result.rating)
    }

    @Test
    fun `悪手率が最低帯より高い場合CLAMPED_LOW`() {
        val table = loadTable()
        val result = StrengthEstimator.estimate(150.0, 2000, table)
        assertEquals(ClampState.CLAMPED_LOW, result.clamped)
        assertEquals(1150, result.rating)
    }

    // ─── 誤差幅（errorMargin）境界 ─────────────────────────────────────────────
    // 正となる資料: research/docs/strength-convergence.md
    // 「アプリの誤差表示への適用」表（保守側丸め）:
    //   〜300手 → ±700／〜1000手 → ±650／〜2000手 → ±600／2000手〜 → ±560

    @Test
    fun `totalMoves300で誤差幅700`() {
        val table = loadTable()
        val result = StrengthEstimator.estimate(61.9, 300, table)
        assertEquals(700, result.errorMargin, "300手は境界内側（〜300手帯）で±700であるべき")
    }

    @Test
    fun `totalMoves301で誤差幅650`() {
        val table = loadTable()
        val result = StrengthEstimator.estimate(61.9, 301, table)
        assertEquals(650, result.errorMargin, "301手は境界外側（〜1000手帯）で±650であるべき")
    }

    @Test
    fun `totalMoves1000で誤差幅650`() {
        val table = loadTable()
        val result = StrengthEstimator.estimate(61.9, 1000, table)
        assertEquals(650, result.errorMargin, "1000手は境界内側（〜1000手帯）で±650であるべき")
    }

    @Test
    fun `totalMoves1001で誤差幅600`() {
        val table = loadTable()
        val result = StrengthEstimator.estimate(61.9, 1001, table)
        assertEquals(600, result.errorMargin, "1001手は境界外側（〜2000手帯）で±600であるべき")
    }

    @Test
    fun `totalMoves2000で誤差幅600`() {
        val table = loadTable()
        val result = StrengthEstimator.estimate(61.9, 2000, table)
        assertEquals(600, result.errorMargin, "2000手は境界内側（〜2000手帯）で±600であるべき")
    }

    @Test
    fun `totalMoves2001で誤差幅560`() {
        val table = loadTable()
        val result = StrengthEstimator.estimate(61.9, 2001, table)
        assertEquals(560, result.errorMargin, "2001手は境界外側（2000手〜帯）で±560であるべき")
    }

    @Test
    fun `totalMoves0で誤差幅700`() {
        val table = loadTable()
        val result = StrengthEstimator.estimate(61.9, 0, table)
        assertEquals(700, result.errorMargin, "0手は〜300手帯なので±700であるべき")
    }

    // ─── 表示形式（toDisplayString） ─────────────────────────────────────────
    // 常に「51 ±25」形式（偏差値・norm v1換算）の誤差幅表示にする
    // （「（推定）」チップは使わない。単位ラベルはタイトル側が持つ）。

    @Test
    fun `補間範囲内の表示形式は偏差値とプラスマイナス誤差`() {
        val table = loadTable()
        val rate1750 = table.rates_per_1000_moves["1600-1899"]!!.values.sum()
        val result = StrengthEstimator.estimate(rate1750, 1000, table)
        // R1750 → 偏差値53、±650 → ±25
        assertEquals("53 ±25", result.toDisplayString())
    }

    @Test
    fun `高クランプ時の表示形式は偏差値プラスとプラスマイナス誤差`() {
        val table = loadTable()
        val result = StrengthEstimator.estimate(10.0, 200, table)
        // R2350+ → 偏差値77+、±700 → ±27
        assertEquals("77+ ±27", result.toDisplayString())
    }

    @Test
    fun `低クランプ時の表示形式は偏差値未満とプラスマイナス誤差`() {
        val table = loadTable()
        val result = StrengthEstimator.estimate(150.0, 2500, table)
        // R1150未満 → 偏差値30未満、±560 → ±22
        assertEquals("30未満 ±22", result.toDisplayString())
    }
}
