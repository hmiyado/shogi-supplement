package dev.miyado.shogisupplement.util

/**
 * iOS実装: 出力を[LogSink]（Swift側で`NSLog`する）へ委ねる。
 *
 * Kotlin/Nativeから`NSLog`を直接呼ばない理由: `NSLog`はObjCの可変長引数関数で、
 * Kotlin/Nativeは可変長引数の受け渡しを正しくブリッジできず、実機arm64で
 * `NSLog("%@", str)`を呼ぶとクラッシュする（実機で観測。可変長を扱えるSwift側の
 * `NSLog("%@", str)`は安全）。[LogSink]未登録の起動最初期は出力を捨てる。
 *
 * 例外のスタックトレース等で極端に長い文字列になりうるため、上限長でtruncateしてから渡す。
 */
actual object Logger {
    private const val MAX_MESSAGE_LENGTH = 2000

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        val suffix = throwable?.let { " ${it.stackTraceToString()}" } ?: ""
        val full = "E/$tag: $message$suffix"
        val safe = if (full.length > MAX_MESSAGE_LENGTH) {
            full.take(MAX_MESSAGE_LENGTH) + "…(truncated ${full.length - MAX_MESSAGE_LENGTH} chars)"
        } else {
            full
        }
        LogSink.handler?.invoke(safe)
    }
}
