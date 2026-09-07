package dev.miyado.shogisupplement.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GameListSummaryTest {

    private fun game(
        id: Long,
        moveCount: Long = 100,
        userSide: String? = "sente",
        winner: String? = null,
        rating: Long = 1500,
        status: GameAnalysisStatus = GameAnalysisStatus.COMPLETED,
    ) = GameRecord(
        id = id,
        fileName = "g$id.kif",
        contentHash = "h$id",
        moveCount = moveCount,
        senteName = null,
        goteName = null,
        analyzedAt = 0,
        rating = rating,
        coefVersion = "v1",
        userSide = userSide,
        gameWinner = winner,
        analysisStatus = status,
    )

    @Test
    fun `勝率は勝敗が判る局だけを分母にする`() {
        val games = List(5) { game(it.toLong(), winner = if (it < 2) "sente" else "gote") } +
            game(9, winner = null)
        val s = games.summarize(emptyMap())

        assertEquals(6, s.games)
        assertEquals(5, s.decidedGames)
        assertEquals(2, s.wins)
        assertEquals(40, s.winRatePct)
    }

    @Test
    fun `勝敗が判る局が5局に満たなければ勝率を出さない`() {
        val games = List(4) { game(it.toLong(), winner = "sente") }
        val s = games.summarize(emptyMap())

        assertEquals(4, s.decidedGames)
        assertNull(s.winRatePct, "4局では1局で25ポイント動くため割合として出さない")
    }

    @Test
    fun `悪手率の分母は自分の手数の合計`() {
        // 先手101手 → 51手、後手100手 → 50手。合計101手。
        val games = listOf(
            game(1, moveCount = 101, userSide = "sente"),
            game(2, moveCount = 100, userSide = "gote"),
        )
        val s = games.summarize(mapOf(1L to 6, 2L to 4))

        assertEquals(101, s.userMoves)
        assertEquals(10, s.blunders)
        assertEquals(9, s.blunderRatePct)
    }

    @Test
    fun `分母が50手に満たなければ悪手率を出さない`() {
        val s = listOf(game(1, moveCount = 60, userSide = "gote")).summarize(mapOf(1L to 3))

        assertEquals(30, s.userMoves)
        assertNull(s.blunderRatePct)
    }

    @Test
    fun `未解析の棋譜は分母に入れない`() {
        val games = listOf(
            game(1, moveCount = 200, userSide = "sente"),
            game(2, moveCount = 200, userSide = "sente", status = GameAnalysisStatus.PENDING),
        )
        val s = games.summarize(mapOf(1L to 10))

        assertEquals(2, s.games, "件数そのものは絞り込み結果の全件")
        assertEquals(100, s.userMoves, "未解析ぶんは悪手も手数も確定していないので入れない")
        assertEquals(10, s.blunderRatePct)
    }

    @Test
    fun `自分の側が未設定の棋譜は集計に入らない`() {
        val s = listOf(game(1, userSide = null, winner = "sente")).summarize(mapOf(1L to 5))

        assertEquals(1, s.games)
        assertEquals(0, s.decidedGames)
        assertEquals(0, s.userMoves)
        assertEquals(emptyList(), s.ratings)
    }
}
