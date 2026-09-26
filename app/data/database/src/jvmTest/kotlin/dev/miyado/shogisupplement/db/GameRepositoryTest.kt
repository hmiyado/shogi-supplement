package dev.miyado.shogisupplement.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.miyado.shogisupplement.classify.ClassificationResult
import dev.miyado.shogisupplement.judge.Judgement
import dev.miyado.shogisupplement.judge.VerdictKind
import dev.miyado.shogisupplement.pipeline.BlunderReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * GameRepository の単体テスト。
 * インメモリSQLiteで保存・重複検出・復元を検証する。
 */
class GameRepositoryTest {
    @Test
    fun `19sqmは既存の検討編集を未送信として引き継ぐ`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShogiSupplementDatabase.Schema.create(driver)
        driver.execute(null, "DROP TABLE study_sync", 0)
        driver.execute(null, "DROP TABLE analysis_remote_base", 0)
        driver.execute(null, "DROP TABLE analysis_delete_target", 0)
        driver.execute(null, "DROP TABLE analysis_sync_generation", 0)
        driver.execute(null, "DROP TABLE analysis_sync_target", 0)
        val db = ShogiSupplementDatabase(driver)
        val repo = SqlDelightGameRepository(db)
        val original = "1 ７六歩(77)\n2 投了"
        val id = repo.savePendingGame("old.kif", "old-study", listOf("7g7f"), emptyMap(), kifText = original, userSide = "sente")
        val edited = "$original\n*移行前のメモ"
        db.shogiSupplementQueries.updateStudyKif(edited, id)
        repo.updateUploadedAt(id, 100)
        ShogiSupplementDatabase.Schema.migrate(driver, 19, ShogiSupplementDatabase.Schema.version)
        val snapshot = repo.getPendingStudyUploads().single()
        assertEquals(original, snapshot.expectedRemoteKif)
        assertEquals(edited, snapshot.studyKif)
        assertEquals(1L, snapshot.revision)
        assertEquals(100L, repo.getGameById(id)?.uploadedAt)
        driver.close()
    }

    @Test
    fun `検討送信中の追加編集は古い送信完了で消えず解析状態を変えない`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShogiSupplementDatabase.Schema.create(driver)
        val repo = SqlDelightGameRepository(ShogiSupplementDatabase(driver))
        val original = "1 ７六歩(77)\n2 投了"
        val id = repo.savePendingGame("test.kif", "queue-test", listOf("7g7f"), emptyMap(), kifText = original, userSide = "sente")
        val first = "$original\n*最初のメモ"
        assertTrue(repo.saveStudyKif(id, original, first))
        assertEquals(emptyList(), repo.getPendingStudyUploads())
        repo.updateUploadedAt(id, 123)
        val snapshot = repo.getPendingStudyUploads().single()
        assertEquals(original, snapshot.expectedRemoteKif)
        val second = "$original\n*追加のメモ"
        assertTrue(repo.saveStudyKif(id, first, second))
        repo.acknowledgeStudyUpload(id, snapshot.revision, first)
        val pending = repo.getPendingStudyUploads().single()
        assertEquals(second, pending.studyKif)
        assertEquals(first, pending.expectedRemoteKif)
        assertEquals(123L, repo.getGameById(id)?.uploadedAt)
        repo.acknowledgeStudyUpload(id, pending.revision, second)
        assertEquals(emptyList(), repo.getPendingStudyUploads())
        val third = "$original\n*さらに追加"
        assertTrue(repo.saveStudyKif(id, second, third))
        repo.acknowledgeStudyUpload(id, snapshot.revision, first)
        assertEquals(second, repo.getPendingStudyUploads().single().expectedRemoteKif)
        assertEquals(original, repo.getGameById(id)?.kifText)
        driver.close()
    }


    @Test
    fun `検討保存で本譜の終局時間を変更できない`() {
        val repo = newRepository()
        val original = "1 ７六歩(77)\n2 投了 (0:12/00:00:12)"
        val id = repo.saveAnalysisAtomically(GameRepository.AnalysisSaveRequest(
            fileName = "terminal.kif", contentHash = "terminal", moves = listOf("7g7f"),
            headers = emptyMap(), reports = emptyList(), rating = 1000, coefVersion = "v1", kifText = original,
        ))
        assertFailsWith<IllegalArgumentException> {
            repo.saveStudyKif(id, original, original.replace("0:12", "0:34"))
        }
        assertEquals(original, repo.getStudyKif(id))
        assertTrue(repo.saveStudyKif(id, original, "$original\n*終局後のメモ"))
    }

    @Test
    fun `検討KIFは別リポジトリから復元でき解析原文と同期状態を変更しない`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShogiSupplementDatabase.Schema.create(driver)
        val database = ShogiSupplementDatabase(driver)
        val repo = SqlDelightGameRepository(database)
        val original = "手合割：平手\n1 ７六歩(77)\n2 ３四歩(33)"
        val id = repo.saveAnalysisAtomically(GameRepository.AnalysisSaveRequest(
            fileName = "study.kif", contentHash = "study", moves = listOf("7g7f", "3c3d"),
            headers = mapOf("手合割" to "平手"), reports = emptyList(), rating = 1500,
            coefVersion = "v1", kifText = original,
        ))
        repo.updateUploadedAt(id, 123L)
        val before = repo.getGameById(id)
        val revision = repo.getAnalysisUploadSnapshot(id)?.revision
        val edited = "$original\n*本譜メモ\n変化：2手\n2 ８四歩(83)\n&分岐"
        assertEquals(original, repo.getStudyKif(id))
        assertTrue(repo.saveStudyKif(id, original, edited))
        val reopened = SqlDelightGameRepository(ShogiSupplementDatabase(driver))
        assertEquals(edited, reopened.getStudyKif(id))
        assertEquals(before?.copy(studyKif = edited), reopened.getGameById(id))
        assertEquals(edited, reopened.getAnalysisUploadSnapshot(id)?.game?.studyKif)
        assertEquals(original, reopened.getAnalysisUploadSnapshot(id)?.game?.kifText)
        assertEquals(revision, reopened.getAnalysisUploadSnapshot(id)?.revision)
        assertEquals(false, reopened.saveStudyKif(id, original, original))
        assertEquals(edited, reopened.getStudyKif(id))
        assertTrue(reopened.saveStudyKif(id, edited, original))
        assertEquals(original, reopened.getStudyKif(id))
        assertFailsWith<IllegalArgumentException> {
            reopened.saveStudyKif(id, original, "手合割：平手\n1 ２六歩(27)")
        }
        assertEquals(original, reopened.getStudyKif(id))
        reopened.deleteGame(id)
        assertNull(reopened.getStudyKif(id))
        assertEquals(false, reopened.saveStudyKif(id, original, edited))
    }

    @Test
    fun `送信比較世代は利用者別に固定され再起動や再送で変わらない`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShogiSupplementDatabase.Schema.create(driver)
        val database = ShogiSupplementDatabase(driver)
        val repo = SqlDelightGameRepository(database)
        val request = GameRepository.AnalysisSaveRequest(
            fileName = "fenced.kif", contentHash = "fenced", moves = listOf("7g7f"),
            headers = emptyMap(), reports = emptyList(), rating = 1000, coefVersion = "v1",
        )
        val id = repo.saveAnalysisAtomically(request)
        val generation = assertNotNull(repo.getAnalysisUploadSnapshot(id)?.generation)
        assertNull(repo.getAnalysisSyncTarget(id, "owner-a", generation))
        assertEquals(GameRepository.AnalysisSyncTarget(null), repo.freezeAnalysisSyncTarget(id, "owner-a", generation, null))
        assertEquals(GameRepository.AnalysisSyncTarget("remote-b"), repo.freezeAnalysisSyncTarget(id, "owner-b", generation, "remote-b"))
        val reopened = SqlDelightGameRepository(ShogiSupplementDatabase(driver))
        assertEquals(GameRepository.AnalysisSyncTarget(null), reopened.freezeAnalysisSyncTarget(id, "owner-a", generation, "new-remote"))
        assertEquals(GameRepository.AnalysisSyncTarget("remote-b"), reopened.getAnalysisSyncTarget(id, "owner-b", generation))
        repo.saveAnalysisAtomically(request.copy(coefVersion = "v2"))
        val next = assertNotNull(repo.getAnalysisUploadSnapshot(id)?.generation)
        assertNull(repo.freezeAnalysisSyncTarget(id, "owner-a", generation, "late"))
        assertEquals(GameRepository.AnalysisSyncTarget("current-remote"), repo.freezeAnalysisSyncTarget(id, "owner-a", next, "current-remote"))
        val deletion = GameRepository.AnalysisDeleteTarget("remote-delete", "request-delete")
        assertEquals(deletion, repo.freezeAnalysisDeleteTarget(id, "owner-a", next, deletion))
        assertEquals(deletion, reopened.freezeAnalysisDeleteTarget(id, "owner-a", next,
            GameRepository.AnalysisDeleteTarget("later-remote", "later-request")))
        assertNull(repo.getAnalysisDeleteTarget(id, "owner-b", next))
        assertNull(repo.freezeAnalysisDeleteTarget(id, "owner-a", generation, deletion))
        repo.deleteGame(id)
        assertNull(reopened.getAnalysisDeleteTarget(id, "owner-a", next))
        assertNull(repo.getAnalysisSyncTarget(id, "owner-a", next))
        assertNull(repo.getAnalysisSyncTarget(id, "owner-b", generation))
        driver.close()
    }

    private fun newRepository(): GameRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShogiSupplementDatabase.Schema.create(driver)
        return SqlDelightGameRepository(ShogiSupplementDatabase(driver))
    }

    @Test
    fun `復元した世代はローカル解析と別に保持し再解析の比較元となる`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShogiSupplementDatabase.Schema.create(driver)
        val repo = SqlDelightGameRepository(ShogiSupplementDatabase(driver))
        val original = "1 ７六歩(77)\n2 投了"
        val id = repo.savePendingGame("restore.kif", "restore-base", listOf("7g7f"), emptyMap(), kifText = original, userSide = "sente")
        repo.confirmRestoredGame(id, "owner", "remote-before", 123)
        val reopened = SqlDelightGameRepository(ShogiSupplementDatabase(driver))
        assertEquals(GameRepository.AnalysisRemoteBase("remote-before"), reopened.getAnalysisRemoteBase(id, "owner"))
        assertNull(reopened.getAnalysisRemoteBase(id, "other"))
        assertTrue(reopened.getAnalysisUploadSnapshot(id)?.generation != "remote-before")
        val request = GameRepository.AnalysisSaveRequest("restore.kif", "restore-base", listOf("7g7f"), emptyMap(), emptyList(), 1000, coefVersion = "local", kifText = original)
        assertEquals(id, repo.saveAnalysisAtomically(request))
        val snapshot = assertNotNull(repo.getAnalysisUploadSnapshot(id))
        repo.confirmRestoredGame(id, "owner", "must-not-replace", 456)
        assertEquals(GameRepository.AnalysisRemoteBase("remote-before"), repo.getAnalysisRemoteBase(id, "owner"))
        assertNull(repo.getGameById(id)?.uploadedAt)
        repo.acknowledgeAnalysisGeneration(id, "owner", assertNotNull(snapshot.generation), snapshot.revision, 789)
        assertEquals(GameRepository.AnalysisRemoteBase(snapshot.generation), repo.getAnalysisRemoteBase(id, "owner"))
        repo.saveAnalysisAtomically(request.copy(coefVersion = "next"))
        repo.acknowledgeAnalysisGeneration(id, "owner", assertNotNull(snapshot.generation), snapshot.revision, 999)
        assertNull(repo.getGameById(id)?.uploadedAt)
        repo.deleteGame(id)
        assertNull(repo.getAnalysisRemoteBase(id, "owner"))
        driver.close()
    }

    @Test
    fun `移行済み棋譜の解析世代は再読込で維持され削除で消える`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShogiSupplementDatabase.Schema.create(driver)
        val database = ShogiSupplementDatabase(driver)
        val repo = SqlDelightGameRepository(database)
        val id = repo.savePendingGame("legacy.kif", "legacy-generation", listOf("7g7f"), emptyMap(), kifText = "1 ７六歩(77)", userSide = null)
        driver.execute(null, "DROP TABLE analysis_sync_generation", 0)
        driver.execute(null, "DROP TABLE analysis_sync_target", 0)
        driver.execute(null, "DROP TABLE analysis_delete_target", 0)
        driver.execute(null, "DROP TABLE analysis_remote_base", 0)
        ShogiSupplementDatabase.Schema.migrate(driver, 20, ShogiSupplementDatabase.Schema.version)
        assertNull(database.analysisSyncGenerationQueries.getGeneration(id).executeAsOneOrNull())
        val generation = assertNotNull(repo.getAnalysisUploadSnapshot(id)?.generation)
        assertTrue(generation.isNotBlank())
        val reopened = SqlDelightGameRepository(ShogiSupplementDatabase(driver))
        assertEquals(generation, reopened.getAnalysisUploadSnapshot(id)?.generation)
        assertEquals(generation, repo.getAnalysisUploadSnapshot(id)?.generation)
        reopened.deleteGame(id)
        assertNull(database.analysisSyncGenerationQueries.getGeneration(id).executeAsOneOrNull())
        assertNull(reopened.getAnalysisUploadSnapshot(id))
        val newId = reopened.savePendingGame("legacy.kif", "legacy-generation", listOf("7g7f"), emptyMap(), kifText = "1 ７六歩(77)", userSide = null)
        assertTrue(generation != assertNotNull(reopened.getAnalysisUploadSnapshot(newId)?.generation))
        reopened.deleteAllLocalData()
        assertNull(database.analysisSyncGenerationQueries.getGeneration(newId).executeAsOneOrNull())
        driver.close()
    }

    @Test
    fun `適用済み要求の再送は新しい解析結果を置換しない`() {
        val repo = newRepository()
        val first = GameRepository.AnalysisSaveRequest(
            fileName = "retry.kif", contentHash = "retry-hash", moves = listOf("7g7f"),
            headers = emptyMap(), reports = emptyList(), rating = 1000,
            coefVersion = "first", requestId = "request-1",
        )
        val gameId = repo.saveAnalysisAtomically(first)
        val firstGeneration = assertNotNull(repo.getAnalysisUploadSnapshot(gameId)?.generation)
        repo.saveAnalysisAtomically(first.copy(rating = 2000, coefVersion = "second", requestId = "request-2"))
        val secondGeneration = assertNotNull(repo.getAnalysisUploadSnapshot(gameId)?.generation)
        assertTrue(firstGeneration != secondGeneration)
        repo.updateUploadedAt(gameId, 123L)
        assertEquals(gameId, repo.saveAnalysisAtomically(first))
        assertEquals(secondGeneration, repo.getAnalysisUploadSnapshot(gameId)?.generation)
        assertEquals("second", repo.getGameById(gameId)?.coefVersion)
        assertEquals(123L, repo.getGameById(gameId)?.uploadedAt)
        assertEquals(gameId, repo.getAppliedAnalysis("retry-hash", "request-1"))
        assertNull(repo.getAppliedAnalysis("another-hash", "request-1"))
    }

    @Test
    fun `棋譜削除後は同じ要求IDで再取込できる`() {
        val repo = newRepository()
        val request = GameRepository.AnalysisSaveRequest(
            fileName = "reimport.kif", contentHash = "reimport-hash", moves = listOf("7g7f"),
            headers = emptyMap(), reports = emptyList(), rating = 1000,
            coefVersion = "v1", requestId = "same-request",
        )
        val firstId = repo.saveAnalysisAtomically(request)
        repo.deleteGame(firstId)
        assertNull(repo.getAppliedAnalysis(request.contentHash, "same-request"))
        val secondId = repo.saveAnalysisAtomically(request)
        assertTrue(secondId != firstId)
        repo.deleteAllLocalData()
        assertNull(repo.getAppliedAnalysis(request.contentHash, "same-request"))
        assertNotNull(repo.getGameById(repo.saveAnalysisAtomically(request)))
    }

    @Test
    fun `要求記録の保存失敗は置換前の結果と解答履歴を復元する`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShogiSupplementDatabase.Schema.create(driver)
        val database = ShogiSupplementDatabase(driver)
        val repo = SqlDelightGameRepository(database)
        val drill = SqlDelightDrillRepository(database)
        val request = GameRepository.AnalysisSaveRequest(
            fileName = "rollback.kif", contentHash = "rollback-hash", moves = listOf("7g7f"),
            headers = emptyMap(), reports = listOf(sampleReport().copy(ply = 1)), rating = 1000,
            coefVersion = "old", requestId = "old-request",
            positionEvalRows = listOf(PositionEvalRow(ply = 0, scoreCp = 10, mateIn = null)),
        )
        val gameId = repo.saveAnalysisAtomically(request)
        val originalGeneration = assertNotNull(repo.getAnalysisUploadSnapshot(gameId)?.generation)
        val problem = repo.getReports(gameId).single()
        val attemptId = drill.saveDrillAttempt(problem.id, "7g7f", true, 0.0)
        repo.updateUploadedAt(gameId, 123L)
        driver.execute(null, """
            CREATE TRIGGER fail_request BEFORE INSERT ON applied_analysis_request
            WHEN NEW.request_id = 'new-request'
            BEGIN SELECT RAISE(ABORT, 'injected request failure'); END;
        """.trimIndent(), 0)
        assertFailsWith<Exception> {
            repo.saveAnalysisAtomically(request.copy(coefVersion = "new", requestId = "new-request"))
        }
        assertEquals("old", repo.getGameById(gameId)?.coefVersion)
        assertEquals(originalGeneration, repo.getAnalysisUploadSnapshot(gameId)?.generation)
        assertEquals(123L, repo.getGameById(gameId)?.uploadedAt)
        assertEquals(problem.id, repo.getReports(gameId).single().id)
        assertEquals(attemptId, drill.getDrillAttempts(problem.id).single().id)
        assertEquals(gameId, repo.getAppliedAnalysis(request.contentHash, "old-request"))
        assertNull(repo.getAppliedAnalysis(request.contentHash, "new-request"))
    }

    @Test
    fun `古い送信完了は再解析後の結果を送信済みにしない`() {
        val repo = newRepository()
        val request = GameRepository.AnalysisSaveRequest(
            fileName = "race.kif", contentHash = "race-hash", moves = listOf("7g7f"),
            headers = emptyMap(), reports = emptyList(), rating = 1000, coefVersion = "v1",
        )
        val id = repo.saveAnalysisAtomically(request)
        val oldRevision = assertNotNull(repo.getAnalysisRevision(id))
        repo.saveAnalysisAtomically(request.copy(coefVersion = "v2"))
        repo.markAnalysisUploaded(id, oldRevision, 123L)
        assertNull(repo.getGameById(id)?.uploadedAt)
        val newRevision = assertNotNull(repo.getAnalysisRevision(id))
        assertTrue(newRevision > oldRevision)
        repo.markAnalysisUploaded(id, newRevision, 456L)
        assertEquals(456L, repo.getGameById(id)?.uploadedAt)
    }

    private fun sampleReport() = BlunderReport(
        ply = 41,
        side = "sente",
        moveUsi = "B*3d",
        bestUsi = "2f6f",
        lossWp = 0.225,
        classification = ClassificationResult(
            category = "駒損（タクティクス）",
            diffMaterial = -11,
            punishChecks = 0,
            tookMovedPiece = false,
            missedMateIn = null,
        ),
        judgement = Judgement(
            kind = VerdictKind.TARGET,
            verdict = "○ 出題対象",
            note = "自帯6.3件/1000手 (上帯5.2件)。帯として典型的なミス",
            problem = "手筋 (両取り・素抜き) の問題",
            priority = 2.9978349024480666,
        ),
    )

    @Test
    fun `保存したゲームとレポートが復元できる`() {
        val repo = newRepository()
        // 2手目（後手）の悪手: ply=2、直前局面は先手1手後のSFEN
        val report = sampleReport().copy(ply = 2)
        val moves = listOf("7g7f", "3c3d") // 有効な2手

        val gameId = repo.saveAnalysis(
            fileName = "miyado_game1.kif",
            contentHash = "hash-abc",
            moves = moves,
            headers = mapOf("先手" to "匿名", "後手" to "匿名"),
            reports = listOf(report),
            rating = 1750,
            coefVersion = "hao_v1",
            analyzedAt = 1_780_000_000L,
            engineMetaJson = "{\"engine_rev\":\"rev\",\"nodes\":400000}",
        )
        assertTrue(gameId > 0)

        val games = repo.getAllGames()
        assertEquals(1, games.size)
        val game = games[0]
        assertEquals("miyado_game1.kif", game.fileName)
        assertEquals(2L, game.moveCount)
        assertEquals("匿名", game.senteName)
        assertEquals("匿名", game.goteName)
        assertEquals(1750L, game.rating)
        assertEquals("hao_v1", game.coefVersion)
        assertEquals("{\"engine_rev\":\"rev\",\"nodes\":400000}", game.engineMetaJson)
        assertEquals(1_780_000_000L, game.analyzedAt)

        val reports = repo.getReports(gameId)
        assertEquals(1, reports.size)
        val r = reports[0]
        assertEquals(2L, r.ply)
        assertEquals("sente", r.side)
        assertEquals("B*3d", r.moveUsi)
        assertEquals("2f6f", r.bestUsi)
        assertEquals(0.225, r.lossWp, 1e-9)
        assertEquals("駒損（タクティクス）", r.category)
        assertEquals(-11L, r.diffMaterial)
        assertEquals(0L, r.punishChecks)
        assertEquals(false, r.tookMovedPiece)
        assertNull(r.missedMateIn)
        assertEquals("○ 出題対象", r.verdict)
        assertEquals("自帯6.3件/1000手 (上帯5.2件)。帯として典型的なミス", r.note)
        assertEquals("手筋 (両取り・素抜き) の問題", r.problemType)
        assertEquals(2.9978349024480666, r.priority, 1e-9)
        // sfen_before はSFEN形式（ply=2 = 先手1手「7g7f」後の局面）
        assertEquals(
            "lnsgkgsnl/1r5b1/ppppppppp/9/9/2P6/PP1PPPPPP/1B5R1/LNSGKGSNL w - 2",
            r.sfenBefore,
        )
    }

    @Test
    fun `ホーム用の最近のゲーム取得は件数をDB側で制限し完了済みの棋力集計は別条件で取得する`() {
        val repo = newRepository()
        repeat(4) { index ->
            repo.saveAnalysis(
                fileName = "game-$index.kif",
                contentHash = "hash-$index",
                moves = listOf("7g7f"),
                headers = emptyMap(),
                reports = emptyList(),
                rating = 1750 + index,
                coefVersion = "hao_v1",
                analyzedAt = 1_780_000_000L + index,
                userSide = if (index == 3) "sente" else null,
            )
        }
        repo.savePendingGame(
            fileName = "pending.kif",
            contentHash = "pending-hash",
            moves = listOf("7g7f"),
            headers = emptyMap(),
            kifText = "手合割：平手",
            userSide = "sente",
            importedAt = 1_780_000_010L,
        )

        assertEquals(listOf("pending.kif", "game-3.kif"), repo.getRecentGames(2).map { it.fileName })
        assertEquals(listOf("game-3.kif"), repo.getGamesWithUserSide().map { it.fileName })
        assertTrue(repo.getRecentGames(0).isEmpty())
    }

    @Test
    fun `持ち時間ヘッダの原文を保存・復元できる`() {
        val repo = newRepository()
        val gameId = repo.saveAnalysis(
            fileName = "kiou_game1.kif",
            contentHash = "hash-time-control",
            moves = listOf("7g7f"),
            headers = emptyMap(),
            reports = emptyList(),
            rating = 1750,
            coefVersion = "hao_v1",
            timeControlRaw = "10分+30秒",
        )

        val game = repo.getGameById(gameId)!!
        assertEquals("10分+30秒", game.timeControlRaw)
        assertNull(game.timeControlByoyomiRaw)
    }

    @Test
    fun `持ち時間ヘッダを渡さない場合はnullのまま保存される`() {
        val repo = newRepository()
        val gameId = repo.saveAnalysis(
            fileName = "miyado_game2.kif",
            contentHash = "hash-no-time-control",
            moves = listOf("7g7f"),
            headers = emptyMap(),
            reports = emptyList(),
            rating = 1750,
            coefVersion = "hao_v1",
        )

        val game = repo.getGameById(gameId)!!
        assertNull(game.timeControlRaw)
        assertNull(game.timeControlByoyomiRaw)
    }

    @Test
    fun `申告日時がnullならゲームにもnullを保存する`() {
        val repo = newRepository()
        val gameId = repo.saveAnalysis(
            fileName = "undeclared.kif",
            contentHash = "hash-rating-declared-at-null",
            moves = listOf("7g7f"),
            headers = emptyMap(),
            reports = emptyList(),
            rating = 1750,
            coefVersion = "hao_v1",
        )

        assertNull(repo.getGameById(gameId)?.ratingDeclaredAt)
    }

    @Test
    fun `未解析棋譜として保存した持ち時間ヘッダは解析完了後も引き継がれる`() {
        val repo = newRepository()
        val gameId = repo.savePendingGame(
            fileName = "wars_game2.kif",
            contentHash = "hash-pending-time-control",
            moves = listOf("7g7f"),
            headers = emptyMap(),
            kifText = "手合割：平手",
            userSide = "sente",
            timeControlRaw = "0分",
            timeControlByoyomiRaw = "10秒",
        )

        val completedId = repo.saveAnalysis(
            fileName = "wars_game2.kif",
            contentHash = "hash-pending-time-control",
            moves = listOf("7g7f"),
            headers = emptyMap(),
            reports = emptyList(),
            rating = 1600,
            coefVersion = "hao_v1",
            timeControlRaw = "0分",
            timeControlByoyomiRaw = "10秒",
        )

        assertEquals(gameId, completedId)
        val game = repo.getGameById(completedId)!!
        assertEquals("0分", game.timeControlRaw)
        assertEquals("10秒", game.timeControlByoyomiRaw)
    }

    @Test
    fun `対局者名を更新できる`() {
        val repo = newRepository()
        val gameId = repo.saveAnalysis(
            fileName = "miyado_game1.kif",
            contentHash = "hash-players",
            moves = listOf("7g7f"),
            headers = mapOf("先手" to "太郎", "後手" to "花子"),
            reports = emptyList(),
            rating = 1750,
            coefVersion = "hao_v1",
        )

        repo.updateGamePlayers(gameId, "次郎", "桜子")

        val game = repo.getGameById(gameId)!!
        assertEquals("次郎", game.senteName)
        assertEquals("桜子", game.goteName)
    }

    @Test
    fun `対局者名を空欄で更新するとnullになる`() {
        val repo = newRepository()
        val gameId = repo.saveAnalysis(
            fileName = "miyado_game1.kif",
            contentHash = "hash-players-clear",
            moves = listOf("7g7f"),
            headers = mapOf("先手" to "太郎", "後手" to "花子"),
            reports = emptyList(),
            rating = 1750,
            coefVersion = "hao_v1",
        )

        repo.updateGamePlayers(gameId, null, null)

        val game = repo.getGameById(gameId)!!
        assertNull(game.senteName)
        assertNull(game.goteName)
    }

    @Test
    fun `同一ハッシュはgetByHashで既存IDが返る`() {
        val repo = newRepository()
        assertNull(repo.getByHash("hash-abc"))

        val gameId = repo.saveAnalysis(
            fileName = "miyado_game1.kif",
            contentHash = "hash-abc",
            moves = listOf("7g7f"),
            headers = emptyMap(),
            reports = emptyList(),
            rating = 1750,
            coefVersion = "hao_v1",
        )
        assertEquals(gameId, repo.getByHash("hash-abc"))
        assertNull(repo.getByHash("hash-other"))
    }

    @Test
    fun `未解析棋譜を保存すると解析結果の集計対象から外れる`() {
        val repo = newRepository()
        val gameId = repo.savePendingGame(
            fileName = "pending.kif",
            contentHash = "pending-hash",
            moves = listOf("7g7f"),
            headers = emptyMap(),
            kifText = "手合割：平手",
            userSide = "sente",
        )

        assertEquals(GameAnalysisStatus.PENDING, repo.getGameById(gameId)!!.analysisStatus)
        assertEquals(listOf(gameId), repo.getPendingGames().map { it.id })
        assertTrue(repo.getGamesWithUserSide().isEmpty())
        assertTrue(repo.getNotUploadedGames().isEmpty())
        repo.markRestoredPendingGameUploaded(gameId, 123L)
        assertEquals(123L, repo.getGameById(gameId)!!.uploadedAt)
    }

    @Test
    fun `未解析棋譜の解析結果は同じゲームIDへ保存される`() {
        val repo = newRepository()
        val gameId = repo.savePendingGame(
            fileName = "pending.kif",
            contentHash = "pending-hash",
            moves = listOf("7g7f"),
            headers = emptyMap(),
            kifText = "手合割：平手",
            userSide = "sente",
        )

        val restoredRevision = repo.getAnalysisRevision(gameId)!!
        repo.markRestoredPendingGameUploaded(gameId, 100L)
        assertEquals(100L, repo.getGameById(gameId)!!.uploadedAt)

        val completedId = repo.saveAnalysis(
            fileName = "pending.kif",
            contentHash = "pending-hash",
            moves = listOf("7g7f"),
            headers = emptyMap(),
            reports = emptyList(),
            rating = 1800,
            coefVersion = "hao_v1",
            userSide = "sente",
        )

        assertEquals(gameId, completedId)
        assertEquals(GameAnalysisStatus.COMPLETED, repo.getGameById(gameId)!!.analysisStatus)
        assertEquals(1800L, repo.getGameById(gameId)!!.rating)
        assertEquals(1, repo.getAllGames().size)
        assertNull(repo.getGameById(gameId)!!.uploadedAt)
        assertEquals(listOf(gameId), repo.getNotUploadedGames().map { it.id })
        assertTrue(repo.getAnalysisRevision(gameId)!! > restoredRevision)
        repo.markAnalysisUploaded(gameId, restoredRevision, 120L)
        assertNull(repo.getGameById(gameId)!!.uploadedAt)
        repo.markRestoredPendingGameUploaded(gameId, 123L)
        assertNull(repo.getGameById(gameId)!!.uploadedAt)
    }

    @Test
    fun `詰み見逃しのmissedMateInが保存復元できる`() {
        val repo = newRepository()
        val mateReport = sampleReport().copy(
            ply = 3,
            classification = ClassificationResult(
                category = "詰み見逃し",
                diffMaterial = 0,
                punishChecks = 0,
                tookMovedPiece = false,
                missedMateIn = 5,
            ),
        )
        val gameId = repo.saveAnalysis(
            fileName = "g.kif",
            contentHash = "h",
            moves = listOf("7g7f", "3c3d", "2g2f"), // 有効な3手
            headers = emptyMap(),
            reports = listOf(mateReport),
            rating = 1750,
            coefVersion = "hao_v1",
        )
        val r = repo.getReports(gameId).single()
        assertNotNull(r.missedMateIn)
        assertEquals(5L, r.missedMateIn)
        assertEquals("詰み見逃し", r.category)
        // sfen_before はSFEN形式（ply=3 = 2手後の局面）
        assertTrue(r.sfenBefore.contains("/"), "sfenBefore should be SFEN format: ${r.sfenBefore}")
    }

    // ─── C2: アップロード関連 ───────────────────────────────────────────────

    @Test
    fun `kif_textとmoves_usiが保存復元できる`() {
        val repo = newRepository()
        val moves = listOf("7g7f", "3c3d")
        val gameId = repo.saveAnalysis(
            fileName = "test.kif",
            contentHash = "hash-kif",
            moves = moves,
            headers = emptyMap(),
            reports = emptyList(),
            rating = 1750,
            coefVersion = "hao_v1",
            kifText = "手合割：平手\n▲7六歩 △3四歩",
        )
        val game = repo.getGameById(gameId)!!
        assertEquals("手合割：平手\n▲7六歩 △3四歩", game.kifText)
        assertEquals(moves, game.movesUsi)
        assertNull(game.uploadedAt)
    }

    @Test
    fun `kifTextなしでも保存できる`() {
        val repo = newRepository()
        val gameId = repo.saveAnalysis(
            fileName = "test.kif",
            contentHash = "hash-nokif",
            moves = listOf("7g7f"),
            headers = emptyMap(),
            reports = emptyList(),
            rating = 1750,
            coefVersion = "hao_v1",
        )
        val game = repo.getGameById(gameId)!!
        assertNull(game.kifText)
        assertEquals(listOf("7g7f"), game.movesUsi)
    }

    @Test
    fun `updateUploadedAtが保存復元できる`() {
        val repo = newRepository()
        val gameId = repo.saveAnalysis(
            fileName = "test.kif",
            contentHash = "hash-upload",
            moves = listOf("7g7f"),
            headers = emptyMap(),
            reports = emptyList(),
            rating = 1750,
            coefVersion = "hao_v1",
        )
        assertNull(repo.getGameById(gameId)!!.uploadedAt)

        val uploadTime = 1_780_000_999L
        repo.updateUploadedAt(gameId, uploadTime)
        assertEquals(uploadTime, repo.getGameById(gameId)!!.uploadedAt)
    }

    @Test
    fun `getNotUploadedGamesは未アップロードのみ返す`() {
        val repo = newRepository()
        val id1 = repo.saveAnalysis(
            fileName = "g1.kif", contentHash = "h1",
            moves = listOf("7g7f"), headers = emptyMap(),
            reports = emptyList(), rating = 1750, coefVersion = "hao_v1",
        )
        val id2 = repo.saveAnalysis(
            fileName = "g2.kif", contentHash = "h2",
            moves = listOf("7g7f"), headers = emptyMap(),
            reports = emptyList(), rating = 1750, coefVersion = "hao_v1",
        )
        repo.updateUploadedAt(id1, 1_780_000_000L)  // id1 はアップロード済み

        val notUploaded = repo.getNotUploadedGames()
        assertEquals(1, notUploaded.size)
        assertEquals(id2, notUploaded[0].id)
    }

    @Test
    fun `resetAllUploadedAtで全ゲームのuploaded_atがNULLに戻る`() {
        val repo = newRepository()
        val id1 = repo.saveAnalysis(
            fileName = "g1.kif", contentHash = "h1",
            moves = listOf("7g7f"), headers = emptyMap(),
            reports = emptyList(), rating = 1750, coefVersion = "hao_v1",
        )
        val id2 = repo.saveAnalysis(
            fileName = "g2.kif", contentHash = "h2",
            moves = listOf("7g7f"), headers = emptyMap(),
            reports = emptyList(), rating = 1750, coefVersion = "hao_v1",
        )
        repo.updateUploadedAt(id1, 1_780_000_000L)
        repo.updateUploadedAt(id2, 1_780_000_100L)
        assertEquals(0, repo.getNotUploadedGames().size)

        // アカウント削除後: サーバー側データが消えたので全リセット
        repo.resetAllUploadedAt()

        assertNull(repo.getGameById(id1)!!.uploadedAt)
        assertNull(repo.getGameById(id2)!!.uploadedAt)
        // 全ゲームが再アップロード対象に戻る
        assertEquals(2, repo.getNotUploadedGames().size)
    }

    @Test
    fun `resetAllUploadedAtはゲーム0件でもエラーにならない`() {
        val repo = newRepository()
        repo.resetAllUploadedAt()
        assertEquals(0, repo.getNotUploadedGames().size)
    }

    // ─── cp_before / cp_after（blunder_report）──────────────────────────────

    @Test
    fun `cp_beforeとcp_afterが保存復元できる`() {
        val repo = newRepository()
        val report = sampleReport().copy(
            cpBefore = 300,
            cpAfter = 450,
        )
        val gameId = repo.saveAnalysis(
            fileName = "test.kif",
            contentHash = "hash-cp",
            moves = listOf("7g7f"),
            headers = emptyMap(),
            reports = listOf(report),
            rating = 1750,
            coefVersion = "hao_v1",
        )
        val reports = repo.getReports(gameId)
        assertEquals(1, reports.size)
        assertEquals(300L, reports[0].cpBefore)
        assertEquals(450L, reports[0].cpAfter)
    }

    @Test
    fun `cp_beforeとcp_afterがnullのレコードも復元できる`() {
        val repo = newRepository()
        val report = sampleReport()  // cpBefore=null, cpAfter=null
        val gameId = repo.saveAnalysis(
            fileName = "test.kif",
            contentHash = "hash-cp-null",
            moves = listOf("7g7f"),
            headers = emptyMap(),
            reports = listOf(report),
            rating = 1750,
            coefVersion = "hao_v1",
        )
        val reports = repo.getReports(gameId)
        assertEquals(1, reports.size)
        assertNull(reports[0].cpBefore)
        assertNull(reports[0].cpAfter)
    }

    // ─── source_place（既存行の読み出し時正規化）─────────────────────────────────

    // 正規化前は「場所」ヘッダの生値をそのまま source_place に保存していたため、
    // 既存行にはその生値（ウォーズの固定文字列・lishogiの対局URL・任意の文字列）が残っている。
    // saveAnalysis の sourcePlace 引数は正規化済みの値を受け取る前提の
    // ため、以下のテストでは生値を直接渡して「正規化前に保存された既存行」を再現する。

    @Test
    fun `生の場所ヘッダ値が将棋ウォーズだった既存行は読み出し時にwarsへ正規化される`() {
        val repo = newRepository()
        val gameId = repo.saveAnalysis(
            fileName = "legacy.kif",
            contentHash = "hash-legacy-wars",
            moves = listOf("7g7f"),
            headers = emptyMap(),
            reports = emptyList(),
            rating = 1750,
            coefVersion = "hao_v1",
            sourcePlace = "将棋ウォーズ", // 正規化前に保存されていた生値
        )
        assertEquals("wars", repo.getGameById(gameId)?.sourcePlace)
    }

    @Test
    fun `生のlishogi対局URLが保存されていた既存行は読み出し時にlishogiへ正規化される`() {
        val repo = newRepository()
        val gameId = repo.saveAnalysis(
            fileName = "legacy.kif",
            contentHash = "hash-legacy-lishogi",
            moves = listOf("7g7f"),
            headers = emptyMap(),
            reports = emptyList(),
            rating = 1750,
            coefVersion = "hao_v1",
            sourcePlace = "https://lishogi.org/abcd1234", // 正規化前に保存されていた生値
        )
        assertEquals("lishogi", repo.getGameById(gameId)?.sourcePlace)
    }

    @Test
    fun `ウォーズともlishogiとも判定できない生値が保存されていた既存行はotherへ正規化される`() {
        val repo = newRepository()
        val gameId = repo.saveAnalysis(
            fileName = "legacy.kif",
            contentHash = "hash-legacy-other",
            moves = listOf("7g7f"),
            headers = emptyMap(),
            reports = emptyList(),
            rating = 1750,
            coefVersion = "hao_v1",
            sourcePlace = "某道場", // ウォーズ固定文字列でもlishogi URLでもない任意の生値
        )
        assertEquals("other", repo.getGameById(gameId)?.sourcePlace)
    }

    @Test
    fun `source_placeがnullの既存行はnullのまま（otherに寄せない）`() {
        val repo = newRepository()
        val gameId = repo.saveAnalysis(
            fileName = "legacy.kif",
            contentHash = "hash-legacy-null",
            moves = listOf("7g7f"),
            headers = emptyMap(),
            reports = emptyList(),
            rating = 1750,
            coefVersion = "hao_v1",
            sourcePlace = null,
        )
        assertNull(repo.getGameById(gameId)?.sourcePlace)
    }

    @Test
    fun `正規化済みのsource_placeは読み出し時にそのまま保たれる（冪等）`() {
        val repo = newRepository()
        val gameId = repo.saveAnalysis(
            fileName = "new.kif",
            contentHash = "hash-normalized",
            moves = listOf("7g7f"),
            headers = emptyMap(),
            reports = emptyList(),
            rating = 1750,
            coefVersion = "hao_v1",
            sourcePlace = "kiou", // 保存経路が既に正規化済みで渡した値
        )
        assertEquals("kiou", repo.getGameById(gameId)?.sourcePlace)
    }

    // ─── position_eval ───────────────────────────────────────────────────────

    @Test
    fun `position_evalが保存復元できる`() {
        val repo = newRepository()
        val gameId = repo.saveAnalysis(
            fileName = "test.kif",
            contentHash = "hash-peval",
            moves = listOf("7g7f"),
            headers = emptyMap(),
            reports = emptyList(),
            rating = 1750,
            coefVersion = "hao_v1",
        )
        val rows = listOf(
            PositionEvalRow(ply = 0, scoreCp = 120, mateIn = null),
            PositionEvalRow(ply = 1, scoreCp = -200, mateIn = null),
            PositionEvalRow(ply = 2, scoreCp = null, mateIn = 3),
        )
        repo.savePositionEvals(gameId, rows)

        val restored = repo.getPositionEvals(gameId)
        assertEquals(3, restored.size)
        assertEquals(0, restored[0].ply)
        assertEquals(120, restored[0].scoreCp)
        assertNull(restored[0].mateIn)
        assertEquals(1, restored[1].ply)
        assertEquals(-200, restored[1].scoreCp)
        assertNull(restored[1].mateIn)
        assertEquals(2, restored[2].ply)
        assertNull(restored[2].scoreCp)
        assertEquals(3, restored[2].mateIn)
    }

    @Test
    fun `解析本体とposition_evalはposition_eval失敗時にまとめてロールバックされる`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShogiSupplementDatabase.Schema.create(driver)
        driver.execute(
            null,
            """
            CREATE TRIGGER fail_position_eval
            BEFORE INSERT ON position_eval
            WHEN NEW.best_usi = 'RAISE'
            BEGIN
                SELECT RAISE(ABORT, 'injected position_eval failure');
            END;
            """.trimIndent(),
            0,
        )
        val repo = SqlDelightGameRepository(ShogiSupplementDatabase(driver))

        assertFailsWith<Exception> {
            repo.saveAnalysisAtomically(
                GameRepository.AnalysisSaveRequest(
                    fileName = "atomic.kif",
                    contentHash = "hash-atomic-failure",
                    requestId = "failed-request",
                    moves = listOf("7g7f"),
                    headers = emptyMap(),
                    reports = emptyList(),
                    rating = 1750,
                    coefVersion = "hao_v1",
                    positionEvalRows = listOf(
                        PositionEvalRow(ply = 0, scoreCp = null, mateIn = null, bestUsi = "RAISE"),
                    ),
                ),
            )
        }

        assertNull(repo.getByHash("hash-atomic-failure"))
        assertNull(repo.getAppliedAnalysis("hash-atomic-failure", "failed-request"))
        assertTrue(repo.getAllGames().isEmpty())
    }

    @Test
    fun `position_evalの再保存（OR REPLACE）は冪等`() {
        val repo = newRepository()
        val gameId = repo.saveAnalysis(
            fileName = "test.kif",
            contentHash = "hash-peval2",
            moves = listOf("7g7f"),
            headers = emptyMap(),
            reports = emptyList(),
            rating = 1750,
            coefVersion = "hao_v1",
        )
        val rows = listOf(PositionEvalRow(ply = 0, scoreCp = 100, mateIn = null))
        repo.savePositionEvals(gameId, rows)
        // 同じ ply を上書き
        val newRows = listOf(PositionEvalRow(ply = 0, scoreCp = 200, mateIn = null))
        repo.savePositionEvals(gameId, newRows)

        val restored = repo.getPositionEvals(gameId)
        assertEquals(1, restored.size)
        assertEquals(200, restored[0].scoreCp)
    }

    @Test
    fun `position_evalは順序（ply昇順）で復元できる`() {
        val repo = newRepository()
        val gameId = repo.saveAnalysis(
            fileName = "test.kif",
            contentHash = "hash-peval3",
            moves = listOf("7g7f", "3c3d", "2g2f"),
            headers = emptyMap(),
            reports = emptyList(),
            rating = 1750,
            coefVersion = "hao_v1",
        )
        // ランダム順で保存
        val rows = listOf(
            PositionEvalRow(ply = 3, scoreCp = 300, mateIn = null),
            PositionEvalRow(ply = 1, scoreCp = -50, mateIn = null),
            PositionEvalRow(ply = 0, scoreCp = 20, mateIn = null),
        )
        repo.savePositionEvals(gameId, rows)

        val restored = repo.getPositionEvals(gameId)
        assertEquals(3, restored.size)
        assertEquals(0, restored[0].ply)
        assertEquals(1, restored[1].ply)
        assertEquals(3, restored[2].ply)
    }

    @Test
    fun `updateBestPvでbest_pvが更新される`() {
        val repo = newRepository()
        val gameId = repo.saveAnalysis(
            fileName = "test.kif",
            contentHash = "hash-pv",
            moves = listOf("7g7f"),
            headers = emptyMap(),
            reports = listOf(sampleReport()),
            rating = 1750,
            coefVersion = "hao_v1",
        )
        val blunder = repo.getReports(gameId).first()
        assertNull(blunder.bestPv)

        repo.updateBestPv(blunder.id, "7g7f 3c3d 2g2f")

        val updated = repo.getReports(gameId).first()
        assertEquals("7g7f 3c3d 2g2f", updated.bestPv)
    }

    @Test
    fun `deleteGameは関連する解析結果とドリル履歴も削除する`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShogiSupplementDatabase.Schema.create(driver)
        val database = ShogiSupplementDatabase(driver)
        val repo = SqlDelightGameRepository(database)
        val drillRepo = SqlDelightDrillRepository(database)
        val gameId = repo.saveAnalysis(
            fileName = "delete.kif",
            contentHash = "hash-delete",
            moves = listOf("7g7f", "3c3d"),
            headers = emptyMap(),
            reports = listOf(sampleReport().copy(ply = 2)),
            rating = 1750,
            coefVersion = "hao_v1",
        )
        repo.savePositionEvals(gameId, listOf(PositionEvalRow(ply = 0, scoreCp = 100, mateIn = null)))
        val report = repo.getReports(gameId).single()
        drillRepo.saveDrillAttempt(
            blunderReportId = report.id,
            userMoveUsi = "2f6f",
            isCorrect = true,
            lossWp = 0.0,
            attemptedAt = 1_780_000_000L,
        )

        repo.deleteGame(gameId)

        assertNull(repo.getGameById(gameId))
        assertTrue(repo.getReports(gameId).isEmpty())
        assertTrue(repo.getPositionEvals(gameId).isEmpty())
        assertTrue(drillRepo.getDrillAttempts(report.id).isEmpty())
    }

    @Test
    fun `deleteGameは指定したゲームだけを削除する`() {
        val repo = newRepository()
        val firstId = repo.saveAnalysis(
            fileName = "first.kif",
            contentHash = "hash-first",
            moves = listOf("7g7f", "3c3d"),
            headers = emptyMap(),
            reports = listOf(sampleReport().copy(ply = 2)),
            rating = 1750,
            coefVersion = "hao_v1",
        )
        val secondId = repo.saveAnalysis(
            fileName = "second.kif",
            contentHash = "hash-second",
            moves = listOf("7g7f", "3c3d"),
            headers = emptyMap(),
            reports = listOf(sampleReport().copy(ply = 2)),
            rating = 1750,
            coefVersion = "hao_v1",
        )

        repo.deleteGame(firstId)

        assertNull(repo.getGameById(firstId))
        assertTrue(repo.getReports(firstId).isEmpty())
        assertNotNull(repo.getGameById(secondId))
        assertEquals(1, repo.getReports(secondId).size)
    }
}
