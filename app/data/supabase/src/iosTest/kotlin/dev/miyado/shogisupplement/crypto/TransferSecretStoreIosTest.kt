package dev.miyado.shogisupplement.crypto

import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TransferSecretStoreIosTest {

    private fun store() = IosTransferSecretStore()

    @AfterTest
    fun tearDown() = runTest {
        store().clear()
    }

    @Test
    fun `save したものはload可能な環境なら読み戻せる`() = runTest {
        val secret = ByteArray(TRANSFER_SECRET_BYTES) { it.toByte() }
        val s = store()
        s.clear()
        s.save(secret)
        val loaded = s.load()
        assertKeychainRoundTripOrUnavailable(secret, loaded)
    }

    @Test
    fun `saveを2回呼んでもupdate経路で最後の値が読める`() = runTest {
        val first = ByteArray(TRANSFER_SECRET_BYTES) { it.toByte() }
        val second = ByteArray(TRANSFER_SECRET_BYTES) { (it + 100).toByte() }
        val s = store()
        s.clear()
        s.save(first)
        s.save(second)
        val loaded = s.load()
        assertKeychainRoundTripOrUnavailable(second, loaded)
    }

    @Test
    fun `clear した後は load が null を返す`() = runTest {
        val secret = ByteArray(TRANSFER_SECRET_BYTES) { it.toByte() }
        val s = store()
        s.save(secret)
        s.clear()
        assertEquals(null, s.load())
    }

    @Test
    fun `getOrCreateSecretは未生成なら生成し 次回は同じ値を返す`() = runTest {
        val s = store()
        s.clear()
        val first = TransferSecretManager.getOrCreateSecret(s)
        assertEquals(TRANSFER_SECRET_BYTES, first.size)
        val second = TransferSecretManager.getOrCreateSecret(store())
        if (s.load() != null) {
            assertEquals(first.toList(), second.toList())
        } else {
            assertEquals(TRANSFER_SECRET_BYTES, second.size)
        }
    }

    private fun assertKeychainRoundTripOrUnavailable(expected: ByteArray, actual: ByteArray?) {
        if (actual == null) {
            return
        }
        assertEquals(expected.toList(), actual.toList())
    }
}
