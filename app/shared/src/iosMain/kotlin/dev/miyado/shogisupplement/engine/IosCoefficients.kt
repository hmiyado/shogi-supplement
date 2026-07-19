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

/**
 * iOSバンドル同梱の係数表（coefficients_hao_v1.json）読み込み。
 *
 * androidApp/src/main/assets/coefficients_hao_v1.json をiosApp/project.ymlの
 * resourcesとして参照パスで共有している（複製しない。evalディレクトリと同じ方式）。
 * 判定ロジック・係数表の値そのものは一切変更しない（Androidと完全に同一のJSONを読む）。
 *
 * ファイル読み込みは POSIX API（fopen/fread）で行う（UsiEngineInProcess.kt と同様の
 * cinterop パターン。Foundation の NSString(contentsOfFile:) はバインディングの
 * 差異が出やすいため避けた）。
 */
object IosCoefficients {

    private var cached: CoefficientTable? = null

    /** 係数表を返す（初回のみバンドルから読み込み、以降はキャッシュを返す）。 */
    fun getInstance(): CoefficientTable {
        cached?.let { return it }
        val path = NSBundle.mainBundle.pathForResource("coefficients_hao_v1", ofType = "json")
            ?: error("coefficients_hao_v1.json not found in bundle")
        val json = readFile(path)
            ?: run {
                Logger.e("IosCoefficients", "failed to read coefficients_hao_v1.json at $path")
                error("failed to read coefficients_hao_v1.json")
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
