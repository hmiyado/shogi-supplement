package dev.miyado.shogisupplement.pipeline

import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.engine.PvInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgressiveReportStateTest {

    /** multipv=1 のみ・pv空のダミー結果（テスト用の最小構成）。 */
    private fun cp(value: Int): List<PvInfo> = listOf(PvInfo(multipv = 1, score = Score.Cp(value), pv = emptyList(), nodes = 0L))

    private val moves3 = listOf("7g7f", "3c3d", "8h2b")

    @Test
    fun `初期状態は何も反映されておらず総数はmoves数+1`() {
        val s = ProgressiveReportState.initial(moves3)
        assertEquals(4, s.total)
        assertEquals(0, s.confirmedThrough)
        assertEquals(0, s.doneCount)
        assertFalse(s.isComplete)
        assertTrue(s.revealedEvals.isEmpty())
    }

    @Test
    fun `out-of-orderで先の局面だけ届いてもバッファされ反映されない`() {
        val s = ProgressiveReportState.initial(moves3).withPosition(2, cp(50))
        assertEquals(1, s.doneCount, "到着済みとしては数える")
        assertEquals(0, s.confirmedThrough, "ply0が未到着のためwatermarkは進まない")
    }

    @Test
    fun `gap-fillで欠けていたplyが届くと連続区間が一括反映される`() {
        var s = ProgressiveReportState.initial(moves3)
        s = s.withPosition(2, cp(50))
        s = s.withPosition(1, cp(30))
        assertEquals(0, s.confirmedThrough, "ply0がまだ無いので1,2があっても反映されない")

        s = s.withPosition(0, cp(10))
        assertEquals(3, s.confirmedThrough, "ply0到着で0,1,2が連続確定し一括で反映される")
        assertEquals(3, s.doneCount)
    }

    @Test
    fun `同じplyの再通知は無視される（冪等）`() {
        var s = ProgressiveReportState.initial(moves3)
        s = s.withPosition(0, cp(10))
        val afterFirst = s
        s = s.withPosition(0, cp(999)) // 別の値で再送されても既知のplyは上書きしない
        assertEquals(afterFirst, s)
    }

    @Test
    fun `全局面が一括で届いても順序どおりに確定する（サーバー解析の一括到着ケース）`() {
        var s = ProgressiveReportState.initial(moves3)
        // NDJSON最終行はploy順とは限らないため、あえて逆順で投入する。
        for (ply in s.total - 1 downTo 0) {
            s = s.withPosition(ply, cp(ply * 10))
        }
        assertTrue(s.isComplete)
        assertEquals(s.total, s.confirmedThrough)
        assertEquals(s.total, s.doneCount)
    }

    @Test
    fun `範囲外のplyは無視される`() {
        val s = ProgressiveReportState.initial(moves3).withPosition(99, cp(0))
        assertEquals(0, s.doneCount)
        assertEquals(0, s.confirmedThrough)
    }

    @Test
    fun `doneCountとconfirmedThroughは順不同着で乖離しうる`() {
        val s = ProgressiveReportState.initial(moves3).withPosition(3, cp(0))
        assertEquals(1, s.doneCount)
        assertEquals(0, s.confirmedThrough, "N（到着数）とwatermark（連続確定区間）は別軸")
    }

    @Test
    fun `全局面が順に届くと完了フラグが立つ`() {
        var s = ProgressiveReportState.initial(moves3)
        assertFalse(s.isComplete)
        for (ply in 0 until s.total) {
            s = s.withPosition(ply, cp(0))
        }
        assertTrue(s.isComplete)
    }

    @Test
    fun `反映済み区間内の悪手だけがrevealedBlunderPliesに載る`() {
        // BlunderJudgeTestの「スイング」ケースと同じ数値（100→400で損失500cp・悪手）を流用。
        // ply1(moves[0]相当)は悪手、ply2(moves[1]相当)は損失350cpで悪手にならない。
        var s = ProgressiveReportState.initial(moves3)
        s = s.withPosition(0, cp(100))
        s = s.withPosition(1, cp(400))
        s = s.withPosition(2, cp(-50))
        assertEquals(3, s.confirmedThrough)
        assertEquals(setOf(1), s.revealedBlunderPlies)
    }

    @Test
    fun `未反映区間の悪手はwatermarkが追いつくまでrevealedBlunderPliesに出ない`() {
        var s = ProgressiveReportState.initial(moves3)
        s = s.withPosition(1, cp(400)) // ply0が無いのでバッファのまま
        s = s.withPosition(2, cp(-50))
        assertEquals(0, s.confirmedThrough)
        assertTrue(s.revealedBlunderPlies.isEmpty())
    }
}
