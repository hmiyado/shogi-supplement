package dev.miyado.shogisupplement.ui.report

import dev.miyado.shogisupplement.gameshare.GameShareBranch
import dev.miyado.shogisupplement.gameshare.GameShareSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

class GameShareSnapshotViewTest {
    private fun snapshot(
        userSide: String = "sente",
        selectedPly: Int = 2,
        branch: GameShareBranch? = null,
    ) = GameShareSnapshot(
        id = "AbCdEfGhIjKlMnOpQrStUv",
        initialSfen = "sfen",
        mainline = listOf("7g7f", "3c3d", "2g2f"),
        selectedPly = selectedPly,
        branch = branch,
        userSide = userSide,
        flip = false,
        winner = null,
        endReason = null,
    )

    @Test
    fun opensSelectedPlyOnFullMainline() {
        assertEquals(listOf("7g7f", "3c3d", "2g2f"), snapshot(selectedPly = 3).selectedPath())
        assertEquals(listOf("7g7f"), snapshot(selectedPly = 1).selectedPath())
    }

    @Test
    fun opensSelectedPlyInsideBranch() {
        val branch = GameShareBranch(originPly = 1, moves = listOf("2g2f", "8c8d"))
        assertEquals(
            listOf("7g7f", "2g2f", "8c8d"),
            snapshot(selectedPly = 3, branch = branch).selectedPath(),
        )
    }

    @Test
    fun playersUseFixedLabelsNotStoredNames() {
        assertEquals("▲自分　△相手", snapshot("sente").playersLine("自分", "相手"))
        assertEquals("▲相手　△自分", snapshot("gote").playersLine("自分", "相手"))
    }
}
