package dev.miyado.shogisupplement.blunder

import dev.miyado.shogisupplement.ui.text.resolveText

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@org.robolectric.annotation.Config(sdk = [34], application = android.app.Application::class)
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
class PositionEvalDisplayTest {


    @Test
    fun `cpモード・先手ユーザー・先手優勢はプラス表示`() = kotlinx.coroutines.test.runTest {
        val label = PositionEvalDisplay.format(scoreCp = 320, mateIn = null, userIsGote = false, evalDisplay = "cp")!!
        assertEquals("+320", label.resolveText())
        assertTrue(label.sign > 0)
    }

    @Test
    fun `cpモード・先手ユーザー・後手優勢はマイナス表示`() = kotlinx.coroutines.test.runTest {
        val label = PositionEvalDisplay.format(scoreCp = -450, mateIn = null, userIsGote = false, evalDisplay = "cp")!!
        assertEquals("−450", label.resolveText())
        assertTrue(label.sign < 0)
    }

    @Test
    fun `cpモード・後手ユーザーは符号反転する`() = kotlinx.coroutines.test.runTest {
        // 先手視点 -450（後手優勢）→ 後手ユーザーには +450
        val label = PositionEvalDisplay.format(scoreCp = -450, mateIn = null, userIsGote = true, evalDisplay = "cp")!!
        assertEquals("+450", label.resolveText())
        assertTrue(label.sign > 0)
    }

    @Test
    fun `cpモード・互角はプラスマイナス0で中立`() = kotlinx.coroutines.test.runTest {
        val label = PositionEvalDisplay.format(scoreCp = 0, mateIn = null, userIsGote = false, evalDisplay = "cp")!!
        assertEquals("±0", label.resolveText())
        assertEquals(0, label.sign)
    }


    @Test
    fun `wpモード・互角は勝率50`() = kotlinx.coroutines.test.runTest {
        val label = PositionEvalDisplay.format(scoreCp = 0, mateIn = null, userIsGote = false, evalDisplay = "wp")!!
        assertEquals("勝率50%", label.resolveText())
        assertEquals(0, label.sign)
    }

    @Test
    fun `wpモード・s1254換算（cp=1254で約73）`() = kotlinx.coroutines.test.runTest {
        val label = PositionEvalDisplay.format(scoreCp = 1254, mateIn = null, userIsGote = false, evalDisplay = "wp")!!
        assertEquals("勝率73%", label.resolveText())
        assertTrue(label.sign > 0)
    }

    @Test
    fun `wpモード・後手ユーザーは符号反転してから換算する`() = kotlinx.coroutines.test.runTest {
        // 先手視点 +1254 → 後手ユーザーには -1254 → 勝率 27%
        val label = PositionEvalDisplay.format(scoreCp = 1254, mateIn = null, userIsGote = true, evalDisplay = "wp")!!
        assertEquals("勝率27%", label.resolveText())
        assertTrue(label.sign < 0)
    }


    @Test
    fun `詰み・先手ユーザーが詰ます側は勝ち表示`() = kotlinx.coroutines.test.runTest {
        val label = PositionEvalDisplay.format(scoreCp = null, mateIn = 5, userIsGote = false, evalDisplay = "cp")!!
        assertEquals("+5手詰", label.resolveText())
        assertTrue(label.sign > 0)
    }

    @Test
    fun `詰み・後手ユーザーは mate_in も符号反転する`() = kotlinx.coroutines.test.runTest {
        // 先手視点 mate_in = -3（後手が詰ます）→ 後手ユーザーには勝ち
        val label = PositionEvalDisplay.format(scoreCp = null, mateIn = -3, userIsGote = true, evalDisplay = "cp")!!
        assertEquals("+3手詰", label.resolveText())
        assertTrue(label.sign > 0)
    }

    @Test
    fun `詰み・詰まされる側は負け表示`() = kotlinx.coroutines.test.runTest {
        val label = PositionEvalDisplay.format(scoreCp = null, mateIn = -7, userIsGote = false, evalDisplay = "cp")!!
        assertEquals("−7手詰", label.resolveText())
        assertTrue(label.sign < 0)
    }


    @Test
    fun `mate0・先手ユーザー・先手が詰まされている（ply=0）は負け表示`() = kotlinx.coroutines.test.runTest {
        val label = PositionEvalDisplay.format(scoreCp = null, mateIn = 0, userIsGote = false, evalDisplay = "cp", ply = 0)!!
        assertEquals("−詰み", label.resolveText())
        assertTrue(label.sign < 0)
    }

    @Test
    fun `mate0・後手ユーザー・先手が詰まされている（ply=0）は勝ち表示`() = kotlinx.coroutines.test.runTest {
        val label = PositionEvalDisplay.format(scoreCp = null, mateIn = 0, userIsGote = true, evalDisplay = "cp", ply = 0)!!
        assertEquals("+詰み", label.resolveText())
        assertTrue(label.sign > 0)
    }

    @Test
    fun `mate0・後手ユーザー・後手が詰まされている（ply=1）は負け表示`() = kotlinx.coroutines.test.runTest {
        val label = PositionEvalDisplay.format(scoreCp = null, mateIn = 0, userIsGote = true, evalDisplay = "cp", ply = 1)!!
        assertEquals("−詰み", label.resolveText())
        assertTrue(label.sign < 0)
    }

    @Test
    fun `mate0・先手ユーザー・後手が詰まされている（ply=1）は勝ち表示`() = kotlinx.coroutines.test.runTest {
        val label = PositionEvalDisplay.format(scoreCp = null, mateIn = 0, userIsGote = false, evalDisplay = "cp", ply = 1)!!
        assertEquals("+詰み", label.resolveText())
        assertTrue(label.sign > 0)
    }

    @Test
    fun `mate非ゼロ・既存の符号ベース判定が維持される`() = kotlinx.coroutines.test.runTest {
        val label = PositionEvalDisplay.format(scoreCp = null, mateIn = 5, userIsGote = false, evalDisplay = "cp", ply = 0)!!
        assertEquals("+5手詰", label.resolveText())
        assertTrue(label.sign > 0)
    }


    @Test
    fun `scoreCpもmateInもnullなら非表示（null）`() = kotlinx.coroutines.test.runTest {
        assertNull(PositionEvalDisplay.format(scoreCp = null, mateIn = null, userIsGote = false, evalDisplay = "cp"))
    }
}
