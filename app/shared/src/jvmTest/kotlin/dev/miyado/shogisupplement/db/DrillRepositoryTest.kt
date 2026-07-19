package dev.miyado.shogisupplement.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.miyado.shogisupplement.classify.ClassificationResult
import dev.miyado.shogisupplement.judge.Judgement
import dev.miyado.shogisupplement.judge.VerdictKind
import dev.miyado.shogisupplement.pipeline.BlunderReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * DrillRepository の単体テスト。
 * インメモリSQLiteで出題候補選定・解答履歴の保存復元を検証する。
 */
class DrillRepositoryTest {

    private fun newGameRepository(database: ShogiSupplementDatabase): GameRepository = GameRepository(database)
    private fun newDrillRepository(database: ShogiSupplementDatabase): DrillRepository = DrillRepository(database)

    private fun newDatabase(): ShogiSupplementDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShogiSupplementDatabase.Schema.create(driver)
        return ShogiSupplementDatabase(driver)
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

    /** ◎1件・○1件・△1件を保存し、blunder_report の id リストを返すヘルパ。 */
    private fun saveDrillFixture(gameRepo: GameRepository): List<BlunderRecord> {
        val reports = listOf(
            sampleReport().copy(
                ply = 1,
                judgement = sampleReport().judgement.copy(
                    kind = VerdictKind.PRIORITY, verdict = "◎ 優先出題", priority = 5.0,
                ),
            ),
            sampleReport().copy(ply = 2), // ○ 出題対象 (priority≈3.0)
            sampleReport().copy(
                ply = 3,
                judgement = sampleReport().judgement.copy(
                    kind = VerdictKind.SKIP, verdict = "△ 見送り", priority = 0.5,
                ),
            ),
        )
        val gameId = gameRepo.saveAnalysis(
            fileName = "g.kif",
            contentHash = "h",
            moves = listOf("7g7f", "3c3d", "2g2f"),
            headers = emptyMap(),
            reports = reports,
            rating = 1750,
            coefVersion = "hao_v1",
        )
        return gameRepo.getReports(gameId)
    }

    @Test
    fun `ドリル出題候補は◎と○のみで◎が先頭になる`() {
        val db = newDatabase()
        val gameRepo = newGameRepository(db)
        val drillRepo = newDrillRepository(db)
        saveDrillFixture(gameRepo)

        val candidates = drillRepo.getDrillCandidates()
        assertEquals(2, candidates.size, "△見送りは出題しない")
        assertEquals("◎ 優先出題", candidates[0].verdict)
        assertEquals("○ 出題対象", candidates[1].verdict)
    }

    @Test
    fun `ドリル解答が保存復元できる`() {
        val db = newDatabase()
        val gameRepo = newGameRepository(db)
        val drillRepo = newDrillRepository(db)
        val target = saveDrillFixture(gameRepo).first()

        val id1 = drillRepo.saveDrillAttempt(
            blunderReportId = target.id,
            userMoveUsi = "2f6f",
            isCorrect = true,
            lossWp = 0.0,
            attemptedAt = 1_780_000_100L,
        )
        val id2 = drillRepo.saveDrillAttempt(
            blunderReportId = target.id,
            userMoveUsi = "B*3d",
            isCorrect = false,
            lossWp = null, // エンジン判定不能ケース
            attemptedAt = 1_780_000_200L,
        )
        assertTrue(id1 > 0 && id2 > id1)

        val attempts = drillRepo.getDrillAttempts(target.id)
        assertEquals(2, attempts.size)
        // 新しい順
        assertEquals("B*3d", attempts[0].userMoveUsi)
        assertEquals(false, attempts[0].isCorrect)
        assertNull(attempts[0].lossWp)
        assertEquals(1_780_000_200L, attempts[0].attemptedAt)
        assertEquals("2f6f", attempts[1].userMoveUsi)
        assertEquals(true, attempts[1].isCorrect)
        assertEquals(0.0, attempts[1].lossWp!!, 1e-12)

        // 他のblunder_reportの履歴は混ざらない
        assertTrue(drillRepo.getDrillAttempts(target.id + 999).isEmpty())
    }
}
