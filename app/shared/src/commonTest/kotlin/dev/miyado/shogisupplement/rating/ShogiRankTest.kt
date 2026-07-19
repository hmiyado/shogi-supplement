package dev.miyado.shogisupplement.rating

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShogiRankTest {

    // ---- toDisplayString ----

    @Test
    fun `Kyu（1） は 1級 と表示される`() {
        assertEquals("1級", ShogiRank.Kyu(1).toDisplayString())
    }

    @Test
    fun `Kyu（10） は 10級 と表示される`() {
        assertEquals("10級", ShogiRank.Kyu(10).toDisplayString())
    }

    @Test
    fun `Kyu（30） は 30級 と表示される`() {
        assertEquals("30級", ShogiRank.Kyu(30).toDisplayString())
    }

    @Test
    fun `Dan（1） は 初段 と表示される`() {
        assertEquals("初段", ShogiRank.Dan(1).toDisplayString())
    }

    @Test
    fun `Dan（2） は 二段 と表示される`() {
        assertEquals("二段", ShogiRank.Dan(2).toDisplayString())
    }

    @Test
    fun `Dan（9） は 九段 と表示される`() {
        assertEquals("九段", ShogiRank.Dan(9).toDisplayString())
    }

    // ---- fromRaw ----

    @Test
    fun `fromRaw（1） は Dan（1） を返す`() {
        assertEquals(ShogiRank.Dan(1), ShogiRank.fromRaw(1))
    }

    @Test
    fun `fromRaw（-1） は Kyu（1） を返す`() {
        assertEquals(ShogiRank.Kyu(1), ShogiRank.fromRaw(-1))
    }

    @Test
    fun `fromRaw（-30） は Kyu（30） を返す`() {
        assertEquals(ShogiRank.Kyu(30), ShogiRank.fromRaw(-30))
    }

    @Test
    fun `fromRaw（0） は null を返す`() {
        assertNull(ShogiRank.fromRaw(0))
    }

    @Test
    fun `fromRaw（10） は null を返す`() {
        // 9段を超えるため無効
        assertNull(ShogiRank.fromRaw(10))
    }

    @Test
    fun `fromRaw（-31） は null を返す`() {
        // 30級を超えるため無効
        assertNull(ShogiRank.fromRaw(-31))
    }

    // ---- toRaw の逆変換が fromRaw と一致する ----

    @Test
    fun `toRaw → fromRaw はラウンドトリップする（Dan）`() {
        for (d in 1..9) {
            val rank = ShogiRank.Dan(d)
            assertEquals(rank, ShogiRank.fromRaw(rank.toRaw()))
        }
    }

    @Test
    fun `toRaw → fromRaw はラウンドトリップする（Kyu）`() {
        for (k in 1..30) {
            val rank = ShogiRank.Kyu(k)
            assertEquals(rank, ShogiRank.fromRaw(rank.toRaw()))
        }
    }
}
