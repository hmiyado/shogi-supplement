package dev.miyado.shogisupplement.db

import kotlin.test.Test
import kotlin.test.assertEquals

class GameListFilterTest {

    private fun game(
        id: Long,
        sourcePlace: String? = null,
        userSide: String? = null,
        openingStyle: String? = null,
        gameWinner: String? = null,
        analyzedAt: Long = 1_000L,
    ) = GameRecord(
        id = id,
        fileName = "g$id.kif",
        contentHash = "hash$id",
        moveCount = 10L,
        senteName = null,
        goteName = null,
        analyzedAt = analyzedAt,
        rating = 1750L,
        coefVersion = "hao_v1",
        sourcePlace = sourcePlace,
        userSide = userSide,
        openingStyle = openingStyle,
        gameWinner = gameWinner,
    )

    // ─── isActive ────────────────────────────────────────────────────────────

    @Test
    fun `全軸未指定ならisActiveはfalse`() {
        assertEquals(false, GameListFilter().isActive)
    }

    @Test
    fun `いずれか1軸でも指定されればisActiveはtrue`() {
        assertEquals(true, GameListFilter(source = "wars").isActive)
        assertEquals(true, GameListFilter(userSide = "sente").isActive)
        assertEquals(true, GameListFilter(openingStyle = "居飛車").isActive)
        assertEquals(true, GameListFilter(result = GameResultFilter.WIN).isActive)
        assertEquals(true, GameListFilter(dateFrom = 100L).isActive)
    }

    // ─── activeCount ─────────────────────────────────────────────────────────

    @Test
    fun `activeCountは全軸未指定なら0`() {
        assertEquals(0, GameListFilter().activeCount)
    }

    @Test
    fun `activeCountは指定されている軸の数を返す`() {
        assertEquals(1, GameListFilter(source = "wars").activeCount)
        assertEquals(2, GameListFilter(source = "wars", userSide = "sente").activeCount)
        assertEquals(
            5,
            GameListFilter(
                source = "wars",
                userSide = "sente",
                openingStyle = "居飛車",
                result = GameResultFilter.WIN,
                dateFrom = 100L,
            ).activeCount,
        )
    }

    // ─── filterGames: 未指定時はそのまま返す ────────────────────────────────────

    @Test
    fun `フィルタ未指定なら全件そのまま返す`() {
        val games = listOf(game(1), game(2), game(3))
        assertEquals(games, games.filterGames(GameListFilter()))
    }

    // ─── 出典 ────────────────────────────────────────────────────────────────

    @Test
    fun `出典フィルタは一致するsourcePlaceのみ残す`() {
        val games = listOf(
            game(1, sourcePlace = "wars"),
            game(2, sourcePlace = "lishogi"),
            game(3, sourcePlace = "wars"),
            game(4, sourcePlace = null),
        )
        val result = games.filterGames(GameListFilter(source = "wars"))
        assertEquals(listOf(1L, 3L), result.map { it.id })
    }

    // ─── 先後 ────────────────────────────────────────────────────────────────

