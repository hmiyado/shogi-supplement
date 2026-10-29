package dev.miyado.shogisupplement.strength

import dev.miyado.shogisupplement.db.PositionEvalRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MateStatisticsTest {

    private fun row(
        ply: Int,
        mateIn: Int? = null,
        scoreCp: Int? = null,
        verified: Boolean = true,
    ) = PositionEvalRow(ply = ply, scoreCp = scoreCp, mateIn = mateIn, mateVerified = verified)

    private fun moves(count: Int): List<String> = List(count) { "m$it" }

    @Test
    fun `9手詰を見逃した後に5手詰を詰ませると元の手数で1件ずつ数える`() {
        val evaluations = listOf(
            row(0, mateIn = 9),
            row(1, scoreCp = 0),
            row(2, mateIn = 5),
            row(3, mateIn = 4),
            row(4, mateIn = 3),
            row(5, mateIn = 2),
            row(6, mateIn = 1),
        )

        val result = MateOpportunities.compute(moves(6), evaluations, "sente", "sente", "投了")

        assertEquals(
            listOf(
                MateOpportunity(9, MateOutcome.MISSED),
                MateOpportunity(5, MateOutcome.FOUND),
            ),
            result,
        )
    }

    @Test
    fun `5手詰が11手詰に伸びても成功は元の5手で1件`() {
        val evaluations = listOf(
            row(0, mateIn = 5),
            row(1, mateIn = 11),
            row(2, mateIn = 9),
            row(3, mateIn = 7),
            row(4, mateIn = 5),
            row(5, mateIn = 3),
            row(6, mateIn = 1),
        )

        val result = MateOpportunities.compute(moves(6), evaluations, "sente", "sente", "投了")

        assertEquals(listOf(MateOpportunity(5, MateOutcome.FOUND)), result)
    }

    @Test
    fun `途中で正しく指しても見逃しは元の手数で1件`() {
        val evaluations = listOf(
            row(0, mateIn = 9),
            row(1, mateIn = 8),
            row(2, mateIn = 7),
            row(3, scoreCp = 120),
            row(4, scoreCp = 100),
        )

        val result = MateOpportunities.compute(moves(4), evaluations, "sente", "gote", "投了")

        assertEquals(listOf(MateOpportunity(9, MateOutcome.MISSED)), result)
    }

    @Test
    fun `後手ユーザーは保存された先手視点の符号を反転する`() {
        val evaluations = listOf(
            row(0, scoreCp = 0),
            row(1, mateIn = -5),
            row(2, mateIn = -4),
            row(3, mateIn = -3),
            row(4, mateIn = -2),
        )

        val result = MateOpportunities.compute(moves(4), evaluations, "gote", "gote", "詰み")

        assertEquals(listOf(MateOpportunity(5, MateOutcome.FOUND)), result)
    }

    @Test
    fun `mate0は局面の手番側が詰まされたものとして扱う`() {
        val userMated = listOf(
            row(0, mateIn = 0),
            row(1, scoreCp = 0),
        )
        assertTrue(MateOpportunities.compute(moves(1), userMated, "sente", "gote", "詰み").isEmpty())

        val opponentMated = listOf(
            row(0, mateIn = 3),
            row(1, mateIn = 0),
        )
        assertEquals(
            listOf(MateOpportunity(3, MateOutcome.FOUND)),
            MateOpportunities.compute(moves(1), opponentMated, "sente", "sente", "詰み"),
        )
    }

    @Test
    fun `最終局面の詰みは実際のユーザーの着手がないため機会にしない`() {
        val evaluations = listOf(
            row(0, scoreCp = 0),
            row(1, mateIn = -5),
        )

        val result = MateOpportunities.compute(moves(1), evaluations, "gote", "gote", "詰み")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `未確認の詰みからは機会を開始しない`() {
        val evaluations = listOf(
            row(0, mateIn = 5, verified = false),
            row(1, mateIn = 4),
            row(2, mateIn = 3),
        )

        val result = MateOpportunities.compute(moves(2), evaluations, "sente", "sente", "投了")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `欠落した局面を通る機会は成功にしない`() {
        val evaluations = listOf(
            row(0, mateIn = 5),
            row(2, mateIn = 3),
            row(3, mateIn = 1),
        )

        val result = MateOpportunities.compute(moves(3), evaluations, "sente", "sente", "投了")

        assertEquals(listOf(MateOpportunity(5, MateOutcome.UNKNOWN)), result)
    }

    @Test
    fun `重複した局面を通る機会は判定不能`() {
        val evaluations = listOf(
            row(0, mateIn = 5),
            row(1, mateIn = 4),
            row(1, scoreCp = 0),
            row(2, mateIn = 3),
            row(3, mateIn = 1),
        )

        val result = MateOpportunities.compute(moves(3), evaluations, "sente", "sente", "投了")

        assertEquals(listOf(MateOpportunity(5, MateOutcome.UNKNOWN)), result)
    }

    @Test
    fun `評価値も詰みもない局面を通る機会は判定不能`() {
        val evaluations = listOf(
            row(0, mateIn = 5),
            row(1, scoreCp = null, mateIn = null),
            row(2, mateIn = 3),
            row(3, mateIn = 1),
        )

        val result = MateOpportunities.compute(moves(3), evaluations, "sente", "sente", "投了")

        assertEquals(listOf(MateOpportunity(5, MateOutcome.UNKNOWN)), result)
    }

    @Test
    fun `実行中の未確認の詰みは見逃しとして終了する`() {
        val evaluations = listOf(
            row(0, mateIn = 5),
            row(1, mateIn = 4, verified = false),
            row(2, scoreCp = 0),
        )

        val result = MateOpportunities.compute(moves(2), evaluations, "sente", "sente", "投了")

        assertEquals(listOf(MateOpportunity(5, MateOutcome.MISSED)), result)
    }

    @Test
    fun `確認済み5手詰から未確認3手勝ちに変わると見逃し5手`() {
        val evaluations = listOf(
            row(0, mateIn = 5),
            row(1, mateIn = 3, verified = false),
        )

        val result = MateOpportunities.compute(moves(1), evaluations, "sente", "sente", "投了")

        assertEquals(listOf(MateOpportunity(5, MateOutcome.MISSED)), result)
    }

    @Test
    fun `後手の未確認mateは符号にかかわらず見逃し、再登場した確認済み詰みは新しい機会`() {
        val evaluations = listOf(
            row(0, scoreCp = 0),
            row(1, mateIn = -5),
            row(2, mateIn = 7, verified = false),
            row(3, mateIn = -3),
            row(4, mateIn = -2),
            row(5, mateIn = -1),
            row(6, mateIn = 0),
        )

        val result = MateOpportunities.compute(moves(6), evaluations, "gote", "gote", "詰み")

        assertEquals(
            listOf(
                MateOpportunity(5, MateOutcome.MISSED),
                MateOpportunity(3, MateOutcome.FOUND),
            ),
            result,
        )
    }

    @Test
    fun `先行する欠落がある機会は未確認の詰みでも判定不能`() {
        val evaluations = listOf(
            row(0, mateIn = 5),
            row(2, mateIn = 3, verified = false),
        )

        val result = MateOpportunities.compute(moves(2), evaluations, "sente", "sente", "投了")

        assertEquals(listOf(MateOpportunity(5, MateOutcome.UNKNOWN)), result)
    }

    @Test
    fun `相手の確認済み詰みは見逃しとして終了する`() {
        val evaluations = listOf(
            row(0, mateIn = 5),
            row(1, mateIn = -3),
        )

        val result = MateOpportunities.compute(moves(1), evaluations, "sente", "gote", "詰み")

        assertEquals(listOf(MateOpportunity(5, MateOutcome.MISSED)), result)
    }

    @Test
    fun `終局理由が投了か詰みでなければ成功にしない`() {
        val evaluations = listOf(
            row(0, mateIn = 5),
            row(1, mateIn = 3),
            row(2, mateIn = 1),
        )

        assertEquals(
            listOf(MateOpportunity(5, MateOutcome.UNKNOWN)),
            MateOpportunities.compute(moves(2), evaluations, "sente", "sente", "切れ負け"),
        )
        assertEquals(
            listOf(MateOpportunity(5, MateOutcome.UNKNOWN)),
            MateOpportunities.compute(moves(2), evaluations, "sente", null, "投了"),
        )
        assertEquals(
            listOf(MateOpportunity(5, MateOutcome.UNKNOWN)),
            MateOpportunities.compute(moves(2), evaluations, "sente", "gote", "投了"),
        )
    }

    @Test
    fun `集計は割合を平均せず成功数と見逃し数を合算する`() {
        val statistics = MateStatistics.aggregate(
            opportunities = listOf(
                MateOpportunity(1, MateOutcome.FOUND),
                MateOpportunity(3, MateOutcome.FOUND),
                MateOpportunity(3, MateOutcome.MISSED),
                MateOpportunity(3, MateOutcome.MISSED),
                MateOpportunity(3, MateOutcome.MISSED),
            ),
            analyzedGames = 4,
            verifiedGames = 2,
        )

        val one = statistics.buckets.first { it.plies == 1 }
        val three = statistics.buckets.first { it.plies == 3 }
        assertEquals(1.0, one.successRate)
        assertEquals(0.25, three.successRate)
        assertEquals(4, statistics.analyzedGames)
        assertEquals(2, statistics.verifiedGames)
    }

    @Test
    fun `機会のない奇数区分は欠測として割合を出さない`() {
        val statistics = MateStatistics.aggregate(
            opportunities = listOf(
                MateOpportunity(1, MateOutcome.FOUND),
                MateOpportunity(5, MateOutcome.MISSED),
            ),
            analyzedGames = 2,
            verifiedGames = 2,
        )

        assertEquals(listOf(1, 3, 5), statistics.buckets.map { it.plies })
        val gap = statistics.buckets.first { it.plies == 3 }
        assertEquals(0, gap.decided)
        assertNull(gap.successRate)
    }

    @Test
    fun `31手以上は31に合算し実際の最大までの区分を作る`() {
        val statistics = MateStatistics.aggregate(
            opportunities = listOf(
                MateOpportunity(31, MateOutcome.FOUND),
                MateOpportunity(33, MateOutcome.MISSED),
                MateOpportunity(35, MateOutcome.FOUND),
            ),
            analyzedGames = 3,
            verifiedGames = 3,
        )

        assertEquals(16, statistics.buckets.size)
        assertEquals(31, statistics.buckets.last().plies)
        assertEquals(2, statistics.buckets.last().found)
        assertEquals(1, statistics.buckets.last().missed)
        assertEquals(MateStatistics.LAST_BUCKET, 31)
    }

    @Test
    fun `判定不能は成功率の分母から除外する`() {
        val statistics = MateStatistics.aggregate(
            opportunities = listOf(
                MateOpportunity(1, MateOutcome.FOUND),
                MateOpportunity(1, MateOutcome.FOUND),
                MateOpportunity(1, MateOutcome.MISSED),
                MateOpportunity(1, MateOutcome.UNKNOWN),
                MateOpportunity(1, MateOutcome.UNKNOWN),
            ),
            analyzedGames = 1,
            verifiedGames = 1,
        )

        val bucket = statistics.buckets.single()
        assertEquals(3, bucket.decided)
        assertEquals(2, bucket.unknown)
        assertEquals(2.0 / 3.0, bucket.successRate)
    }

    @Test
    fun `機会がない場合は空の区分と0件のまま返す`() {
        val statistics = MateStatistics.aggregate(emptyList(), analyzedGames = 7, verifiedGames = 3)

        assertTrue(statistics.buckets.isEmpty())
        assertEquals(7, statistics.analyzedGames)
        assertEquals(3, statistics.verifiedGames)
        assertEquals(MateStatistics(), MateStatistics())
    }
}
