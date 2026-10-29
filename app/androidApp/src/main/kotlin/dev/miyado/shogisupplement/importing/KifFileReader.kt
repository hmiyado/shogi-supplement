package dev.miyado.shogisupplement.importing

import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

internal object KifFileReader {
    const val MAX_BYTES = 5 * 1024 * 1024

    fun read(resolver: ContentResolver, uri: Uri): String {
        val stream = if (uri.scheme == "file") {
            File(requireNotNull(uri.path)).inputStream()
        } else {
            resolver.openInputStream(uri) ?: throw IOException()
        }
        return stream.use(::read)
    }

    fun read(stream: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = stream.read(buffer, 0, minOf(buffer.size, MAX_BYTES + 1 - output.size()))
            if (count < 0) break
            if (count == 0) {
                val next = stream.read()
                if (next < 0) break
                output.write(next)
            } else {
                output.write(buffer, 0, count)
            }
            if (output.size() > MAX_BYTES) throw IOException()
        }
        return decode(output.toByteArray())
    }

    fun decode(bytes: ByteArray): String {
        if (bytes.size > MAX_BYTES) throw IOException()
        val utf8 = try {
            decodeStrict(bytes, Charsets.UTF_8)
        } catch (_: CharacterCodingException) {
            null
        }
        return (utf8 ?: decodeStrict(bytes, Charset.forName("Windows-31J"))).removePrefix("\uFEFF")
    }

    private fun decodeStrict(bytes: ByteArray, charset: Charset): String =
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
}
