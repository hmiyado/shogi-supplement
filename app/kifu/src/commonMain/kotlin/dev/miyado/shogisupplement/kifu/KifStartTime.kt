package dev.miyado.shogisupplement.kifu

import kotlin.time.Instant

/** KIFの開始日時を日本標準時のepoch秒へ変換する。解釈できない表記はnull。 */
fun parseKifStartAtJst(value: String?): Long? {
    if (value == null) return null
    val match = Regex("""(\d{4})/(\d{1,2})/(\d{1,2}).*?(\d{1,2}):(\d{2})$""").find(value) ?: return null
    val (year, month, day, hour, minute) = match.destructured
    fun pad(value: String) = value.padStart(2, '0')
    return runCatching {
        Instant.parse(
            "${pad(year)}-${pad(month)}-${pad(day)}T${pad(hour)}:${minute}:00+09:00",
        ).epochSeconds
    }.getOrNull()
}
