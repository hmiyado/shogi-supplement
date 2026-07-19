package dev.miyado.shogisupplement.engine

import android.content.Context
import android.util.Log
import java.io.File
import java.security.MessageDigest

/**
 * assets/eval/nn.bin → filesDir/eval/nn.bin への展開を管理する。
 *
 * - 展開済みかつ SHA-256 が一致すればスキップ
 * - 初回または差し替え時のみコピー（64MB なので同期で実行: 実測 <2秒）
 */
object EvalLoader {

    private const val TAG = "EvalLoader"
    private const val ASSET_PATH = "eval/nn.bin"
    private const val EXPECTED_SHA256 = "1141d275bceec911156801f27303dc9ff5beb24f4f59144cc069306c59e80782"

    /** filesDir/eval/ への展開先ディレクトリを返す（呼び出し側でエンジンのEvalDirに設定） */
    fun ensureReady(context: Context): File {
        val destDir = File(context.filesDir, "eval")
        destDir.mkdirs()
        val destFile = File(destDir, "nn.bin")

        if (destFile.exists() && sha256hex(destFile) == EXPECTED_SHA256) {
            Log.d(TAG, "nn.bin already up-to-date, skipping extraction")
            return destDir
        }

        Log.i(TAG, "Extracting nn.bin from assets (${destFile.path})")
        context.assets.open(ASSET_PATH).use { src ->
            destFile.outputStream().use { dst ->
                src.copyTo(dst)
            }
        }
        val actualHash = sha256hex(destFile)
        check(actualHash == EXPECTED_SHA256) {
            "nn.bin SHA-256 mismatch: expected=$EXPECTED_SHA256 actual=$actualHash"
        }
        Log.i(TAG, "nn.bin extraction complete")
        return destDir
    }

    private fun sha256hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { inp ->
            val buf = ByteArray(65536)
            var n: Int
            while (inp.read(buf).also { n = it } != -1) {
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
