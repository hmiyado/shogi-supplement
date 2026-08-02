package dev.miyado.shogisupplement.blunder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * PositionEvalDisplay の単体テスト（手送り時の局面評価値表示）。
 *
 * 検証範囲:
 * - cp モードの符号付き表示と後手の符号反転
 * - wp モードの勝率換算（s=1254）と後手の符号反転
 * - 詰み局面（mate_in）の表示
 * - 評価値なし局面（両方 null）は非表示
 */
class PositionEvalDisplayTest {

    // ─── cp モード ─────────────────────────────────────────────────────────────

    @Test
    fun `cpモード・先手ユーザー・先手優勢はプラス表示`() {
        val label = PositionEvalDisplay.format(scoreCp = 320, mateIn = null, userIsGote = false, evalDisplay = "cp")!!
        assertEquals("+320", label.text)
        assertTrue(label.sign > 0)
    }

    @Test
    fun `cpモード・先手ユーザー・後手優勢はマイナス表示`() {
        val label = PositionEvalDisplay.format(scoreCp = -450, mateIn = null, userIsGote = false, evalDisplay = "cp")!!
        assertEquals("−450", label.text)
        assertTrue(label.sign < 0)
    }

    @Test
    fun `cpモード・後手ユーザーは符号反転する`() {
        // 先手視点 -450（後手優勢）→ 後手ユーザーには +450
        val label = PositionEvalDisplay.format(scoreCp = -450, mateIn = null, userIsGote = true, evalDisplay = "cp")!!
        assertEquals("+450", label.text)
        assertTrue(label.sign > 0)
    }

    @Test
    fun `cpモード・互角はプラスマイナス0で中立`() {
        val label = PositionEvalDisplay.format(scoreCp = 0, mateIn = null, userIsGote = false, evalDisplay = "cp")!!
        assertEquals("±0", label.text)
        assertEquals(0, label.sign)
    }

    // ─── wp モード ─────────────────────────────────────────────────────────────

    @Test
    fun `wpモード・互角は勝率50`() {
        val label = PositionEvalDisplay.format(scoreCp = 0, mateIn = null, userIsGote = false, evalDisplay = "wp")!!
        assertEquals("勝率50%", label.text)
        assertEquals(0, label.sign)
    }

    @Test
    fun `wpモード・s1254換算（cp=1254で約73）`() {
        val label = PositionEvalDisplay.format(scoreCp = 1254, mateIn = null, userIsGote = false, evalDisplay = "wp")!!
        assertEquals("勝率73%", label.text)
        assertTrue(label.sign > 0)
    }

    @Test
    fun `wpモード・後手ユーザーは符号反転してから換算する`() {
        // 先手視点 +1254 → 後手ユーザーには -1254 → 勝率 27%
        val label = PositionEvalDisplay.format(scoreCp = 1254, mateIn = null, userIsGote = true, evalDisplay = "wp")!!
        assertEquals("勝率27%", label.text)
        assertTrue(label.sign < 0)
    }

    // ─── 詰み局面 ──────────────────────────────────────────────────────────────

    @Test
    fun `詰み・先手ユーザーが詰ます側は勝ち表示`() {
        val label = PositionEvalDisplay.format(scoreCp = null, mateIn = 5, userIsGote = false, evalDisplay = "cp")!!
        assertEquals("+5手詰", label.text)
        assertTrue(label.sign > 0)
    }

    @Test
    fun `詰み・後手ユーザーは mate_in も符号反転する`() {
        // 先手視点 mate_in = -3（後手が詰ます）→ 後手ユーザーには勝ち
        val label = PositionEvalDisplay.format(scoreCp = null, mateIn = -3, userIsGote = true, evalDisplay = "cp")!!
        assertEquals("+3手詰", label.text)
        assertTrue(label.sign > 0)
    }

    @Test
    fun `詰み・詰まされる側は負け表示`() {
        val label = PositionEvalDisplay.format(scoreCp = null, mateIn = -7, userIsGote = false, evalDisplay = "cp")!!
        assertEquals("−7手詰", label.text)
        assertTrue(label.sign < 0)
    }

    // ─── mate_in=0 ─────────────────────────────────────────────────────────────

    @Test
    fun `mate0・先手ユーザー・先手が詰まされている（ply=0）は負け表示`() {
        val label = PositionEvalDisplay.format(scoreCp = null, mateIn = 0, userIsGote = false, evalDisplay = "cp", ply = 0)!!
        assertEquals("−詰み", label.text)
        assertTrue(label.sign < 0)
    }

    @Test
    fun `mate0・後手ユーザー・先手が詰まされている（ply=0）は勝ち表示`() {
        val label = PositionEvalDisplay.format(scoreCp = null, mateIn = 0, userIsGote = true, evalDisplay = "cp", ply = 0)!!
        assertEquals("+詰み", label.text)
        assertTrue(label.sign > 0)
    }

    @Test
    fun `mate0・後手ユーザー・後手が詰まされている（ply=1）は負け表示`() {
        val label = PositionEvalDisplay.format(scoreCp = null, mateIn = 0, userIsGote = true, evalDisplay = "cp", ply = 1)!!
        assertEquals("−詰み", label.text)
        assertTrue(label.sign < 0)
    }

    @Test
    fun `mate0・先手ユーザー・後手が詰まされている（ply=1）は勝ち表示`() {
        val label = PositionEvalDisplay.format(scoreCp = null, mateIn = 0, userIsGote = false, evalDisplay = "cp", ply = 1)!!
        assertEquals("+詰み", label.text)
        assertTrue(label.sign > 0)
    }

    @Test
    fun `mate非ゼロ・既存の符号ベース判定が維持される`() {
        val label = PositionEvalDisplay.format(scoreCp = null, mateIn = 5, userIsGote = false, evalDisplay = "cp", ply = 0)!!
        assertEquals("+5手詰", label.text)
        assertTrue(label.sign > 0)
    }

    // ─── 非表示 ────────────────────────────────────────────────────────────────

    @Test
    fun `scoreCpもmateInもnullなら非表示（null）`() {
        assertNull(PositionEvalDisplay.format(scoreCp = null, mateIn = null, userIsGote = false, evalDisplay = "cp"))
    }
}
