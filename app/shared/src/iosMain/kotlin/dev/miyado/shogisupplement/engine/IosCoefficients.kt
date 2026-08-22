@file:OptIn(ExperimentalForeignApi::class)

package dev.miyado.shogisupplement.engine

import dev.miyado.shogisupplement.judge.CoefficientTable
import dev.miyado.shogisupplement.util.Logger
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import platform.Foundation.NSBundle
import platform.posix.SEEK_END
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.rewind

/** iOSバンドルの係数表をPOSIX APIで読む。Androidと同じJSONを参照し、FoundationのファイルAPIは使わない。 */
object IosCoefficients {

    private var cached: CoefficientTable? = null

    /** 係数表を返す（初回のみバンドルから読み込み、以降はキャッシュを返す）。 */
    fun getInstance(): CoefficientTable {
        cached?.let { return it }
        val fileName = CoefficientTable.COEFFICIENTS_FILE_NAME
        val path = NSBundle.mainBundle.pathForResource(fileName.removeSuffix(".json"), ofType = "json")
            ?: error("$fileName not found in bundle")
        val json = readFile(path)
            ?: run {
                Logger.e("IosCoefficients", "failed to read $fileName at $path")
                error("failed to read $fileName")
            }
        return CoefficientTable.fromJson(json).also { cached = it }
    }

    private fun readFile(path: String): String? {
        val file = fopen(path, "r") ?: return null
        try {
            fseek(file, 0, SEEK_END)
            val size = ftell(file)
            rewind(file)
            if (size <= 0) return ""
            return memScoped {
                val buffer = allocArray<ByteVar>(size)
                val read = fread(buffer, 1u, size.toULong(), file)
                buffer.readBytes(read.toInt()).decodeToString()
            }
        } finally {
            fclose(file)
        }
    }
}
