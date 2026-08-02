package dev.miyado.shogisupplement.rating

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RankScaleTest {

    private val wars = RankScale(maxKyu = 30)
    private val kiou = RankScale(maxKyu = 10)

    @Test
    fun `ウォーズ目盛りは30級から九段まで39段階`() {
        assertEquals(39, wars.labels.size)
        assertEquals("30級", wars.labels.first())
        assertEquals("九段", wars.labels.last())
        assertEquals("1級", wars.labels[29])
        assertEquals("初段", wars.labels[30])
    }

    @Test
    fun `棋桜目盛りは10級から九段まで19段階`() {
        assertEquals(19, kiou.labels.size)
        assertEquals("10級", kiou.labels.first())
        assertEquals("1級", kiou.labels[9])
        assertEquals("初段", kiou.labels[10])
        assertEquals("九段", kiou.labels.last())
    }

    @Test
    fun `全インデックスがfromIndex-toIndexで往復する`() {
        for (scale in listOf(wars, kiou)) {
            for (i in scale.labels.indices) {
                assertEquals(i, scale.toIndex(scale.fromIndex(i)))
            }
        }
    }

    @Test
    fun `目盛り下限より低い級は最下位へ丸める`() {
        assertEquals(0, kiou.toIndex(ShogiRank.Kyu(30)))
        assertEquals(0, kiou.toIndex(ShogiRank.Kyu(11)))
        assertEquals(0, kiou.toIndex(ShogiRank.Kyu(10)))
    }

    @Test
    fun `範囲外インデックスは例外`() {
        assertFailsWith<IllegalArgumentException> { kiou.fromIndex(19) }
        assertFailsWith<IllegalArgumentException> { kiou.fromIndex(-1) }
    }
}
