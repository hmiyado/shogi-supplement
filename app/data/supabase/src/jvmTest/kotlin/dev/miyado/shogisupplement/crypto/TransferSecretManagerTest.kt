package dev.miyado.shogisupplement.crypto

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TransferSecretManagerTest {

    private lateinit var dir: File

    @BeforeTest
    fun setUp() {
        dir = createTempDirectory("transfer-secret-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun store() = JvmTransferSecretStore(dir)

    @Test
    fun `未生成なら生成して保存し 次回は同じ値を返す`() = runTest {
        val first = TransferSecretManager.getOrCreateSecret(store())
        assertEquals(TRANSFER_SECRET_BYTES, first.size)

        // 別インスタンス（同じディレクトリ）でも永続化された同じ値が読める
        val second = TransferSecretManager.getOrCreateSecret(store())
        assertEquals(first.toList(), second.toList())
    }

    @Test
    fun `save したものは load で読み戻せる`() = runTest {
        val secret = ByteArray(TRANSFER_SECRET_BYTES) { it.toByte() }
        val s = store()
        s.save(secret)
        assertEquals(secret.toList(), s.load()?.toList())
    }

    @Test
    fun `clear した後は load が null を返す`() = runTest {
        val secret = ByteArray(TRANSFER_SECRET_BYTES) { it.toByte() }
        val s = store()
        s.save(secret)
        assertNotNull(s.load())
        s.clear()
        assertNull(s.load())
    }
}
