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
}
