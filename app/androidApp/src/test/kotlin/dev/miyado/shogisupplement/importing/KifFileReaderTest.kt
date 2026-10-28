package dev.miyado.shogisupplement.importing

import dev.miyado.shogisupplement.kifu.KifParser
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KifFileReaderTest {
    private val kif = "先手：髙橋\n後手：山田\n手合割：平手\n手数----指手---------消費時間--\n1 ７六歩(77)\n2 ３四歩(33)\n3 投了\n"
    private val windows31j = kif.toByteArray(Charset.forName("Windows-31J"))

    @Test fun utf8AndWindows31jPreserveNamesAndMoves() {
        for (bytes in listOf(kif.toByteArray(), windows31j)) {
            val decoded = KifFileReader.read(ByteArrayInputStream(bytes))
            assertEquals(kif, decoded)
            val game = KifParser().parse(decoded)
            assertEquals("髙橋", game.senteName)
            assertEquals("山田", game.goteName)
            assertEquals(listOf("7g7f", "3c3d"), game.moves)
        }
    }

    @Test fun utf8BomIsRemoved() {
        val bytes = byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) + kif.toByteArray()
        assertEquals(kif, KifFileReader.decode(bytes))
    }

    @Test fun validUtf8IsPreferred() {
        assertEquals("先手：😀", KifFileReader.decode("先手：😀".toByteArray()))
    }

    @Test fun malformedEncodingIsRejectedWithoutReplacement() {
        assertFailsWith<CharacterCodingException> { KifFileReader.decode(byteArrayOf(0x81.toByte())) }
    }

    @Test fun emptyFileRemainsEmpty() {
        assertEquals("", KifFileReader.read(ByteArrayInputStream(byteArrayOf())))
    }

    @Test fun exactLimitIsAccepted() {
        val bytes = ByteArray(KifFileReader.MAX_BYTES) { 65 }
        assertEquals(bytes.size, KifFileReader.read(ByteArrayInputStream(bytes)).length)
    }

    @Test fun oversizedBytesAreRejected() {
        assertFailsWith<IOException> { KifFileReader.decode(ByteArray(KifFileReader.MAX_BYTES + 1)) }
    }

    @Test fun oversizedStreamStopsAfterLimitPlusOne() {
        var consumed = 0
        val endless = object : InputStream() {
            override fun read(): Int { consumed++; return 65 }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                b.fill(65, off, off + len)
                consumed += len
                return len
            }
        }
        assertFailsWith<IOException> { KifFileReader.read(endless) }
        assertEquals(KifFileReader.MAX_BYTES + 1, consumed)
    }

    @Test fun shortReadsDoNotTruncate() {
        val stream = object : ByteArrayInputStream(windows31j) {
            override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, minOf(1, len))
        }
        assertEquals(kif, KifFileReader.read(stream))
    }
}
