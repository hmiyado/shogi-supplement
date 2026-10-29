package dev.miyado.shogisupplement.importing

import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.Charset
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KifFileReaderUriTest {
    private val text = "先手：髙橋\n1 ７六歩(77)\n"
    private val bytes = text.toByteArray(Charset.forName("Windows-31J"))
    private val resolver get() = RuntimeEnvironment.getApplication().contentResolver

    @Test fun fileUriUsesWindows31jDecoder() {
        val file = File.createTempFile("shift-jis-", ".kif")
        try {
            file.writeBytes(bytes)
            assertEquals(text, KifFileReader.read(resolver, Uri.fromFile(file)))
        } finally {
            file.delete()
        }
    }

    @Test fun contentUriUsesWindows31jDecoderAndClosesStream() {
        var closed = false
        val stream = object : ByteArrayInputStream(bytes) {
            override fun close() { closed = true; super.close() }
        }
        val uri = Uri.parse("content://kif-test/sample.kif")
        shadowOf(resolver).registerInputStream(uri, stream)
        assertEquals(text, KifFileReader.read(resolver, uri))
        assertTrue(closed)
    }
}
