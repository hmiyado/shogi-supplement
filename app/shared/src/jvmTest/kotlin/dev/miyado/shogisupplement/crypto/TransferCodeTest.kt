package dev.miyado.shogisupplement.crypto

import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TransferCodeTest {

    @Test
    fun `ランダムな128bitシークレットは往復で完全一致する`() {
        repeat(50) {
            val secret = CryptographyRandom.Default.nextBytes(TRANSFER_SECRET_BYTES)
            val code = TransferCode.encode(secret)
            val decoded = TransferCode.decode(code)
            assertNotNull(decoded, "code=$code")
            assertEquals(secret.toList(), decoded.toList())
        }
    }

    @Test
    fun `全ゼロと全FFの境界値も往復する`() {
        val zero = ByteArray(TRANSFER_SECRET_BYTES)
        val ff = ByteArray(TRANSFER_SECRET_BYTES) { 0xFF.toByte() }
        assertEquals(zero.toList(), TransferCode.decode(TransferCode.encode(zero))?.toList())
        assertEquals(ff.toList(), TransferCode.decode(TransferCode.encode(ff))?.toList())
    }

    @Test
    fun `5文字ごとにハイフンで区切られる`() {
        val secret = ByteArray(TRANSFER_SECRET_BYTES) { it.toByte() }
        val code = TransferCode.encode(secret)
        val groups = code.split("-")
        // 26+1=27文字を5文字ずつ: 5,5,5,5,5,2
        assertEquals(listOf(5, 5, 5, 5, 5, 2), groups.map { it.length })
    }

    @Test
    fun `小文字や空白ハイフンを含んでいても正しく復元できる`() {
        val secret = ByteArray(TRANSFER_SECRET_BYTES) { (it * 7).toByte() }
        val code = TransferCode.encode(secret)
        val messy = "  " + code.lowercase().replace("-", " - ") + "  "
        assertEquals(secret.toList(), TransferCode.decode(messy)?.toList())
    }

    @Test
    fun `Crockfordの紛らわしい文字は正規化される`() {
        val secret = ByteArray(TRANSFER_SECRET_BYTES) { it.toByte() }
        val code = TransferCode.encode(secret)
        // 0→O, 1→I, 1→L の逆変換を人為的に混ぜても同じ結果に戻る
        val confused = code.replace('0', 'O').replace('1', 'I')
        assertEquals(secret.toList(), TransferCode.decode(confused)?.toList())
    }

    @Test
    fun `1文字破損したコードはチェックサム不一致でnullになる`() {
        val secret = ByteArray(TRANSFER_SECRET_BYTES) { (it * 3 + 1).toByte() }
        val code = TransferCode.encode(secret)
        val chars = code.toCharArray()
        // ハイフン以外の1文字を別の値へ差し替える
        val targetIndex = chars.indexOfFirst { it != '-' }
        val original = chars[targetIndex]
        val replacement = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".first { it != original }
        chars[targetIndex] = replacement
        assertNull(TransferCode.decode(String(chars)))
    }

    @Test
    fun `長さが違うコードはnull`() {
        assertNull(TransferCode.decode("0000-0000"))
        assertNull(TransferCode.decode(""))
    }

    @Test
    fun `アルファベット外の文字を含むコードはnull`() {
        val secret = ByteArray(TRANSFER_SECRET_BYTES)
        val code = TransferCode.encode(secret)
        assertNull(TransferCode.decode(code.replaceFirst(code.first { it != '-' }.toString(), "!")))
    }
}
