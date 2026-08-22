package dev.miyado.shogisupplement.engine

import android.content.pm.ApplicationInfo
import android.util.Log
import java.io.File

/** Androidのエンジン起動口。パス解決とLogcat連携を担い、USI通信は共通実装へ委譲する。Why notここで通信しない: serverと同じ処理を共有するため。 */
object UsiEngineProcess {
    private const val TAG = "UsiEngineProcess"

    /** エンジンを起動してUSIハンドシェイクを完了する。 @param appInfo nativeLibraryDir取得用情報。 @param evalDir EvalDirの絶対パス。 */
    fun create(appInfo: ApplicationInfo, evalDir: File): Engine {
        val soPath = File(appInfo.nativeLibraryDir, "libyaneuraou_usi.so")
        require(soPath.exists()) { "エンジンバイナリが見つかりません: ${soPath.absolutePath}" }

        return UsiEngineSubprocess.create(
            enginePath = soPath.absolutePath,
            evalDir = evalDir.absolutePath,
            logLifecycle = { Log.d(TAG, it) },
            logIo = { Log.v(TAG, it) },
        )
    }
}
