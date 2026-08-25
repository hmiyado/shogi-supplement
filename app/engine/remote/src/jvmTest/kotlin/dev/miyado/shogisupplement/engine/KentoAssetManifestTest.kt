package dev.miyado.shogisupplement.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KentoAssetManifestTest {

    private val json = """
        {
          "files": {
            "kento/study-worker.js": "AABBCC",
            "kento-assets/yo-1234/nn.bin": "ddeeff"
          }
        }
    """.trimIndent()

    @Test
    fun `載っているパスの期待ハッシュを小文字で返す`() {
        val parsed = KentoAssetManifest.parse(json)!!
        assertEquals("aabbcc", parsed.expected("kento/study-worker.js"))
        assertEquals("ddeeff", parsed.expected("kento-assets/yo-1234/nn.bin"))
    }

    @Test
    fun `載っていないパスはnullを返す`() {
        val parsed = KentoAssetManifest.parse(json)!!
        assertNull(parsed.expected("kento/webapp-bridge.js"))
    }

    @Test
    fun `JSONとして壊れていればnullを返す`() {
        assertNull(KentoAssetManifest.parse("{"))
        assertNull(KentoAssetManifest.parse(""))
    }

    @Test
    fun `filesキーが無ければnullを返す`() {
        assertNull(KentoAssetManifest.parse("""{"generated":"2026-08-15"}"""))
    }

    @Test
    fun `ハッシュ値が文字列でなければnullを返す`() {
        assertNull(KentoAssetManifest.parse("""{"files":{"kento/a.js":{"sha":"x"}}}"""))
    }

    @Test
    fun `大文字小文字の違いは一致とみなす`() {
        assertTrue(KentoAssetManifest.matches("aabbcc", "AABBCC"))
        assertTrue(KentoAssetManifest.matches("aabbcc", "aabbcc"))
    }

    @Test
    fun `期待ハッシュが無いパスは一致しない`() {
        assertFalse(KentoAssetManifest.matches(null, "aabbcc"))
    }

    @Test
    fun `値が違えば一致しない`() {
        assertFalse(KentoAssetManifest.matches("aabbcc", "aabbcd"))
    }
}
