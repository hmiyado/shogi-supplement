package dev.miyado.shogisupplement.crypto

import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TransferSecretKeysTest {

    private val secret = ByteArray(TRANSFER_SECRET_BYTES) { (it + 1).toByte() }

    @Test
    fun `K_authとK_encは32バイト`() = runTest {
        assertEquals(32, TransferSecretKeys.deriveAuthKey(secret).size)
        assertEquals(32, TransferSecretKeys.deriveEncKey(secret).size)
    }

    @Test
    fun `同じSからは常に同じK_authとK_encが決定的に導出される`() = runTest {
        val auth1 = TransferSecretKeys.deriveAuthKey(secret)
        val auth2 = TransferSecretKeys.deriveAuthKey(secret)
        assertEquals(auth1.toList(), auth2.toList())

        val enc1 = TransferSecretKeys.deriveEncKey(secret)
        val enc2 = TransferSecretKeys.deriveEncKey(secret)
        assertEquals(enc1.toList(), enc2.toList())
    }

    @Test
    fun `K_authとK_encは互いに独立（同じSからでも一致しない）`() = runTest {
        val auth = TransferSecretKeys.deriveAuthKey(secret)
        val enc = TransferSecretKeys.deriveEncKey(secret)
        assertNotEquals(auth.toList(), enc.toList())
    }

    @Test
    fun `Sが異なれば導出鍵も異なる`() = runTest {
        val other = ByteArray(TRANSFER_SECRET_BYTES) { (it + 2).toByte() }
        assertNotEquals(
            TransferSecretKeys.deriveAuthKey(secret).toList(),
            TransferSecretKeys.deriveAuthKey(other).toList(),
        )
    }

    @Test
    fun `authKeyHashは32バイトのSHA-256で決定的`() = runTest {
        val authKey = TransferSecretKeys.deriveAuthKey(secret)
        val hash1 = TransferSecretKeys.authKeyHash(authKey)
        val hash2 = TransferSecretKeys.authKeyHash(authKey)
        assertEquals(32, hash1.size)
        assertEquals(hash1.toList(), hash2.toList())
    }

    // Why not 既存のderive()呼び出し経由のみで検証: 自己無矛盾性（同じ入力→同じ出力／
    // 異なる入力→異なる出力）だけでは、依拠しているHKDF-SHA256実装が仕様どおりの
    // 出力バイト列を返しているかまでは確認できない。ここではラッパーを経由せず
    // HKDFプリミティブを直接叩き、RFC 5869 Appendix Aの既知ベクタと突き合わせる。

    @Test
    fun `RFC5869 Test Case 1（salt・info指定あり）の既知ベクタと一致する`() = runTest {
        val ikm = "0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b".hexToByteArray()
        val salt = "000102030405060708090a0b0c".hexToByteArray()
        val info = "f0f1f2f3f4f5f6f7f8f9".hexToByteArray()
        val expectedOkm =
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865".hexToByteArray()

        val okm = deriveHkdfSha256(ikm, salt, info, outputSizeBytes = 42)
        assertEquals(expectedOkm.toList(), okm.toList())
    }

    @Test
    fun `RFC5869 Test Case 3（salt・infoともに空）の既知ベクタと一致する`() = runTest {
        // salt = null を明示的な salt = ByteArray(0) と並べて検証する。HMACはゼロ長キーを
        // ハッシュ長ぶんのゼロ埋めキーと同じブロックに正規化するため両者は等価になるはず
        // （salt省略時の既定動作がRFCどおりであることの根拠にする）。
        val ikm = "0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b".hexToByteArray()
        val expectedOkm =
            "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8".hexToByteArray()

        val okmViaNullSalt = deriveHkdfSha256(ikm, salt = null, info = ByteArray(0), outputSizeBytes = 42)
        assertEquals(expectedOkm.toList(), okmViaNullSalt.toList())

        val okmViaEmptySalt = deriveHkdfSha256(ikm, salt = ByteArray(0), info = ByteArray(0), outputSizeBytes = 42)
        assertEquals(expectedOkm.toList(), okmViaEmptySalt.toList())
    }

    private suspend fun deriveHkdfSha256(
        ikm: ByteArray,
        salt: ByteArray?,
        info: ByteArray,
        outputSizeBytes: Int,
    ): ByteArray {
        val hkdf = CryptographyProvider.Default.get(HKDF)
        val derivation = hkdf.secretDerivation(
            digest = SHA256,
            outputSize = (outputSizeBytes * 8).bits,
            salt = salt,
            info = info,
        )
        return derivation.deriveSecretToByteArray(ikm)
    }
}
