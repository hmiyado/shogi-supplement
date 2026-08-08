package dev.miyado.shogisupplement.util

/**
 * ログ1行を実出力先へ渡すSwift⇄Kotlin橋渡し。
 *
 * Kotlin/Nativeは可変長引数のObjC関数（`NSLog(format, ...)`）を正しくブリッジできず、
 * 実機arm64で`NSLog("%@", str)`を呼ぶと可変長引数の受け渡しが壊れてクラッシュする。
 * そのため出力の実行はSwift側（可変長を正しく扱える）へ委ね、Kotlinはここへ文字列を渡す。
 * 未登録（起動最初期）の間は捨てる。
 */
object LogSink {
    var handler: ((line: String) -> Unit)? = null
}
