package dev.miyado.shogisupplement.db

import kotlin.test.Test
import kotlin.test.assertEquals

class GameListFilterTest {

    private fun game(
        id: Long,
        sourcePlace: String? = null,
        userSide: String? = null,
        openingStyle: String? = null,
        openingTags: String? = openingStyle,
        gameWinner: String? = null,
        analyzedAt: Long = 1_000L,
        timeControlRaw: String? = null,
        timeControlByoyomiRaw: String? = null,
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
        openingTags = openingTags,
        gameWinner = gameWinner,
        timeControlRaw = timeControlRaw,
        timeControlByoyomiRaw = timeControlByoyomiRaw,
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
        assertEquals(true, GameListFilter(timeControl = "3分切れ負け").isActive)
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
    fun `戦型フィルタは代表でない戦型でも残す`() {
        val games = listOf(
            game(1, openingStyle = "角換わり", openingTags = "角換わり|棒銀"),
            game(2, openingStyle = "四間飛車"),
            game(3, openingStyle = null, openingTags = null),
        )
        assertEquals(listOf(1L), games.filterGames(GameListFilter(openingStyle = "棒銀")).map { it.id })
        assertEquals(listOf(1L), games.filterGames(GameListFilter(openingStyle = "角換わり")).map { it.id })
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
    fun `distinctOpeningStylesはタグを重複なく代表順で返す`() {
        val games = listOf(
            game(1, openingStyle = "四間飛車"),
            game(2, openingStyle = "角換わり", openingTags = "角換わり|棒銀"),
            game(3, openingStyle = "四間飛車"),
            game(4, openingStyle = null, openingTags = null),
        )
        assertEquals(listOf("角換わり", "四間飛車", "棒銀"), games.distinctOpeningStyles())
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

    // ─── 持ち時間 ────────────────────────────────────────────────────────────

    @Test
    fun `持ち時間フィルタはサービスごとの呼び名に変換した表示文字列で一致する`() {
        val games = listOf(
            game(1, sourcePlace = "kiou", timeControlRaw = "3分切れ負け"),
            game(2, sourcePlace = "kiou", timeControlRaw = "5分+5秒追加"),
            game(3, sourcePlace = "lishogi", timeControlRaw = "10分+30秒"),
            game(4, sourcePlace = "wars", timeControlRaw = "0分", timeControlByoyomiRaw = "30秒"),
        )
        assertEquals(
            listOf(1L),
            games.filterGames(GameListFilter(timeControl = "ショート（3分切れ負け）")).map { it.id },
        )
        assertEquals(
            listOf(2L),
            games.filterGames(GameListFilter(timeControl = "フィッシャー（5分+5秒追加）")).map { it.id },
        )
        assertEquals(
            listOf(3L),
            games.filterGames(GameListFilter(timeControl = "10分秒読み30秒")).map { it.id },
        )
        assertEquals(
            listOf(4L),
            games.filterGames(GameListFilter(timeControl = "1手30秒")).map { it.id },
        )
    }

    @Test
    fun `判定表に無い持ち時間はサービスや値が違ってもその他へまとまる`() {
        val games = listOf(
            game(1, sourcePlace = "wars", timeControlRaw = "7分+15秒"),
            game(2, sourcePlace = "kiou", timeControlRaw = "7分+15秒"),
            game(3, sourcePlace = null, timeControlRaw = "5分+30秒"),
        )
        assertEquals(
            listOf(1L, 2L, 3L),
            games.filterGames(GameListFilter(timeControl = TIME_CONTROL_OTHER)).map { it.id },
        )
    }

    @Test
    fun `同じヘッダでもサービスごとの呼び名が違えば別の持ち時間として扱う`() {
        val games = listOf(
            game(1, sourcePlace = "kiou", timeControlRaw = "3分切れ負け"),
            game(2, sourcePlace = "wars", timeControlRaw = "3分切れ負け"),
        )
        assertEquals(
            listOf(1L),
            games.filterGames(GameListFilter(timeControl = "ショート（3分切れ負け）")).map { it.id },
        )
        assertEquals(
            listOf(2L),
            games.filterGames(GameListFilter(timeControl = "3分切れ負け")).map { it.id },
        )
    }

    @Test
    fun `持ち時間ヘッダを持たないレコードはその他にも含まれない`() {
        val games = listOf(
            game(1, sourcePlace = "wars", timeControlRaw = "7分+15秒"),
            game(2, sourcePlace = "wars"),
        )
        assertEquals(
            listOf(1L),
            games.filterGames(GameListFilter(timeControl = TIME_CONTROL_OTHER)).map { it.id },
        )
    }

    @Test
    fun `distinctTimeControlsはルールを昇順で並べその他を末尾に置く`() {
        val games = listOf(
            game(1, sourcePlace = "wars", timeControlRaw = "3分切れ負け"),
            game(2, sourcePlace = "kiou", timeControlRaw = "3分切れ負け"),
            game(3, sourcePlace = "wars", timeControlRaw = "0分", timeControlByoyomiRaw = "30秒"),
            game(4, sourcePlace = "wars", timeControlRaw = "5分+30秒"),
            game(5, sourcePlace = "wars"),
        )
        assertEquals(
            listOf("1手30秒", "3分切れ負け", "ショート（3分切れ負け）", TIME_CONTROL_OTHER),
            games.distinctTimeControls(),
        )
    }

    @Test
    fun `distinctTimeControlsは持ち時間を持つレコードが無ければ空を返す`() {
        assertEquals(emptyList(), listOf(game(1, sourcePlace = "wars")).distinctTimeControls())
    }

    // ─── 出典と持ち時間の連動 ────────────────────────────────────────────────

    private fun gamesWithSourcesAndTimeControls() = listOf(
        game(1, sourcePlace = "wars", timeControlRaw = "0分", timeControlByoyomiRaw = "30秒"),
        game(2, sourcePlace = "lishogi", timeControlRaw = "10分+30秒"),
        game(3, sourcePlace = "kiou", timeControlRaw = "7分+15秒"),
    )

    @Test
    fun `availableTimeControlsは指定した出典に現れる持ち時間だけ返す`() {
        val games = gamesWithSourcesAndTimeControls()
        assertEquals(listOf("1手30秒"), games.availableTimeControls("wars"))
        assertEquals(listOf("10分秒読み30秒"), games.availableTimeControls("lishogi"))
        assertEquals(listOf(TIME_CONTROL_OTHER), games.availableTimeControls("kiou"))
    }

    @Test
    fun `棋桜の10分30秒はラベルが無くても棋桜の持ち時間としてその他と別に扱う`() {
        val games = listOf(
            game(1, sourcePlace = "kiou", timeControlRaw = "10分+30秒"),
            game(2, sourcePlace = "kiou", timeControlRaw = "7分+15秒"),
        )
        assertEquals(listOf("10分+30秒", TIME_CONTROL_OTHER), games.distinctTimeControls())
        assertEquals(
            listOf(1L),
            games.filterGames(GameListFilter(timeControl = "10分+30秒")).map { it.id },
        )
    }

    @Test
    fun `availableTimeControlsは出典未指定なら全棋譜から返す`() {
        assertEquals(
            listOf("10分秒読み30秒", "1手30秒", TIME_CONTROL_OTHER),
            gamesWithSourcesAndTimeControls().availableTimeControls(null),
        )
    }

    @Test
    fun `clearUnavailableTimeControlは出典に無い持ち時間の指定を外す`() {
        val games = gamesWithSourcesAndTimeControls()
        val filter = GameListFilter(source = "wars", timeControl = "10分秒読み30秒")
        assertEquals(null, filter.clearUnavailableTimeControl(games).timeControl)
        assertEquals("wars", filter.clearUnavailableTimeControl(games).source)
    }

    @Test
    fun `clearUnavailableTimeControlは出典にある持ち時間なら残す`() {
        val games = gamesWithSourcesAndTimeControls()
        val filter = GameListFilter(source = "wars", timeControl = "1手30秒")
        assertEquals(filter, filter.clearUnavailableTimeControl(games))
    }

    @Test
    fun `clearUnavailableTimeControlは出典未指定なら何も外さない`() {
        val games = gamesWithSourcesAndTimeControls()
        val filter = GameListFilter(timeControl = "10分秒読み30秒")
        assertEquals(filter, filter.clearUnavailableTimeControl(games))
    }

    @Test
    fun `clearUnavailableTimeControlは持ち時間未指定なら何もしない`() {
        val games = gamesWithSourcesAndTimeControls()
        val filter = GameListFilter(source = "wars")
        assertEquals(filter, filter.clearUnavailableTimeControl(games))
    }
}
