package dev.miyado.shogisupplement.engine

import android.content.pm.ApplicationInfo
import android.util.Log
import java.io.File

/**
 * Android用のエンジンプロセス起動口。
 *
 * nativeLibraryDir の libyaneuraou_usi.so を起動し、USIプロトコルで通信する実体は
 * [UsiEngineSubprocess]（:shared・server/workerと共通）に委譲する。ここでは
 * ApplicationInfo からのバイナリパス解決とLogcat連携のみを担う。
 *
 * Why not このファイルにプロセス起動・USIハンドシェイクを実装: android.content.pm.ApplicationInfo
 * 以外の部分（ProcessBuilderでのexec・USIハンドシェイク・info行パース）はserver/workerと
 * 全く同一のロジックのため、[UsiEngineSubprocess] に一本化した。
 */
object UsiEngineProcess {
    private const val TAG = "UsiEngineProcess"

    /**
     * エンジンプロセスを起動し、USIハンドシェイクを完了させて返す。
     *
     * @param appInfo ApplicationInfo（nativeLibraryDir 取得用）
     * @param evalDir EvalDir（filesDir/eval）の絶対パス
     */
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
