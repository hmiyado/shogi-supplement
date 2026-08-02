package dev.miyado.shogisupplement.blunder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * DisplayWinProb（表示用勝率換算 s=1254）の単体テスト。
 *
 * 判定系（BlunderJudge s=600）と別物であることを確認する。
 */
class DisplayWinProbTest {

    @Test
    fun `cp=0 は互角（0_5）`() {
        assertEquals(0.5, DisplayWinProb.winProb(0), 1e-9)
    }

    @Test
    fun `cp=600 は約61_8パーセント`() {
        // 1 / (1 + exp(-600/1254)) ≈ 0.6178
        val wp = DisplayWinProb.winProb(600)
        assertEquals(0.618, wp, 1e-3)
    }

    @Test
    fun `cp=1254 は約73_1パーセント`() {
        // 1 / (1 + exp(-1)) ≈ 0.7311
        val wp = DisplayWinProb.winProb(1254)
        assertEquals(0.731, wp, 1e-3)
    }

    @Test
    fun `cp負値は0_5未満`() {
        val wp = DisplayWinProb.winProb(-600)
        assertEquals(0.382, wp, 1e-3)
    }

    @Test
    fun `lossWp はシグモイド差分の正確な計算`() {
        // cpBefore=300, cpAfter=200 → loss = winProb(300) - winProb(-200)
        val expected = DisplayWinProb.winProb(300) - DisplayWinProb.winProb(-200)
        val actual = DisplayWinProb.lossWp(cpBefore = 300, cpAfter = 200)
        assertEquals(expected, actual, 1e-12)
    }

    @Test
    fun `s=1254はs=600より損失が小さく見える（同じcp損失）`() {
        // 同じ cpBefore=500, cpAfter=500 でも s=1254 の方が損失値は小さい
        val display = DisplayWinProb.lossWp(500, 500)
        val judge = BlunderJudge.winProb(500) - BlunderJudge.winProb(-500)
        // 600 系の方が cp 感度が高い → 損失値大
        assertTrue(judge > display, "s=600 の損失($judge) は s=1254 の損失($display) より大きいはず")
    }

    @Test
    fun `cp有り勝率モードと旧lossWpの分岐確認`() {
        // cp_before=300, cp_after=400 (=損失700cp) のケース
        val cpBefore = 300
        val cpAfter = 400
        val recalculated = DisplayWinProb.lossWp(cpBefore, cpAfter)
        // 旧 loss_wp は s=600 で計算されているはず（値が異なる）
        val oldLossWp = BlunderJudge.winProb(cpBefore) - BlunderJudge.winProb(-cpAfter)
        // 両者は異なる（s が違うため）
        val diff = kotlin.math.abs(recalculated - oldLossWp)
        assertTrue(diff > 0.001, "s=1254($recalculated) と s=600($oldLossWp) は差があるはず: diff=$diff")
    }
}
