package dev.miyado.shogisupplement.ui.common

import androidx.compose.runtime.Composable
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.dateWithTimeIntervalSince1970

@Composable
actual fun ReportBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS: システムバック（Android の物理/ジェスチャーバックキー）に相当する概念がないため no-op。
    // 検討モード終了は ReportScreen 内の「終了」ボタン（onStudyEnd）で行う。
}

actual fun formatDateTime(epochSeconds: Long): String = format(epochSeconds, "yyyy/MM/dd HH:mm")

actual fun currentLocalDateTime(): String {
    val formatter = NSDateFormatter()
    formatter.dateFormat = "yyyy/MM/dd HH:mm"
    return formatter.stringFromDate(NSDate())
}

/** 月日のみの短縮表示（"M/d"）。 */
actual fun formatShortDate(epochSeconds: Long): String = format(epochSeconds, "M/d")

/**
 * epochSecondsを端末のタイムゾーンで整形する。
 * Why not 通算日数から自前で計算する: 日付境界がUTCで切れるため、
 * JSTでは午前9時より前の対局が前日として表示される。
 */
private fun format(epochSeconds: Long, pattern: String): String {
    val formatter = NSDateFormatter()
    formatter.dateFormat = pattern
    return formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochSeconds.toDouble()))
}
