package dev.miyado.shogisupplement.util

/**
 * ISO-8601 UTCをJSTの「M月d日 H:mm」へ変換し、解析不能な入力は原文で返す。
 * Why not kotlinx-datetime: commonMainの依存を増やさず、必要な範囲だけ手計算するため。
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
