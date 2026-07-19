package dev.miyado.shogisupplement.blunder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlunderJudgeTest {

    // ---- 勝率変換 ----

    @Test
    fun `勝率変換 - cp0は50パーセント`() {
        assertEquals(0.5, BlunderJudge.winProb(0), 1e-9)
    }

    @Test
    fun `勝率変換 - シグモイド 1÷（1+exp（-cp÷600））`() {
        // cp=600 → 1/(1+e^-1) ≈ 0.7311
        assertEquals(0.7310585786300049, BlunderJudge.winProb(600), 1e-9)
        assertEquals(1 - 0.7310585786300049, BlunderJudge.winProb(-600), 1e-9)
    }

    @Test
    fun `mateスコアのcp変換 - ±（30000-n）`() {
        assertEquals(29997, BlunderJudge.toCp(Score.Mate(3)))
        assertEquals(-29995, BlunderJudge.toCp(Score.Mate(-5)))
        assertEquals(-30000, BlunderJudge.toCp(Score.Mate(0)))
    }

    @Test
    fun `cpスコアは±30000にクランプされる`() {
        assertEquals(30000, BlunderJudge.toCp(Score.Cp(99999)))
        assertEquals(-30000, BlunderJudge.toCp(Score.Cp(-99999)))
    }

    // ---- スイング悪手 ----

    @Test
    fun `スイング - 500cp損かつ勝率5-95かつ指した後マイナスは悪手`() {
        // 指す前 +100（自分視点）→ 指した後 +400（相手視点）= 自分視点-400。損失500cp
        val v = BlunderJudge.judge(Score.Cp(100), Score.Cp(400))
        assertTrue(v.isBlunder)
        assertEquals(BlunderType.EVAL_SWING, v.type)
        assertEquals(500, v.lossCp)
    }

    @Test
    fun `スイング - 損失500cp未満は悪手でない`() {
        val v = BlunderJudge.judge(Score.Cp(100), Score.Cp(399))
        assertFalse(v.isBlunder)
    }

    @Test
    fun `スイング - 指す前勝率95超（消化試合）は悪手でない`() {
        // cp=2000 → 勝率96.5% > 95%。損失2100cp・指した後マイナスでも除外
        val v = BlunderJudge.judge(Score.Cp(2000), Score.Cp(100))
        assertFalse(v.isBlunder)
    }

    @Test
    fun `スイング - 指す前勝率5未満（既に敗勢）は悪手でない`() {
        val v = BlunderJudge.judge(Score.Cp(-2000), Score.Cp(2500))
        assertFalse(v.isBlunder)
    }

    @Test
    fun `スイング - 指した後もプラスなら悪手でない`() {
        // +800 → 相手視点-300 = 自分視点+300。500cp損だがまだ優勢
        val v = BlunderJudge.judge(Score.Cp(800), Score.Cp(-300))
        assertFalse(v.isBlunder)
    }

    // ---- 詰み見逃し ----

    @Test
    fun `詰み見逃し - 詰みを逃して詰みが消えたら悪手`() {
        val v = BlunderJudge.judge(Score.Mate(5), Score.Cp(-300))
        assertTrue(v.isBlunder)
        assertEquals(BlunderType.MATE_MISS, v.type)
    }

    @Test
    fun `詰み見逃し - 詰みを維持していれば悪手でない`() {
        // 指した後、相手視点で mate -4（相手が詰まされる）= 自分の詰み継続
        val v = BlunderJudge.judge(Score.Mate(5), Score.Mate(-4))
        assertFalse(v.isBlunder)
    }

    @Test
    fun `詰み見逃し - 詰みから相手の詰み筋に入っても詰み見逃しとして判定`() {
        val v = BlunderJudge.judge(Score.Mate(3), Score.Mate(6))
        assertTrue(v.isBlunder)
        assertEquals(BlunderType.MATE_MISS, v.type)
    }

    // ---- 頓死 ----

    @Test
    fun `頓死 - 勝負が残る局面から被詰みに入ったら悪手`() {
        // 指す前 -200cp（勝負は残る）→ 指した後、相手にmate 7
        val v = BlunderJudge.judge(Score.Cp(-200), Score.Mate(7))
        assertTrue(v.isBlunder)
        assertEquals(BlunderType.SUDDEN_DEATH, v.type)
    }

    @Test
    fun `頓死 - 既に大差の負けからの被詰みは頓死でない`() {
        // -2500cp（勝率1.5% < 5%）からの被詰み: 頓死でもスイングでもない
        val v = BlunderJudge.judge(Score.Cp(-2500), Score.Mate(7))
        assertFalse(v.isBlunder)
    }

    @Test
    fun `頓死 - 負け局面でも勝率5パーセント以上の被詰みはスイング悪手になる`() {
        // Python is_blunder_v1 と同一挙動: -1500cp（勝率7.6%）からの被詰みは
        // 頓死条件(cp>-500)は外れるがスイング条件に該当する
        val v = BlunderJudge.judge(Score.Cp(-1500), Score.Mate(7))
        assertTrue(v.isBlunder)
        assertEquals(BlunderType.EVAL_SWING, v.type)
    }

    @Test
    fun `頓死 - 既に被詰みの局面からは頓死としない`() {
        val v = BlunderJudge.judge(Score.Mate(-5), Score.Mate(3))
        assertFalse(v.isBlunder)
    }
}