    @Test
    fun `先後フィルタは一致するuserSideのみ残す`() {
        val games = listOf(
            game(1, userSide = "sente"),
            game(2, userSide = "gote"),
            game(3, userSide = null),
        )
        val result = games.filterGames(GameListFilter(userSide = "sente"))
        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun `戦型フィルタは一致するopeningStyleのみ残す`() {
        val games = listOf(
            game(1, openingStyle = "居飛車"),
            game(2, openingStyle = "四間飛車"),
            game(3, openingStyle = null),
        )
        val result = games.filterGames(GameListFilter(openingStyle = "居飛車"))
        assertEquals(listOf(1L), result.map { it.id })
    }

    // ─── 勝敗 ────────────────────────────────────────────────────────────────

    @Test
    fun `勝ちフィルタはuserSideとgameWinnerが一致するもののみ残す`() {
        val games = listOf(
            game(1, userSide = "sente", gameWinner = "sente"), // 勝ち
            game(2, userSide = "sente", gameWinner = "gote"),  // 負け
            game(3, userSide = "gote", gameWinner = "gote"),   // 勝ち
        )
        val result = games.filterGames(GameListFilter(result = GameResultFilter.WIN))
        assertEquals(listOf(1L, 3L), result.map { it.id })
    }

    @Test
    fun `負けフィルタはuserSideとgameWinnerが不一致のもののみ残す`() {
        val games = listOf(
            game(1, userSide = "sente", gameWinner = "sente"), // 勝ち
            game(2, userSide = "sente", gameWinner = "gote"),  // 負け
        )
        val result = games.filterGames(GameListFilter(result = GameResultFilter.LOSS))
        assertEquals(listOf(2L), result.map { it.id })
    }

    @Test
    fun `勝敗フィルタはuserSideかgameWinnerが欠けているレコードを除外する`() {
        val games = listOf(
            game(1, userSide = "sente", gameWinner = "sente"),
            game(2, userSide = null, gameWinner = "sente"),
            game(3, userSide = "sente", gameWinner = null),
        )
        val result = games.filterGames(GameListFilter(result = GameResultFilter.WIN))
        assertEquals(listOf(1L), result.map { it.id })
    }

    // ─── 日付 ────────────────────────────────────────────────────────────────

    @Test
    fun `dateFromは解析日時がその値以上のレコードのみ残す`() {
        val games = listOf(
            game(1, analyzedAt = 100L),
            game(2, analyzedAt = 200L),
            game(3, analyzedAt = 300L),
        )
        val result = games.filterGames(GameListFilter(dateFrom = 200L))
        assertEquals(listOf(2L, 3L), result.map { it.id })
    }

    // ─── AND結合 ─────────────────────────────────────────────────────────────

    @Test
    fun `複数軸を指定するとAND結合で絞り込まれる`() {
        val games = listOf(
            game(1, sourcePlace = "wars", userSide = "sente", gameWinner = "sente", analyzedAt = 500L),
            game(2, sourcePlace = "wars", userSide = "gote", gameWinner = "gote", analyzedAt = 500L),
            game(3, sourcePlace = "lishogi", userSide = "sente", gameWinner = "sente", analyzedAt = 500L),
            game(4, sourcePlace = "wars", userSide = "sente", gameWinner = "gote", analyzedAt = 500L),
        )
        val result = games.filterGames(
            GameListFilter(source = "wars", userSide = "sente", result = GameResultFilter.WIN),
        )
        assertEquals(listOf(1L), result.map { it.id })
    }

    // ─── distinctSources ────────────────────────────────────────────────────

    @Test
    fun `distinctSourcesはKifuSourceの並び順で重複なく実在する値のみ返す`() {
        val games = listOf(
            game(1, sourcePlace = "other"),
            game(2, sourcePlace = "wars"),
            game(3, sourcePlace = "wars"),
            game(4, sourcePlace = null),
        )
        assertEquals(listOf("wars", "other"), games.distinctSources())
    }

    @Test
    fun `distinctSourcesは空リストなら空を返す`() {
        assertEquals(emptyList(), emptyList<GameRecord>().distinctSources())
    }

    @Test
    fun `distinctOpeningStylesは実在する値を重複なくソートして返す`() {
        val games = listOf(
            game(1, openingStyle = "四間飛車"),
            game(2, openingStyle = "居飛車"),
            game(3, openingStyle = "四間飛車"),
            game(4, openingStyle = null),
        )
        assertEquals(listOf("四間飛車", "居飛車").sorted(), games.distinctOpeningStyles())
    }

    // ─── hasUserSideData / hasResultData ────────────────────────────────────

    @Test
    fun `hasUserSideDataはuserSideを持つレコードが1件でもあればtrue`() {
        assertEquals(true, listOf(game(1, userSide = "sente")).hasUserSideData())
        assertEquals(false, listOf(game(1, userSide = null)).hasUserSideData())
    }

    @Test
    fun `hasResultDataはuserSideとgameWinnerが両方揃うレコードが1件でもあればtrue`() {
        assertEquals(
            true,
            listOf(game(1, userSide = "sente", gameWinner = "sente")).hasResultData(),
        )
        assertEquals(
            false,
            listOf(game(1, userSide = "sente", gameWinner = null)).hasResultData(),
        )
        assertEquals(
            false,
            listOf(game(1, userSide = null, gameWinner = "sente")).hasResultData(),
        )
    }
}
