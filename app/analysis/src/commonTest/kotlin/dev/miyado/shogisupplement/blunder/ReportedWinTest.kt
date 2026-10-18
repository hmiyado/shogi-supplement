package dev.miyado.shogisupplement.blunder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ReportedWinTest {
    @Test fun distinguishesReportedWinFromProvenMate() {
        assertEquals(PositionEvalDisplay.EvalText.Win(7), PositionEvalDisplay.format(null, 7, false, "cp", mateVerified = false)?.value)
        assertEquals(PositionEvalDisplay.EvalText.Win(-7), PositionEvalDisplay.format(null, 7, true, "cp", mateVerified = false)?.value)
        assertEquals(PositionEvalDisplay.EvalText.Mate(7), PositionEvalDisplay.format(null, 7, false, "cp", mateVerified = true)?.value)
    }
    @Test fun unverifiedZeroNeverClaimsCheckmate() {
        assertEquals(PositionEvalDisplay.EvalText.WinTerminal(false), PositionEvalDisplay.format(null, 0, false, "cp", ply = 0, mateVerified = false)?.value)
        assertEquals(PositionEvalDisplay.EvalText.WinTerminal(true), PositionEvalDisplay.format(null, 0, true, "cp", ply = 0, mateVerified = false)?.value)
    }
    @Test fun reportedWinIsNotClassifiedAsMissedMate() {
        assertNotEquals(BlunderType.MATE_MISS, BlunderJudge.judge(Score.Mate(7, verified = false), Score.Cp(0)).type)
        assertNotEquals(BlunderType.SUDDEN_DEATH, BlunderJudge.judge(Score.Cp(0), Score.Mate(7, verified = false)).type)
    }
}
