package dev.miyado.shogisupplement.crypto

import dev.miyado.shogisupplement.kifu.PrivateKifuFields
import dev.miyado.shogisupplement.kifu.KifParser
import dev.miyado.shogisupplement.kifu.KifuDecomposer
import dev.miyado.shogisupplement.kifu.KifuReconstructor
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PrivateEncCodecTest {

    @Test
    fun `分岐原文と分岐メモは暗号化バックアップで欠落せず公開復元には出ない`() = runTest {
        val variations = "変化：2手\r\n2 ８四歩(83)\r\n*分岐だけの秘密  \r\n&分岐しおり\r\n3 ２六歩(27)\r\n\r\n変化：3手\r\n3 ６八銀(79)\r\n*入れ子のメモ\r\n"
        val raw = "手合割：平手\r\n1 ７六歩(77)\r\n2 ３四歩(33)\r\n3 投了\r\n\r\n$variations"
        val original = KifParser().parse(raw)
        val parts = KifuDecomposer.decompose(raw, original)
        assertEquals(variations, parts.private.variationKif)
        val encrypted = PrivateEncCodec.encrypt(kEnc, parts.private, aad)
        val decrypted = PrivateEncCodec.decrypt(kEnc, encrypted, aad)
        val restored = KifuReconstructor.reconstruct(parts.public, decrypted)
        assertTrue(restored.endsWith(variations))
        assertEquals(original.moves, KifParser().parse(restored).moves)
        val second = KifuDecomposer.decompose(restored, KifParser().parse(restored))
        assertEquals(variations, second.private.variationKif)
        val masked = KifuReconstructor.reconstruct(parts.public, null)
        assertTrue(!masked.contains("変化"))
        assertTrue(!masked.contains("分岐だけの秘密"))
        assertTrue(!masked.contains("入れ子のメモ"))
    }

    @Test
    fun `局面メモはKIFから暗号化バックアップを経由して同じ位置へ復元される`() = runTest {
        val raw = "手合割：平手\r\n*開始メモ\r\n&開始\r\n1 ７六歩(77)\r\n*一手目  \r\n*\r\n&候補\r\n2 ３四歩(33)\r\n3 投了\r\n*終局メモ\r\n"
        val original = KifParser().parse(raw)
        val parts = KifuDecomposer.decompose(raw, original)
        val encrypted = PrivateEncCodec.encrypt(kEnc, parts.private, aad)
        val decrypted = PrivateEncCodec.decrypt(kEnc, encrypted, aad)
        val restored = KifParser().parse(KifuReconstructor.reconstruct(parts.public, decrypted))
        assertEquals(original.positionNotes, restored.positionNotes)
        assertEquals(original.moves, restored.moves)
        assertEquals(original.endReason, restored.endReason)
        val masked = KifParser().parse(KifuReconstructor.reconstruct(parts.public, null))
        assertEquals(emptyMap(), masked.positionNotes)
    }

    private val kEnc = ByteArray(32) { it.toByte() }
    private val fields = PrivateKifuFields(
        senteName = "太郎",
        goteName = "花子",
        extraHeaders = mapOf("棋戦" to "テスト対局", "場所" to "https://lishogi.org/abcd1234"),
        comments = listOf("*この手は定跡", "&しおり1"),
    )
    private val aad = "content-hash-abc123".encodeToByteArray()

    @Test
    fun `暗号化して復号すると元のPrivateKifuFieldsに戻る`() = runTest {
        val blob = PrivateEncCodec.encrypt(kEnc, fields, aad)
        val decrypted = PrivateEncCodec.decrypt(kEnc, blob, aad)
        assertEquals(fields, decrypted)
    }

    @Test
    fun `毎回ランダムnonceのため同じ入力でも暗号文は毎回変わる`() = runTest {
        val blob1 = PrivateEncCodec.encrypt(kEnc, fields, aad)
        val blob2 = PrivateEncCodec.encrypt(kEnc, fields, aad)
        assertEquals(false, blob1.contentEquals(blob2))
    }

    @Test
    fun `先頭1バイトは形式バージョン`() = runTest {
        val blob = PrivateEncCodec.encrypt(kEnc, fields, aad)
        assertEquals(PrivateEncCodec.VERSION, blob[0])
    }

    @Test
    fun `AADが異なると復号に失敗する`() = runTest {
        val blob = PrivateEncCodec.encrypt(kEnc, fields, aad)
        assertFails {
            PrivateEncCodec.decrypt(kEnc, blob, "違うcontent_hash".encodeToByteArray())
        }
    }

    @Test
    fun `鍵が異なると復号に失敗する`() = runTest {
        val blob = PrivateEncCodec.encrypt(kEnc, fields, aad)
        val wrongKey = ByteArray(32) { (it + 1).toByte() }
        assertFails { PrivateEncCodec.decrypt(wrongKey, blob, aad) }
    }

    @Test
    fun `暗号文が改ざんされると復号に失敗する（認証タグで検出）`() = runTest {
        val blob = PrivateEncCodec.encrypt(kEnc, fields, aad)
        val tampered = blob.copyOf()
        tampered[tampered.size - 1] = (tampered[tampered.size - 1] + 1).toByte()
        assertFails { PrivateEncCodec.decrypt(kEnc, tampered, aad) }
    }

    @Test
    fun `未対応の形式バージョンは拒否される`() = runTest {
        val blob = PrivateEncCodec.encrypt(kEnc, fields, aad)
        val badVersion = blob.copyOf()
        badVersion[0] = 99
        assertFailsWith<IllegalArgumentException> { PrivateEncCodec.decrypt(kEnc, badVersion, aad) }
    }


    @Test
    fun `ゴールデン- 固定nonceでの暗号化結果は固定バイト列と一致する`() = runTest {
        val fixedNonce = ByteArray(12) { it.toByte() }
        val plaintext = "golden-fixture".encodeToByteArray()
        val blob = PrivateEncCodec.encryptWithFixedNonceForGoldenTest(
            kEnc = kEnc,
            plaintext = plaintext,
            aad = aad,
            nonce = fixedNonce,
        )
        assertEquals(GOLDEN_HEX, blob.toHexString())
    }

    companion object {
        private const val GOLDEN_HEX =
            "01000102030405060708090a0b206dba7fa08bef7de439e3fec38ceadeb9c1bc65d13c744fc8476413e2bf"
    }
}

private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
