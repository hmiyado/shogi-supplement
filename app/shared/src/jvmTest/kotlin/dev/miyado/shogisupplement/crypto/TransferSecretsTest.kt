package dev.miyado.shogisupplement.crypto

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransferSecretsTest {

    private val s = ByteArray(TRANSFER_SECRET_BYTES) { it.toByte() }
    private val other = ByteArray(TRANSFER_SECRET_BYTES) { (it + 100).toByte() }

    @Test
    fun `1つぶんの保存値は両方の鍵の導出元になる`() {
        val secrets = assertNotNull(TransferSecrets.fromStored(s))
        assertContentEquals(s, secrets.encSecret)
        assertContentEquals(s, secrets.authSecret)
    }

    @Test
    fun `2つぶんの保存値は前半が復号用_後半が認証用になる`() {
        val secrets = assertNotNull(TransferSecrets.fromStored(s + other))
        assertContentEquals(s, secrets.encSecret)
        assertContentEquals(other, secrets.authSecret)
    }

    @Test
    fun `想定外の長さの保存値はnullを返す`() {
        assertNull(TransferSecrets.fromStored(ByteArray(15)))
        assertNull(TransferSecrets.fromStored(ByteArray(48)))
        assertNull(TransferSecrets.fromStored(ByteArray(0)))
    }

    @Test
    fun `認証用を引き直しても復号用は変わらない`() {
        val before = assertNotNull(TransferSecrets.fromStored(s))
        val after = before.rotateAuth()
        assertContentEquals(s, after.encSecret)
        assertFalse(after.authSecret.contentEquals(s), "認証用が引き直されていない")
    }

    @Test
    fun `引き直す前の保存値は1つぶん_引き直した後は2つぶんになる`() {
        val before = assertNotNull(TransferSecrets.fromStored(s))
        assertEquals(TRANSFER_SECRET_BYTES, before.toStored().size)
        assertEquals(TRANSFER_SECRET_BYTES * 2, before.rotateAuth().toStored().size)
    }

    @Test
    fun `保存と読み出しを往復しても値が変わらない`() {
        val rotated = assertNotNull(TransferSecrets.fromStored(s)).rotateAuth()
        val restored = assertNotNull(TransferSecrets.fromStored(rotated.toStored()))
        assertContentEquals(rotated.encSecret, restored.encSecret)
        assertContentEquals(rotated.authSecret, restored.authSecret)
    }

    @Test
    fun `引き直す前のコードは従来と同じ表記のままになる`() {
        val secrets = assertNotNull(TransferSecrets.fromStored(s))
        assertEquals(TransferCode.encode(s), TransferCode.encode(secrets))
    }

    @Test
    fun `引き直した後のコードは長くなり_両方のシークレットを復元できる`() {
        val rotated = assertNotNull(TransferSecrets.fromStored(s)).rotateAuth()
        val code = TransferCode.encode(rotated)
        assertTrue(code.length > TransferCode.encode(s).length)

        val decoded = assertNotNull(TransferCode.decodeSecrets(code))
        assertContentEquals(rotated.encSecret, decoded.encSecret)
        assertContentEquals(rotated.authSecret, decoded.authSecret)
    }

    @Test
    fun `引き直す前のコードは両方の鍵に同じ値を返す`() {
        val decoded = assertNotNull(TransferCode.decodeSecrets(TransferCode.encode(s)))
        assertContentEquals(s, decoded.encSecret)
        assertContentEquals(s, decoded.authSecret)
    }

    @Test
    fun `既存のdecodeは引き直した後のコードを受け付けない`() {
        val rotated = assertNotNull(TransferSecrets.fromStored(s)).rotateAuth()
        assertNull(TransferCode.decode(TransferCode.encode(rotated)))
        assertContentEquals(s, TransferCode.decode(TransferCode.encode(s)))
    }

    @Test
    fun `引き直した後のコードも1文字の写し間違いを検出する`() {
        val rotated = assertNotNull(TransferSecrets.fromStored(s)).rotateAuth()
        val code = TransferCode.encode(rotated)
        val broken = code.replaceFirst(code.first { it.isLetterOrDigit() }, 'Z')
        assertNull(TransferCode.decodeSecrets(broken))
    }
}

class TransferSecretRotationTest {

    private class InMemoryStore(private var stored: ByteArray? = null) : TransferSecretStore {
        override suspend fun load(): ByteArray? = stored
        override suspend fun save(secret: ByteArray) {
            stored = secret
        }

        override suspend fun clear() {
            stored = null
        }
    }

    @Test
    fun `引き直すと認証用だけが変わり保存値にも反映される`() = runTest {
        val store = InMemoryStore()
        val before = TransferSecretManager.getOrCreateSecrets(store)

        val after = TransferSecretManager.rotateAuthSecret(store)

        assertContentEquals(before.encSecret, after.encSecret, "復号用が変わってはいけない")
        assertFalse(after.authSecret.contentEquals(before.authSecret), "認証用が変わるはず")

        val reloaded = assertNotNull(TransferSecrets.fromStored(assertNotNull(store.load())))
        assertContentEquals(after.encSecret, reloaded.encSecret)
        assertContentEquals(after.authSecret, reloaded.authSecret)
    }

    @Test
    fun `何度引き直しても復号用は最初のまま保たれる`() = runTest {
        val store = InMemoryStore()
        val first = TransferSecretManager.getOrCreateSecrets(store)

        repeat(3) { TransferSecretManager.rotateAuthSecret(store) }

        val last = TransferSecretManager.getOrCreateSecrets(store)
        assertContentEquals(first.encSecret, last.encSecret)
    }

    @Test
    fun `壊れた保存値は作り直す`() = runTest {
        val store = InMemoryStore(ByteArray(7))
        val secrets = TransferSecretManager.getOrCreateSecrets(store)
        assertEquals(TRANSFER_SECRET_BYTES, secrets.encSecret.size)
        assertContentEquals(secrets.encSecret, secrets.authSecret)
    }
}
