package dev.miyado.shogisupplement.drill

import kotlin.test.Test
import kotlin.test.assertEquals

/** 7日間連続の達成が、後退しない累計として数えられることを保証する。 */
class WeekStreaksTest {

    private fun days(vararg d: Long) = d.toList()

    @Test
    fun `6日では達成しない`() {
        assertEquals(0, WeekStreaks.count(days(1, 2, 3, 4, 5, 6)))
    }

    @Test
    fun `7日続けば1回`() {
        val streaks = WeekStreaks.find(days(1, 2, 3, 4, 5, 6, 7))
        assertEquals(1, streaks.size)
        assertEquals(WeekStreak(ordinal = 1, startDayIndex = 0, endDayIndex = 6), streaks.single())
    }

    @Test
    fun `14日続けば2回になり、8日目からの重なりでは数えない`() {
        val streaks = WeekStreaks.find(days(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14))
        assertEquals(2, streaks.size)
        assertEquals(listOf(0 to 6, 7 to 13), streaks.map { it.startDayIndex to it.endDayIndex })
    }

    @Test
    fun `1日空くと連続は切れ、そこから数え直す`() {
        // 6日 → 1日空き → 7日。後半だけが達成になる。
        val streaks = WeekStreaks.find(days(1, 2, 3, 4, 5, 6, 8, 9, 10, 11, 12, 13, 14))
        assertEquals(1, streaks.size)
        assertEquals(6, streaks.single().startDayIndex)
        assertEquals(12, streaks.single().endDayIndex)
    }

    @Test
    fun `離れた達成は別々に数える`() {
        val first = (1L..7L).toList()
        val second = (20L..26L).toList()
        val streaks = WeekStreaks.find(first + second)
        assertEquals(2, streaks.size)
        assertEquals(listOf(1, 2), streaks.map { it.ordinal })
    }

    @Test
    fun `取組日が無ければ達成も無い`() {
        assertEquals(emptyList(), WeekStreaks.find(emptyList()))
    }
}
