package dev.miyado.shogisupplement.util

/**
 * ISO-8601 UTC文字列（例: "2026-07-28T00:00:00Z"）をJST（UTC+9時間固定。夏時間なし）に変換し、
 * 「7月28日 9:00」の形式で返す。
 *
 * [dev.miyado.shogisupplement.engine.RemoteAnalysisException.QuotaExceeded.resetAt] の表示整形用。
 * パース不能な入力（サーバー未到達時の空文字列など）はそのまま返す
 * （呼び出し側 [dev.miyado.shogisupplement.engine.RemoteAnalysisErrorMapper] のフォールバック）。
 *
 * Why not kotlinx-datetime: commonMainに新規依存を足さない方針のため、文字列パース＋9時間加算の
 * 手計算（年月日時分の四則演算のみ）で足りる範囲を自前で実装する。うるう年判定も西暦の
 * 4/100/400年ルールで自前計算する（2月末日の繰り上げに必要）。
 */
fun formatResetAtJst(resetAtIso: String): String {
    val parsed = parseIso8601Utc(resetAtIso) ?: return resetAtIso
    val jst = parsed.plusHours(JST_OFFSET_HOURS)
    return "${jst.month}月${jst.day}日 ${jst.hour}:${jst.minute.toString().padStart(2, '0')}"
}

private const val JST_OFFSET_HOURS = 9

private data class UtcDateTime(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
)

/** "yyyy-MM-ddTHH:mm:ss(.fff)?Z" 形式のみ受け付ける（サーバーは java.time.Instant#toString 形式で返す）。 */
private fun parseIso8601Utc(s: String): UtcDateTime? {
    if (!s.endsWith("Z")) return null
    val body = s.dropLast(1)
    val parts = body.split("T")
    if (parts.size != 2) return null
    val dateNums = parts[0].split("-").map { it.toIntOrNull() ?: return null }
    if (dateNums.size != 3) return null
    val timePart = parts[1].substringBefore(".")
    val timeNums = timePart.split(":").map { it.toIntOrNull() ?: return null }
    if (timeNums.size < 2) return null
    return UtcDateTime(
        year = dateNums[0],
        month = dateNums[1],
        day = dateNums[2],
        hour = timeNums[0],
        minute = timeNums[1],
    )
}

private fun isLeapYear(year: Int): Boolean =
    (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if (isLeapYear(year)) 29 else 28
    else -> 31 // 不正な月はサーバー生成のISO文字列では起きない想定
}

/** 時間の加算のみサポートする（用途がクォータリセット時刻の+9時間固定のみのため）。 */
private fun UtcDateTime.plusHours(hoursToAdd: Int): UtcDateTime {
    var totalMinutes = hour * 60 + minute + hoursToAdd * 60
    var newYear = year
    var newMonth = month
    var newDay = day
    while (totalMinutes >= 24 * 60) {
        totalMinutes -= 24 * 60
        newDay += 1
        val dim = daysInMonth(newYear, newMonth)
        if (newDay > dim) {
            newDay = 1
            newMonth += 1
            if (newMonth > 12) {
                newMonth = 1
                newYear += 1
            }
        }
    }
    return UtcDateTime(newYear, newMonth, newDay, totalMinutes / 60, totalMinutes % 60)
}
