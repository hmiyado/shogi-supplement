@file:OptIn(ExperimentalForeignApi::class)

package dev.miyado.shogisupplement.ui

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.posix.SEEK_END
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.fwrite
import platform.posix.remove
import platform.posix.rewind

/**
 * 中断された解析を再開するための、解析開始直前のスナップショット。
 * サーバーはUSI手列のハッシュで冪等なため、この内容で同じリクエストを再送するだけで復旧できる。
 * 申告情報（ratingService等）も持つのは、省くと再開のときだけ申告情報が欠落するため。
 */
@Serializable
data class PendingAnalysis(
    val kifText: String,
    val userSide: String,
    val fileName: String,
    val ratingService: String? = null,
    val ratingRaw: Long? = null,
    val ratingRule: String? = null,
    val createdAtEpochSeconds: Long,
)

/**
 * [PendingAnalysis] を Documents 配下の単一JSONファイルへ永続化する。
 *
 * Why not DB: 同時に1本しか走らない取込フローの一時状態1件にスキーマは過剰。
 * Why not Foundation系のファイルAPI: Kotlin/Nativeバインディングの差異を避けるため、
 * iosMainの既存ファイルI/Oと同じくPOSIX API（fopen系）に統一している。
 */
object PendingAnalysisStore {

    private const val FILE_NAME = "pending_analysis.json"
    private val json = Json { ignoreUnknownKeys = true }

    fun save(pending: PendingAnalysis) {
        writeFile(filePath(), json.encodeToString(PendingAnalysis.serializer(), pending))
    }

    /**
     * 保存済みのpendingを返す（無い・壊れているときはnull）。壊れたファイルは削除する——
     * 残しても起動のたびにパース失敗を繰り返すだけのため。
     */
    fun load(): PendingAnalysis? {
        val text = readFile(filePath()) ?: return null
        return runCatching { json.decodeFromString(PendingAnalysis.serializer(), text) }
            .getOrElse {
                clear()
                null
            }
    }

    /** 解析完了時・取込破棄時に呼ぶ。ファイルが無いときの[remove]は無害なため存在チェック不要。 */
    fun clear() {
        remove(filePath())
    }

    private fun filePath(): String {
        val documentsDir = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true,
        ).firstOrNull() as? String ?: error("Documents directory not found")
        return "$documentsDir/$FILE_NAME"
    }

    private fun writeFile(path: String, content: String) {
        val file = fopen(path, "w") ?: error("failed to open $path for write")
        try {
            val bytes = content.encodeToByteArray()
            if (bytes.isNotEmpty()) {
                bytes.usePinned { pinned ->
                    fwrite(pinned.addressOf(0), 1u, bytes.size.toULong(), file)
                }
            }
        } finally {
            fclose(file)
        }
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
