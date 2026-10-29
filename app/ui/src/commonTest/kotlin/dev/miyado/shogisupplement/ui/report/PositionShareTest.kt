package dev.miyado.shogisupplement.ui.report

import dev.miyado.shogisupplement.board.ShogiBoard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class PositionShareTest {
    private val startSfen = ShogiBoard().toSfen()

    @Test
    fun parseAcceptsHashPrefixAndPercentEncoding() {
        val sfen = startSfen.replace(" ", "%20")
        val parsed = assertIs<PositionShareParse.Valid>(
            PositionShare.parseFragment("#position=v1&sfen=$sfen&flip=false"),
        )
        assertEquals(startSfen, parsed.position.sfen)
        assertEquals(false, parsed.position.flip)
    }

    @Test
    fun absentWhenNoFragment() {
        assertSame(PositionShareParse.Absent, PositionShare.parseFragment(null))
        assertSame(PositionShareParse.Absent, PositionShare.parseFragment(""))
        assertSame(PositionShareParse.Absent, PositionShare.parseFragment("#"))
        assertSame(PositionShareParse.Absent, PositionShare.parseFragment("#sfen=abc"))
    }

    @Test
    fun rejectsUnknownVersionAndMalformedKeys() {
        val encoded = startSfen.replace(" ", "%20")
        assertSame(
            PositionShareParse.Invalid,
            PositionShare.parseFragment("position=v2&sfen=$encoded&flip=0"),
        )
        assertSame(
            PositionShareParse.Invalid,
            PositionShare.parseFragment("position=v1&sfen=$encoded&flip=0&junk"),
        )
        assertSame(
            PositionShareParse.Invalid,
            PositionShare.parseFragment("position=v1&position=v1&sfen=$encoded"),
        )
        assertSame(
            PositionShareParse.Invalid,
            PositionShare.parseFragment("position=v1&sfen=$encoded&flip=2"),
        )
        assertSame(
            PositionShareParse.Invalid,
            PositionShare.parseFragment("position=v1&sfen=%E6%AD"),
        )
    }

    @Test
    fun invalidUtf8PercentEncodingIsInvalidInsteadOfThrowing() {
        assertSame(
            PositionShareParse.Invalid,
            PositionShare.parseFragment("position=v1&sfen=%FF"),
        )
        assertSame(
            PositionShareParse.Invalid,
            PositionShare.parseFragment("%FF=v1&sfen=%FF"),
        )
    }

    @Test
    fun overlongFragmentAndSfenAreRejected() {
        assertSame(
            PositionShareParse.Invalid,
            PositionShare.parseFragment("#position=v1&sfen=" + "a".repeat(5_000)),
        )
        assertNull(PositionShare.position("b - 1".padEnd(300, ' '), false))
    }

    @Test
    fun rejectsInvalidSfenBounds() {
        val encoded = startSfen.replace(" ", "%20")
        assertSame(
            PositionShareParse.Invalid,
            PositionShare.parseFragment("position=v1&sfen=$encoded&flip=0&extra=1&sfen=$encoded"),
        )
        assertNull(PositionShare.position("8k/9/9/9/9/9/9/9/8K b - 0", false))
        assertNull(PositionShare.position("8k/9/9/9/9/9/9/9/8K x - 1", false))
        assertNull(PositionShare.position("8k/9/9/9/9/9/9/9/8K b 19P 1", false))
        assertNull(PositionShare.position("8k/9/9/9/9/9/9/9/8K b 999999999999999999P 1", false))
        assertNull(PositionShare.position("PP5k1/PP5K1/PP7/PP7/PP7/PP7/PP7/PP7/PP7 b 1P 1", false))
        assertNull(PositionShare.position("8k/9/9/9/9/9/9/9/8K b 0P 1", false))
        assertNull(PositionShare.position("8k/9/9/9/9/9/9/9/8K b - 0", false))
        assertNull(PositionShare.position("9/9/9/9/9/9/9/9/9 b - 1", false))
        assertNull(PositionShare.position("8k/9/9/9/9/9/9/9/8K b - 1000000", false))
    }

}
