package dev.miyado.shogisupplement.util

import platform.Foundation.NSLog

/**
 * iOS実装: NSLog（stderr/OSLog経由）に委譲する。
 *
 * UsiEngineInProcess起動後はプロセスのfd0/fd1がエンジン用パイプに専有されるため、
 * println/print は使わないこと（NSLogはOSLog経由でありfd1を使わない）。
 *
 * NSLogのフォーマット文字列安全化: 呼び出し済みの引数（[message]・スタックトレース）を
 * フォーマット文字列として渡すと、その中の "%" がフォーマット指定子として誤解釈され、
 * 未定義の可変長引数を読みに行ってクラッシュしうる。対策として（1）フォーマット文字列は
 * 常に固定の "%@" のみを使い、動的な文字列は必ず引数側（%@に束縛される値）として渡す
 * （2）例外のスタックトレース等で極端に長い文字列になりうるため、上限長で truncate
 * してから渡す（OSLog自体のメッセージ長制限による予期しない切り詰め・負荷も避ける）。
 */
actual object Logger {
    /** これを超える長さの本文は truncate する（スタックトレースの暴走対策）。 */
    private const val MAX_MESSAGE_LENGTH = 2000

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        val suffix = throwable?.let { " ${it.stackTraceToString()}" } ?: ""
        val full = "E/$tag: $message$suffix"
        val safe = if (full.length > MAX_MESSAGE_LENGTH) {
            full.take(MAX_MESSAGE_LENGTH) + "…(truncated ${full.length - MAX_MESSAGE_LENGTH} chars)"
        } else {
            full
        }
        // フォーマット文字列は常に固定の "%@" のみを使う。safe は引数側でありフォーマット文字列
        // として解釈されないため、内部に "%" を含んでいても安全。
        NSLog("%@", safe)
    }
}
