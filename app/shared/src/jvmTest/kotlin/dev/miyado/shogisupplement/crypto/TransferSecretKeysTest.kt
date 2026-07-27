package dev.miyado.shogisupplement.crypto

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
}
