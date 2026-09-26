package dev.miyado.shogisupplement.webApp.report

import dev.miyado.shogisupplement.text.AppStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KentoInputTest {
    @Test
    fun blankInputIsRejected() {
        assertEquals(ParseOutcome.Error(AppStrings.KENTO_ERROR_EMPTY_INPUT), parseKifInput(" \n\t"))
    }

    @Test
    fun headersWithoutMovesAreRejected() {
        assertEquals(ParseOutcome.Error(AppStrings.KENTO_ERROR_NO_MOVES), parseKifInput("先手：先手の名前"))
    }

    @Test
    fun kifPreservesHeadersAndOriginalTextAlongsideUsiMoves() {
        val kif = "先手：先手の名前\n後手：後手の名前\n1 ７六歩(77)\n2 投了"
        val input = assertIs<ParseOutcome.Ok>(parseKifInput("\n$kif\n")).input
        assertEquals("startpos", input.baseSfenArg)
        assertEquals(listOf("7g7f"), input.moves)
        assertEquals("先手の名前", input.headers["先手"])
        assertEquals("後手の名前", input.headers["後手"])
        assertEquals(kif, input.kifText)
    }
}
