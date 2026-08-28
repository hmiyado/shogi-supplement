package dev.miyado.shogisupplement.drill

import kotlin.test.Test
import kotlin.test.assertEquals

class DrillReadPvMatchTest {

    @Test
    fun 完全一致なら全手数を返す() {
        val userPv = listOf("8h2b+", "3a2b", "2b2c")
        val actualPv = listOf("8h2b+", "3a2b", "2b2c")
        assertEquals(3, DrillReadPvMatch.matchLength(userPv, actualPv))
    }

    @Test
    fun 先頭から食い違えば0を返す() {
        val userPv = listOf("7g6f")
        val actualPv = listOf("8h2b+")
        assertEquals(0, DrillReadPvMatch.matchLength(userPv, actualPv))
    }

    @Test
    fun 途中から食い違えばそこまでの手数を返す() {
        val userPv = listOf("8h2b+", "3a2b", "6f6e")
        val actualPv = listOf("8h2b+", "3a2b", "2b2c")
        assertEquals(2, DrillReadPvMatch.matchLength(userPv, actualPv))
    }

    @Test
    fun 片方が短ければ短い方の長さで頭打ちになる() {
        val userPv = listOf("8h2b+")
        val actualPv = listOf("8h2b+", "3a2b", "2b2c")
        assertEquals(1, DrillReadPvMatch.matchLength(userPv, actualPv))
    }

    @Test
    fun 読み筋が空なら0を返す() {
        assertEquals(0, DrillReadPvMatch.matchLength(emptyList(), listOf("8h2b+")))
        assertEquals(0, DrillReadPvMatch.matchLength(listOf("8h2b+"), emptyList()))
    }
}
