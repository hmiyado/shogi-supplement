@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package dev.miyado.shogisupplement.util

/**
 * [Logger.wasmJs.kt]と同じ理由（wasmJsはデフォルトで`Date`を参照できない）で
 * @JsFun による最小限のブリッジにする。
 */
@JsFun("() => Date.now()")
private external fun jsDateNowMillis(): Double

actual fun currentEpochSeconds(): Long = (jsDateNowMillis() / 1000.0).toLong()
