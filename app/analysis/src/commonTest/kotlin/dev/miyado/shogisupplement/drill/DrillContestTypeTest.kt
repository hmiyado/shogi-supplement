package dev.miyado.shogisupplement.drill

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DrillContestTypeTest {

    @Test
    fun 評価値の絶対値が800以上なら拮抗ではない() {
        assertFalse(DrillContestType.isCloseContest(cpBefore = 850, secondCp = 840, missedMateIn = null))
        assertFalse(DrillContestType.isCloseContest(cpBefore = -850, secondCp = -840, missedMateIn = null))
    }

    @Test
    fun 次善手との差が400以上なら拮抗ではない() {
        assertFalse(DrillContestType.isCloseContest(cpBefore = 100, secondCp = -350, missedMateIn = null))
    }

    @Test
    fun 詰みスコアは拮抗ではない() {
        assertFalse(DrillContestType.isCloseContest(cpBefore = 30_000, secondCp = 100, missedMateIn = null))
    }

    @Test
    fun 見落とした詰みがある局面は拮抗ではない() {
        assertFalse(DrillContestType.isCloseContest(cpBefore = null, secondCp = null, missedMateIn = 5))
    }

    @Test
    fun cpBeforeが無ければ拮抗と判定しない() {
        assertFalse(DrillContestType.isCloseContest(cpBefore = null, secondCp = null, missedMateIn = null))
    }

    @Test
    fun 評価値の差が小さければ拮抗() {
        assertTrue(DrillContestType.isCloseContest(cpBefore = 100, secondCp = 50, missedMateIn = null))
    }

    @Test
    fun 次善手が無くても評価値の絶対値が小さければ拮抗() {
        assertTrue(DrillContestType.isCloseContest(cpBefore = -200, secondCp = null, missedMateIn = null))
    }
}
