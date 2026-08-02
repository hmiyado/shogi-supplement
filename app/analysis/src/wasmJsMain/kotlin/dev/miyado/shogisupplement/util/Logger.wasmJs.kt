@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package dev.miyado.shogisupplement.util

/**
 * ブラウザ console.error への橋渡し。wasmJsターゲットは js(IR) と違い `console` を
 * デフォルトで参照できないため（kotlinx-browser等の追加依存が要る）、@JsFun で
 * 直接JSスニペットを呼ぶ最小限のブリッジにする。
 */
@JsFun("(msg) => console.error(msg)")
private external fun jsConsoleError(msg: String)

actual object Logger {
    actual fun e(tag: String, message: String, throwable: Throwable?) {
        val suffix = throwable?.let { " " + it.stackTraceToString() } ?: ""
        jsConsoleError("E/$tag: $message$suffix")
    }
}
