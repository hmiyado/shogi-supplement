package dev.miyado.shogisupplement.ui.common

/**
 * :ui commonMain 共通の数値フォーマットヘルパー。
 *
 * "%.1f".format(x)（java.lang.String.format）は Kotlin/Native commonMain では使えないため、
 * その代替実装をここに集約し、ReportScreen.kt と DrillScreen.kt の両方から共用する。
 */

/**
 * 非負の Double を小数点以下1桁で四捨五入して文字列化する（損失%表示用）。
 * 呼び出し元は ReportScreen の BlunderCard・DrillScreen の結果画面（いずれも非負の
 * 損失%表示）のみのため、符号は扱わない。
 */
fun formatFixed1(value: Double): String {
    val scaled = kotlin.math.round(value * 10).toLong()
    val intPart = scaled / 10
    val fracPart = scaled % 10
    return "$intPart.$fracPart"
}
