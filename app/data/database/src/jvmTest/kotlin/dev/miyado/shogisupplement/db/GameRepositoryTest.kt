package dev.miyado.shogisupplement.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.miyado.shogisupplement.classify.ClassificationResult
import dev.miyado.shogisupplement.judge.Judgement
import dev.miyado.shogisupplement.judge.VerdictKind
import dev.miyado.shogisupplement.pipeline.BlunderReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * GameRepository の単体テスト。
 * インメモリSQLiteで保存・重複検出・復元を検証する。
 */
class GameRepositoryTest {

    private fun newRepository(): GameRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShogiSupplementDatabase.Schema.create(driver)
        return SqlDelightGameRepository(ShogiSupplementDatabase(driver))
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
    fun `持ち時間ルールを保存・復元できる`() {
        val repo = newRepository()
        val gameId = repo.saveAnalysis(
            fileName = "kiou_game1.kif",
            contentHash = "hash-time-control",
            moves = listOf("7g7f"),
            headers = emptyMap(),
            reports = emptyList(),
            rating = 1750,
            coefVersion = "hao_v1",
            timeControlKind = "fischer",
            timeControlBaseMinutes = 10,
            timeControlIncrementSeconds = 30,
        )

        val game = repo.getGameById(gameId)!!
        assertEquals("fischer", game.timeControlKind)
        assertEquals(10L, game.timeControlBaseMinutes)
        assertEquals(30L, game.timeControlIncrementSeconds)
    }

    @Test
    fun `持ち時間ルールを渡さない場合は3列ともnullのまま保存される`() {
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
        assertNull(game.timeControlKind)
        assertNull(game.timeControlBaseMinutes)
        assertNull(game.timeControlIncrementSeconds)
    }

    @Test
    fun `未解析棋譜として保存した持ち時間ルールは解析完了後も引き継がれる`() {
        val repo = newRepository()
        val gameId = repo.savePendingGame(
            fileName = "wars_game2.kif",
            contentHash = "hash-pending-time-control",
            moves = listOf("7g7f"),
            headers = emptyMap(),
            kifText = "手合割：平手",
            userSide = "sente",
            timeControlKind = "byoyomi",
            timeControlBaseMinutes = 0,
            timeControlIncrementSeconds = 10,
        )

        val completedId = repo.saveAnalysis(
            fileName = "wars_game2.kif",
            contentHash = "hash-pending-time-control",
            moves = listOf("7g7f"),
            headers = emptyMap(),
            reports = emptyList(),
            rating = 1600,
            coefVersion = "hao_v1",
            timeControlKind = "byoyomi",
            timeControlBaseMinutes = 0,
            timeControlIncrementSeconds = 10,
        )

        assertEquals(gameId, completedId)
        val game = repo.getGameById(completedId)!!
        assertEquals("byoyomi", game.timeControlKind)
        assertEquals(0L, game.timeControlBaseMinutes)
        assertEquals(10L, game.timeControlIncrementSeconds)
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
