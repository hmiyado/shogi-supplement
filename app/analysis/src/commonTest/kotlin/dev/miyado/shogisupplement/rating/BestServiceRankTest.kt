package dev.miyado.shogisupplement.rating

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BestServiceRankTest {

    @Test
    fun `空のMapならnullを返す`() {
        assertNull(bestServiceRank(emptyMap()))
    }

    @Test
    fun `lishogiのみのMapならnullを返す（段級位ではないため対象外）`() {
        assertNull(bestServiceRank(mapOf("lishogi" to mapOf("main" to 5))))
    }

    @Test
    fun `単一サービス内でrawが最大のルールを選ぶ`() {
        val ranks = mapOf(
            "shogi_wars" to mapOf("10min" to ShogiRank.Kyu(2).toRaw(), "3min" to ShogiRank.Dan(1).toRaw()),
        )
        val best = bestServiceRank(ranks)
        assertEquals(BestServiceRank("shogi_wars", "3min", ShogiRank.Dan(1).toRaw()), best)
    }

    @Test
    fun `サービスをまたいでrawが最大のものを選ぶ`() {
        val ranks = mapOf(
            "shogi_wars" to mapOf("10min" to ShogiRank.Kyu(2).toRaw()),
            "kiou" to mapOf("serious" to ShogiRank.Dan(2).toRaw()),
        )
        val best = bestServiceRank(ranks)
        assertEquals(BestServiceRank("kiou", "serious", ShogiRank.Dan(2).toRaw()), best)
    }

    @Test
    fun `lishogiは他サービスと混在していても比較対象から除外される`() {
        val ranks = mapOf(
            "lishogi" to mapOf("main" to 9999),
            "shogi_wars" to mapOf("10min" to ShogiRank.Kyu(5).toRaw()),
        )
        val best = bestServiceRank(ranks)
        assertEquals("shogi_wars", best?.service)
    }
}
