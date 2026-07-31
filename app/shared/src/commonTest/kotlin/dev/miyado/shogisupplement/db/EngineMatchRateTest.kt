package dev.miyado.shogisupplement.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EngineMatchRateTest {

    @Test
    fun `userSideがnullなら計算不能`() {
        val result = EngineMatchRate.compute(
            movesUsi = listOf("7g7f"),
            positionEvals = listOf(PositionEvalRow(ply = 0, scoreCp = 0, mateIn = null, bestUsi = "7g7f")),
            userSide = null,
        )
        assertNull(result)
    }

    @Test
    fun `対象手がゼロならnull`() {
        val result = EngineMatchRate.compute(
            movesUsi = listOf("7g7f"),
            positionEvals = emptyList(),
            userSide = "sente",
        )
        assertNull(result)
    }

    @Test
    fun `pv1一致は一致としてカウントされる`() {
        val result = EngineMatchRate.compute(
            movesUsi = listOf("7g7f"),
            positionEvals = listOf(PositionEvalRow(ply = 0, scoreCp = 0, mateIn = null, bestUsi = "7g7f")),
            userSide = "sente",
        )
        assertEquals(1.0, result?.rate)
        assertEquals(1, result?.sampleMoves)
    }

    @Test
    fun `pv2一致もtop2一致としてカウントされる`() {
        val result = EngineMatchRate.compute(
            movesUsi = listOf("7g7f"),
            positionEvals = listOf(
                PositionEvalRow(ply = 0, scoreCp = 0, mateIn = null, bestUsi = "2g2f", secondUsi = "7g7f"),
            ),
            userSide = "sente",
        )
        assertEquals(1.0, result?.rate)
    }

    @Test
    fun `pv1にもpv2にも一致しない手は不一致`() {
        val result = EngineMatchRate.compute(
            movesUsi = listOf("7g7f"),
            positionEvals = listOf(
                PositionEvalRow(ply = 0, scoreCp = 0, mateIn = null, bestUsi = "2g2f", secondUsi = "3g3f"),
            ),
            userSide = "sente",
        )
        assertEquals(0.0, result?.rate)
    }

    @Test
    fun `secondUsiがnullの旧データはpv1のみで判定する`() {
        val result = EngineMatchRate.compute(
            movesUsi = listOf("7g7f"),
            positionEvals = listOf(
                PositionEvalRow(ply = 0, scoreCp = 0, mateIn = null, bestUsi = "2g2f", secondUsi = null),
            ),
            userSide = "sente",
        )
        assertEquals(0.0, result?.rate)
    }

    @Test
    fun `相手側の手は分母に含めない`() {
        // t=0: sente(7g7f), t=1: gote(3c3d). userSide=sente のときt=1は対象外。
        val result = EngineMatchRate.compute(
            movesUsi = listOf("7g7f", "3c3d"),
            positionEvals = listOf(
                PositionEvalRow(ply = 0, scoreCp = 0, mateIn = null, bestUsi = "7g7f"),
                PositionEvalRow(ply = 1, scoreCp = 0, mateIn = null, bestUsi = "8c8d"),
            ),
            userSide = "sente",
        )
        assertEquals(1.0, result?.rate)
        assertEquals(1, result?.sampleMoves)
    }

    @Test
    fun `複数手の一致率を平均する`() {
        // sente側3手（ply 0, 2, 4）中2手一致
        val result = EngineMatchRate.compute(
            movesUsi = listOf("7g7f", "3c3d", "2g2f", "8c8d", "2f2e"),
            positionEvals = listOf(
                PositionEvalRow(ply = 0, scoreCp = 0, mateIn = null, bestUsi = "7g7f"), // moves[0]と一致
                PositionEvalRow(ply = 2, scoreCp = 0, mateIn = null, bestUsi = "2g2f"), // moves[2]と一致
                PositionEvalRow(ply = 4, scoreCp = 0, mateIn = null, bestUsi = "6i7h"), // moves[4]と不一致
            ),
            userSide = "sente",
        )
        assertEquals(3, result?.sampleMoves)
        assertEquals(2, result?.matched)
        assertEquals(2.0 / 3.0, result?.rate)
    }
}
