@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package dev.miyado.shogisupplement.util

/** wasmJsからDateへ接続する最小限の@JsFunブリッジ。 */
@JsFun("() => Date.now()")
private external fun jsDateNowMillis(): Double

actual fun currentEpochSeconds(): Long = (jsDateNowMillis() / 1000.0).toLong()
