@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package dev.miyado.shogisupplement.ui.common

import androidx.compose.runtime.Composable

@Composable
actual fun ReportBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Web: ブラウザの戻る操作は検討モード終了に割り当てない（iOSと同じくno-op。
    // 検討モード終了は ReportScreen 内の「終了」ボタンで行う）。
}

/**
 * epochSeconds（UTC）を "yyyy/MM/dd HH:mm" に変換する。wasmJsは kotlin.js.Date をデフォルトで
 * 参照できないため、iOS実装と同じ Howard Hinnant の civilFromDays アルゴリズムで変換する
 * （デモ画面用途のみのためタイムゾーンは UTC 固定の簡易実装）。
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

/** wasmJsではブラウザのDateを直接参照できないため、JsFunで取得する。 */
actual fun currentLocalDateTime(): String = jsCurrentLocalDateTime()

@JsFun("""
    () => {
        const d = new Date();
        const p = n => String(n).padStart(2, '0');
        return d.getFullYear() + '/' + p(d.getMonth() + 1) + '/' + p(d.getDate()) + ' ' + p(d.getHours()) + ':' + p(d.getMinutes());
    }
""")
private external fun jsCurrentLocalDateTime(): String

/** 月日のみの短縮表示（"M/d"）。[formatDateTime] と同じ civilFromDays を再利用する。 */
actual fun formatShortDate(epochSeconds: Long): String {
    val totalDays = epochSeconds.floorDiv(86400)
    val (_, month, day) = civilFromDays(totalDays)
    return "$month/$day"
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
