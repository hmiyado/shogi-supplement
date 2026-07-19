package dev.miyado.shogisupplement.util

/**
 * 軽量ロガー。android.util.Log は Android専用APIのため、KMP化した
 * ViewModel（:ui commonMain）からは使えない。expect/actualで最小限のログ出力先を提供する。
 *
 * - Android実装: android.util.Log.e に委譲（Logcat出力）。
 * - iOS実装: NSLog に委譲。エンジンin-process実行中
 *   （UsiEngineInProcess.create 呼び出し後）はプロセスのfd0/fd1がエンジン用パイプに
 *   専有されるため、println/print は使わずNSLog（stderr/OSLog経由）を使うこと。
 */
expect object Logger {
    /** エラーログを出力する。 */
    fun e(tag: String, message: String, throwable: Throwable? = null)
}
