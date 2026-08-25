package dev.miyado.shogisupplement.crypto

import dev.miyado.shogisupplement.kifu.PrivateKifuFields
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * jvmTestの[PrivateEncCodecTest]と同一の入力・同一の期待値ハードコード。
 * iOS実装（CryptoKit/OpenSSL3経由のcryptography-provider-optimal）がJVM実装（JDK provider）と
 * 完全に同一のワイヤフォーマット（バージョンバイト・nonce・ciphertext|tagレイアウト）を
 * 生成することを、各プラットフォームが独立にこの固定値と一致することで確認する
 * （タスク指示「iOS/JVM間で同一形式になることのテスト」）。
 */
class PrivateEncCodecIosTest {

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
    fun `ゴールデン- 固定nonceでの暗号化結果はJVMと同一のバイト列になる`() = runTest {
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
        // jvmTest/PrivateEncCodecTest.GOLDEN_HEX と同じ値（同一入力に対する期待値）。
        private const val GOLDEN_HEX =
            "01000102030405060708090a0b206dba7fa08bef7de439e3fec38ceadeb9c1bc65d13c744fc8476413e2bf"
    }
}

private fun ByteArray.toHexString(): String = joinToString("") {
    val hex = (it.toInt() and 0xFF).toString(16)
    if (hex.length == 1) "0$hex" else hex
}
