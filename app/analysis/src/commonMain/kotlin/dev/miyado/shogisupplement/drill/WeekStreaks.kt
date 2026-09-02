package dev.miyado.shogisupplement.drill

/** 7日間連続をやり切った1回分。[endDay] はその7日目にあたる取組日の添字。 */
data class WeekStreak(val ordinal: Int, val startDayIndex: Int, val endDayIndex: Int)

/**
 * 取組日から「7日間連続の達成」を数える。
 * 14日続けば2回、21日で3回と、7日ごとに1回ずつ増える後退しない累計。
 */
object WeekStreaks {

    /**
     * @param dayNumbers 取組日を連続する整数（Julian day number）にしたもの。昇順・重複なし。
     * @return 達成した順に並べた [WeekStreak]。添字は [dayNumbers] に対応する。
     */
    fun find(dayNumbers: List<Long>): List<WeekStreak> {
        val streaks = mutableListOf<WeekStreak>()
        var runLength = 0
        for (i in dayNumbers.indices) {
            runLength = if (i > 0 && dayNumbers[i] == dayNumbers[i - 1] + 1L) runLength + 1 else 1
            if (runLength % 7 == 0) {
                streaks.add(WeekStreak(ordinal = streaks.size + 1, startDayIndex = i - 6, endDayIndex = i))
            }
        }
        return streaks
    }

    fun count(dayNumbers: List<Long>): Int = find(dayNumbers).size
}
