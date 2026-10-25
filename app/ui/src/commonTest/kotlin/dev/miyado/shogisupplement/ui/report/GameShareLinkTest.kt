package dev.miyado.shogisupplement.ui.report

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class GameShareLinkTest {
    private val id = "AbCdEfGhIjKlMnOpQrStUv"

    @Test
    fun buildsAndParsesCanonicalGameUrl() {
        val url = GameShareLink.urlFor(id)
        assertEquals("https://shogi-supplement.miyado.dev/kento.html?game=v1&id=$id", url)
        assertIs<GameShareParse.Valid>(GameShareLink.parseQuery(url!!.substringAfter('?')))
    }

    @Test
    fun queryJoiningAndLegacyFragmentRemainSupported() {
        assertEquals("https://example.test/kento.html?debug=1&game=v1&id=$id",
            GameShareLink.urlFor(id, "https://example.test/kento.html?debug=1"))
        assertEquals(GameShareParse.Valid(id), GameShareLink.parseQuery("?debug=1&game=v1&id=$id"))
        assertEquals(GameShareParse.Valid(id), GameShareLink.parseFragment("#game=v1&id=$id"))
        assertEquals(GameShareParse.Absent, GameShareLink.parseQuery("?debug=1"))
        assertEquals(GameShareParse.Invalid, GameShareLink.parseQuery("?game=v2&id=$id"))
    }

    @Test
    fun rejectsMalformedVersionAndId() {
        assertIs<GameShareParse.Invalid>(
            GameShareLink.parseFragment("#game=v2&id=$id"),
        )
        assertIs<GameShareParse.Invalid>(
            GameShareLink.parseFragment("#game=v1&id=short"),
        )
        assertIs<GameShareParse.Invalid>(
            GameShareLink.parseFragment("#game=v1&id=$id&id=$id"),
        )
        assertIs<GameShareParse.Invalid>(
            GameShareLink.parseFragment("#game=v1"),
        )
        assertNull(GameShareLink.urlFor("short"))
    }

    @Test
    fun ignoresPositionKeysButKeepsGameLinkPriority() {
        assertIs<GameShareParse.Valid>(
            GameShareLink.parseFragment("#game=v1&id=$id&position=v1&flip=1"),
        )
        assertIs<GameShareParse.Absent>(
            GameShareLink.parseFragment("#position=v1&sfen=x&flip=0"),
        )
    }
}
