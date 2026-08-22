@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package dev.miyado.shogisupplement.util

/** wasmJsからブラウザのconsole.errorへ接続する最小限の@JsFunブリッジ。 */
@JsFun("(msg) => console.error(msg)")
private external fun jsConsoleError(msg: String)

actual object Logger {
    actual fun e(tag: String, message: String, throwable: Throwable?) {
        val suffix = throwable?.let { " " + it.stackTraceToString() } ?: ""
        jsConsoleError("E/$tag: $message$suffix")
    }
}
