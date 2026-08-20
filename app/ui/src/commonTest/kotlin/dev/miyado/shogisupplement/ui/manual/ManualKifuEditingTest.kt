package dev.miyado.shogisupplement.ui.manual

import kotlin.test.Test
import kotlin.test.assertEquals

class ManualKifuEditingTest {

    @Test
    fun `a move from an earlier position replaces the later mainline`() {
        assertEquals(
            listOf("7g7f", "3c3d", "2g2f"),
            manualLineAfterMove(listOf("7g7f", "3c3d", "8c8d", "2g2f"), 2, "2g2f"),
        )
    }

    @Test
    fun `resignation from an earlier position removes later mainline`() {
        assertEquals(
            listOf("7g7f", "3c3d"),
            manualLineAfterResign(listOf("7g7f", "3c3d", "2g2f"), 2),
        )
    }

    @Test
    fun `a move after resignation reopens the truncated mainline`() {
        val resignedLine = manualLineAfterResign(listOf("7g7f", "3c3d", "2g2f"), 2)

        assertEquals(
            listOf("7g7f", "3c3d", "8c8d"),
            manualLineAfterMove(resignedLine, resignedLine.size, "8c8d"),
        )
    }

    @Test
    fun `resigning creates a saveable draft at the displayed position`() {
        val draft = ManualKifuDraft(
            moves = listOf("7g7f", "3c3d", "2g2f"),
            senteName = "先手",
            goteName = "後手",
            startedAt = "2026/08/20 23:30",
            place = "東京",
            resigned = false,
        ).resignedAt(2)

        assertEquals(listOf("7g7f", "3c3d"), draft.moves)
        assertEquals(true, draft.resigned)
        assertEquals("先手", draft.senteName)
        assertEquals("東京", draft.place)
    }
}
