package dev.miyado.shogisupplement.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.repertoire.*
import kotlin.test.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher

class GameListReaderTest {
    private fun database(test: (ShogiSupplementDatabase, SqlDelightGameRepository, JdbcSqliteDriver) -> Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            ShogiSupplementDatabase.Schema.create(driver)
            val db = ShogiSupplementDatabase(driver)
            test(db, SqlDelightGameRepository(db), driver)
        }
    }

    private fun add(repo: GameRepository, n: Int, pending: Boolean = false, moves: List<String> = listOf("7g7f", "3c3d")): Long {
        val side = if (n % 3 == 0) null else if (n % 2 == 0) "sente" else "gote"
        if (pending) return repo.savePendingGame("$n.kif", "hash$n", moves, emptyMap(), importedAt = 1000,
            kifText = "1 ７六歩(77)\n2 ３四歩(33)\n3 投了", userSide = side)
        return repo.saveAnalysis("$n.kif", "hash$n", moves, emptyMap(), emptyList(), 1000 + n,
            coefVersion = "test", analyzedAt = 1000, userSide = side, gameWinner = if (n % 4 == 0) "sente" else "gote",
            sourcePlace = if (n % 2 == 0) "wars" else "lishogi", openingTags = if (n % 2 == 0) "居飛車|相居飛車" else "振り飛車",
            timeControlRaw = if (n % 2 == 0) "10分" else "独自ルール")
    }

    private fun ready(reader: GameListReader): Int {
        var processed = 0
        do { val batch = reader.indexBatch(); processed += batch } while (batch > 0)
        return processed
    }

    @Test fun pagesHaveStableTiesAndSummaryCoversUnreadGames() = database { _, repo, _ ->
        repeat(95) { add(repo, it, pending = it == 94) }
        val reader = repo.gameListQueries
        assertEquals(95, ready(reader))
        val expected = repo.getAllGames().sortedWith(compareByDescending<GameRecord> { it.analyzedAt }.thenByDescending { it.id })
        var cursor: GamePageCursor? = null
        val loaded = mutableListOf<GameRecord>()
        do {
            val page = reader.page(null, GameListFilter(), cursor, 17)
            assertTrue(page.games.size <= 17)
            loaded += page.games
            cursor = page.next
        } while (cursor != null)
        assertEquals(expected.map { it.id }, loaded.map { it.id })
        assertEquals(expected.summarize(repo.getBlunderCounts()), reader.summary(null, GameListFilter()))
        assertEquals(0, ready(reader))
    }

    @Test fun sqlFiltersAndFacetsMatchExistingRulesAcrossAllPages() = database { _, repo, _ ->
        repeat(75) { add(repo, it, pending = it % 11 == 0) }
        val reader = repo.gameListQueries
        ready(reader)
        val all = repo.getAllGames().sortedWith(compareByDescending<GameRecord> { it.analyzedAt }.thenByDescending { it.id })
        val filters = listOf(GameListFilter(), GameListFilter(source = "wars"), GameListFilter(userSide = "gote"),
            GameListFilter(result = GameResultFilter.WIN), GameListFilter(result = GameResultFilter.LOSS),
            GameListFilter(openingStyle = "居飛車"), GameListFilter(timeControl = TIME_CONTROL_OTHER),
            GameListFilter(dateFrom = 1000, dateTo = 1000), GameListFilter(dateFrom = 1001),
            GameListFilter(source = "wars", result = GameResultFilter.WIN, userSide = "sente", openingStyle = "相居飛車"))
        for (filter in filters) {
            val expected = all.filterGames(filter)
            assertEquals(expected.map { it.id }, reader.page(null, filter, limit = 100).games.map { it.id }, "$filter")
            assertEquals(expected.summarize(repo.getBlunderCounts()), reader.summary(null, filter), "$filter")
        }
        val facets = reader.facets(null)
        assertEquals(all.distinctSources(), facets.sources)
        assertEquals(all.distinctOpeningStyles(), facets.openings)
        assertEquals(all.distinctTimeControls(), facets.timeControls(null))
        assertEquals(all.availableTimeControls("wars"), facets.timeControls("wars"))
    }

    @Test fun labelsMatchUnreadGamesAndRemainIsolatedByOwner() = database { db, repo, _ ->
        repeat(65) { add(repo, it) }
        val labels = SqlDelightRepertoireRepository(db)
        val reader = repo.gameListQueries
        ready(reader)
        val board = ShogiBoard().also { it.push(ShogiMove.fromUsi("7g7f")) }
        assertTrue(labels.addPositionLabel("alice", board.toSfen(), PositionLabelScope.ALL, "序盤"))
        assertTrue(labels.addPositionLabel("bob", board.toSfen(), PositionLabelScope.ALL, "別ラベル"))
        assertEquals(2, ready(reader))
        val filter = GameListFilter(positionLabel = "序盤")
        assertEquals(65, reader.summary("alice", filter).games)
        assertEquals(40, reader.page("alice", filter).games.size)
        assertTrue(reader.page("alice", filter).games.all { it.positionLabels == setOf("序盤") })
        assertEquals(0, reader.summary("bob", filter).games)
        assertTrue(reader.page(null, GameListFilter()).games.all { it.positionLabels.isEmpty() })
        val entry = labels.entries("alice").single()
        assertTrue(labels.removePositionLabel("alice", entry, "序盤"))
        assertEquals(1, ready(reader))
        assertEquals(0, reader.summary("alice", filter).games)
        assertEquals(0, ready(SqlDelightGameRepository(db).gameListQueries))
    }

    @Test fun changedStudyOnlyReindexesOneGameAndDeletionRemovesMatches() = database { db, repo, _ ->
        val id = add(repo, 1, pending = true)
        repeat(5) { add(repo, it + 2) }
        val reader = repo.gameListQueries
        ready(reader)
        val original = assertNotNull(repo.getStudyKif(id))
        val edited = "$original\n\n変化：2手\n2 ８四歩(83)\n"
        assertTrue(repo.saveStudyKif(id, original, edited))
        assertEquals(1, ready(reader))
        val board = ShogiBoard().also { it.push(ShogiMove.fromUsi("7g7f")); it.push(ShogiMove.fromUsi("8c8d")) }
        SqlDelightRepertoireRepository(db).addPositionLabel("alice", board.toSfen(), PositionLabelScope.ALL, "分岐")
        ready(reader)
        assertEquals(listOf(id), reader.page("alice", GameListFilter(positionLabel = "分岐")).games.map { it.id })
        repo.deleteGame(id)
        assertEquals(0, ready(reader))
        assertEquals(0, reader.summary("alice", GameListFilter(positionLabel = "分岐")).games)
        assertEquals(listOf("分岐"), reader.facets("alice").knownLabels)
        assertTrue(reader.facets("alice").matchingLabels.isEmpty())
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun databaseNotificationsDoNotLoopWhenIndexIsUpdated() = runTest {
        database { db, repo, _ ->
            var notifications = 0
            val reader = repo.gameListQueries
            val listener = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                reader.changes().collect { notifications++ }
            }
            assertEquals(1, notifications)
            add(repo, 1)
            assertTrue(notifications > 1)
            val afterInsert = notifications
            ready(reader)
            assertEquals(afterInsert, notifications)
            SqlDelightRepertoireRepository(db).addPositionLabel("alice", ShogiBoard().toSfen(), PositionLabelScope.ALL, "初期局面")
            assertTrue(notifications > afterInsert)
            val afterLabel = notifications
            ready(reader)
            assertEquals(afterLabel, notifications)
            listener.cancel()
        }
    }

    @Test fun scopeAndTurnRulesMatchReplayWithoutMultiplyingCounts() = database { db, repo, _ ->
        repeat(3) { add(repo, it) }
        val labels = SqlDelightRepertoireRepository(db)
        val board = ShogiBoard().also { it.push(ShogiMove.fromUsi("7g7f")) }
        val oppositeTurn = board.toSfen().replace(" w ", " b ")
        for (scope in PositionLabelScope.entries) assertTrue(labels.addPositionLabel("alice", oppositeTurn, scope, scope.name))
        val reader = repo.gameListQueries
        ready(reader)
        val expected = labels.labelledGames("alice", repo.getAllGames()).associate { it.id to it.positionLabels }
        val actual = reader.page("alice", GameListFilter()).games.associate { it.id to it.positionLabels }
        assertEquals(expected, actual)
        assertEquals(3, reader.count("alice", GameListFilter()))
        assertEquals(3, reader.summary("alice", GameListFilter(positionLabel = "先手：BLACK")).games)
        assertEquals(0, ready(reader))
        repo.updateUserSide(1, "sente", null, null)
        assertEquals(0, ready(reader))
        assertEquals(repo.getAllGames().count { it.userSide == "sente" }, reader.count("alice", GameListFilter(userSide = "sente")))
    }

    @Test fun warmPageReadsOnlyItsRecordsAndDoesNotReplayGames() = database { db, repo, _ ->
        repeat(130) { add(repo, it) }
        ready(repo.gameListQueries)
        var recordsRead = 0
        val tracked = object : GameRepository by repo {
            override fun getAllGames(): List<GameRecord> = error("Full list must not be loaded")
            override fun getGameById(gameId: Long): GameRecord? {
                recordsRead++
                return repo.getGameById(gameId)
            }
        }
        val reader = SqlDelightGameListReader(db, tracked)
        assertEquals(0, reader.indexBatch())
        reader.summary(null, GameListFilter())
        reader.facets(null)
        assertEquals(0, recordsRead)
        assertEquals(40, reader.page(null, GameListFilter()).games.size)
        assertEquals(40, recordsRead)
    }

    @Test fun benchmarkThousandGamesWhenRequested() = database { db, repo, _ ->
        if (System.getProperty("gameListBenchmark") != "true") return@database
        val random = kotlin.random.Random(7)
        val board = ShogiBoard()
        val moves = buildList {
            repeat(100) {
                val candidates = board.legalMoves()
                if (candidates.isEmpty()) return@repeat
                val move = candidates[random.nextInt(candidates.size)]
                add(move.toUsiString())
                board.push(move)
            }
        }
        assertEquals(100, moves.size)
        repeat(1000) { add(repo, it, moves = moves) }
        val labels = SqlDelightRepertoireRepository(db)
        labels.addPositionLabel("alice", ShogiBoard().toSfen(), PositionLabelScope.ALL, "初期局面")
        val oldStart = System.nanoTime()
        assertEquals(1000, labels.labelledGames("alice", repo.getAllGames()).size)
        val oldMs = (System.nanoTime() - oldStart) / 1_000_000.0
        val reader = repo.gameListQueries
        val coldStart = System.nanoTime()
        assertEquals(1001, ready(reader))
        val coldMs = (System.nanoTime() - coldStart) / 1_000_000.0
        val timings = (1..10).map {
            val start = System.nanoTime()
            assertEquals(0, ready(reader))
            assertEquals(40, reader.page("alice", GameListFilter()).games.size)
            assertEquals(1000, reader.summary("alice", GameListFilter()).games)
            reader.facets("alice")
            (System.nanoTime() - start) / 1_000_000.0
        }.sorted()
        println("game-list benchmark: 1000 games x 100 plies, oldFullReplayMs=$oldMs, coldIndexMs=$coldMs, warmMedianMs=${timings[5]}, warmMaxMs=${timings.last()}")
    }

    @Test fun migrationBackfillsWithoutChangingGameOrRepertoirePayloads() = database { db, repo, driver ->
        val id = add(repo, 1)
        val labels = SqlDelightRepertoireRepository(db)
        labels.addPositionLabel("alice", ShogiBoard().toSfen(), PositionLabelScope.BLACK, "先手の形")
        val before = repo.getGameById(id)
        val labelBefore = labels.entries("alice")
        dropGameListSchema(driver)
        ShogiSupplementDatabase.Schema.migrate(driver, 26, ShogiSupplementDatabase.Schema.version)
        val reopened = SqlDelightGameRepository(ShogiSupplementDatabase(driver))
        assertEquals(2, ready(reopened.gameListQueries))
        assertEquals(before, reopened.getGameById(id))
        assertEquals(labelBefore, labels.entries("alice"))
        assertEquals(setOf("先手：先手の形"), reopened.gameListQueries.page("alice", GameListFilter()).games.single().positionLabels)
    }
}

internal fun dropGameListSchema(driver: JdbcSqliteDriver) {
    driver.execute(null, "ALTER TABLE position_eval DROP COLUMN mate_verified", 0)
    driver.execute(null, "ALTER TABLE blunder_report DROP COLUMN mate_verified", 0)
    listOf("queue_game_list_index", "queue_position_label_index", "invalidate_game_list_index", "delete_game_list_index", "invalidate_position_label_index", "delete_position_label_index").forEach {
        driver.execute(null, "DROP TRIGGER IF EXISTS $it", 0)
    }
    listOf("game_position_key", "game_opening_key", "game_list_index", "position_label_index", "position_label_pending", "game_list_pending").forEach {
        driver.execute(null, "DROP TABLE $it", 0)
    }
    driver.execute(null, "DROP INDEX game_list_order", 0)
}
