@file:OptIn(ExperimentalForeignApi::class)

package dev.miyado.shogisupplement.ui

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
 * バンドル内 SupabaseConfig.properties から Supabase の接続設定を読む。
 *
 * ファイルはビルド時に app/local.properties から生成される（iosApp/project.yml の
 * preBuildScripts 参照）。リポジトリには秘密を置かない（Androidの
 * local.properties→BuildConfig と同じ方針）。ファイルが無い・値が空のときは
 * null を返し、呼び出し側はアカウント導線ごと非表示にする。
 *
 * 読み込みは POSIX API（fopen/fread）で行う（IosCoefficients.kt と同じパターン。
 * Foundation のファイル/plist系APIはバインディングの差異が出やすいため避ける）。
 */
internal object IosSupabaseConfig {

    data class Config(val url: String, val key: String)

    fun load(): Config? {
        val path = NSBundle.mainBundle.pathForResource("SupabaseConfig", ofType = "properties")
            ?: return null
        val text = readFile(path) ?: return null
        val entries = text.lineSequence()
            .mapNotNull { line ->
                val idx = line.indexOf('=')
                if (idx <= 0) null else line.take(idx).trim() to line.drop(idx + 1).trim()
            }
            .toMap()
        val url = entries["SUPABASE_URL"]
        val key = entries["SUPABASE_KEY"]
        if (url.isNullOrBlank() || key.isNullOrBlank()) return null
        return Config(url, key)
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
