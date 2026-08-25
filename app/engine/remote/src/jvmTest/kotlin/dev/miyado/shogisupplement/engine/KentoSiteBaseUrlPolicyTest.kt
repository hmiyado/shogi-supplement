package dev.miyado.shogisupplement.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [KentoSiteBaseUrlPolicy] の単体テスト。 */
class KentoSiteBaseUrlPolicyTest {

    @Test
    fun `httpスキームは末尾スラッシュを補って正規化`() {
        assertEquals("http://127.0.0.1:8925/", KentoSiteBaseUrlPolicy.normalize("http://127.0.0.1:8925"))
    }

    @Test
    fun `末尾スラッシュ済みでも二重にならない`() {
        assertEquals("http://127.0.0.1:8925/", KentoSiteBaseUrlPolicy.normalize("http://127.0.0.1:8925/"))
    }

    @Test
    fun `httpsスキームも許可する`() {
        assertEquals("https://example.com/", KentoSiteBaseUrlPolicy.normalize("https://example.com"))
    }

    @Test
    fun `前後の空白を除去する`() {
        assertEquals("http://127.0.0.1:8925/", KentoSiteBaseUrlPolicy.normalize("  http://127.0.0.1:8925  "))
    }

    @Test
    fun `末尾の連続スラッシュも1つに揃える`() {
        assertEquals("https://example.com/sub/", KentoSiteBaseUrlPolicy.normalize("https://example.com/sub//"))
    }

    @Test
    fun `空文字はnull`() {
        assertNull(KentoSiteBaseUrlPolicy.normalize(""))
    }

    @Test
    fun `空白のみはnull`() {
        assertNull(KentoSiteBaseUrlPolicy.normalize("   "))
    }

    @Test
    fun `スキーム無しはnull`() {
        assertNull(KentoSiteBaseUrlPolicy.normalize("127.0.0.1:8925"))
    }

    @Test
    fun `スキームのみでホスト無しはnull`() {
        assertNull(KentoSiteBaseUrlPolicy.normalize("http://"))
    }

    @Test
    fun `httpとhttps以外のスキームはnull`() {
        assertNull(KentoSiteBaseUrlPolicy.normalize("ftp://example.com"))
    }
}
