package dev.miyado.shogisupplement.ui.common

import androidx.compose.runtime.Composable

@Composable
actual fun ReportBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS: システムバック（Android の物理/ジェスチャーバックキー）に相当する概念がないため no-op。
    // 検討モード終了は ReportScreen 内の「終了」ボタン（onStudyEnd）で行う。
}

/**
 * epochSeconds（UTC）を "yyyy/MM/dd HH:mm" に変換する。
 *
 * java.text.SimpleDateFormat は Kotlin/Native では使えないため、Foundation の
 * NSDateFormatter に頼らず純粋な Kotlin 整数演算（Howard Hinnant の
 * civil_from_days アルゴリズム）で変換する。iOS はデモ画面（対局情報ダイアログ）
 * 用途のみなので、タイムゾーンは簡易的に UTC 固定とする（実運用の厳密なローカル
 * タイムゾーン対応が必要になれば NSDateFormatter 等に置き換えること）。
 */
actual fun formatDateTime(epochSeconds: Long): String {
    val totalMinutes = epochSeconds.floorDiv(60)
    val minute = totalMinutes.mod(60)
    val totalHours = totalMinutes.floorDiv(60)
    val hour = totalHours.mod(24)
    val totalDays = totalHours.floorDiv(24)

    val (year, month, day) = civilFromDays(totalDays)

    return buildString {
        append(year.toString().padStart(4, '0'))
        append('/')
        append(month.toString().padStart(2, '0'))
        append('/')
        append(day.toString().padStart(2, '0'))
        append(' ')
        append(hour.toString().padStart(2, '0'))
        append(':')
        append(minute.toString().padStart(2, '0'))
    }
}

/** 1970-01-01 からの通算日数 → (year, month[1-12], day[1-31])（グレゴリオ暦・UTC）。 */
private fun civilFromDays(z: Long): Triple<Long, Int, Int> {
    val z2 = z + 719468
    val era = (if (z2 >= 0) z2 else z2 - 146096) / 146097
    val doe = z2 - era * 146097 // [0, 146096]
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365 // [0, 399]
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100) // [0, 365]
    val mp = (5 * doy + 2) / 153 // [0, 11]
    val d = doy - (153 * mp + 2) / 5 + 1 // [1, 31]
    val m = if (mp < 10) mp + 3 else mp - 9 // [1, 12]
    val year = if (m <= 2) y + 1 else y
    return Triple(year, m.toInt(), d.toInt())
}
