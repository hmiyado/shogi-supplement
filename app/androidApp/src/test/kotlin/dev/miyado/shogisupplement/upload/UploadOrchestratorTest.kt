package dev.miyado.shogisupplement.upload

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.miyado.shogisupplement.auth.AuthUser
import dev.miyado.shogisupplement.auth.FakeAuthRepository
import dev.miyado.shogisupplement.classify.ClassificationResult
import dev.miyado.shogisupplement.db.DrillRepository
import dev.miyado.shogisupplement.db.GameRepository
import dev.miyado.shogisupplement.db.SettingsRepository
import dev.miyado.shogisupplement.db.ShogiSupplementDatabase
import dev.miyado.shogisupplement.db.SqlDelightDrillRepository
import dev.miyado.shogisupplement.db.SqlDelightGameRepository
import dev.miyado.shogisupplement.db.SqlDelightSettingsRepository
import dev.miyado.shogisupplement.judge.Judgement
import dev.miyado.shogisupplement.judge.VerdictKind
import dev.miyado.shogisupplement.pipeline.BlunderReport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UploadOrchestrator の単体テスト。
 * FakeAuthRepository / FakeUploadRepository / インメモリ DB を注入して検証する。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UploadOrchestratorTest {

    /** UploadOrchestrator と、同一DB上のリポジトリの組。 */
    private data class Built(
        val orchestrator: UploadOrchestrator,
        val upload: FakeUploadRepository,
        val game: GameRepository,
        val drill: DrillRepository,
        val settings: SettingsRepository,
    )

    private fun sampleReport() = BlunderReport(
        ply = 10,
        side = "sente",
        moveUsi = "7g7f",
        bestUsi = "2g2f",
        lossWp = 0.1,
        classification = ClassificationResult(
            category = "緩手",
            diffMaterial = 0,
            punishChecks = 0,
            tookMovedPiece = false,
            missedMateIn = null,
        ),
        judgement = Judgement(
            kind = VerdictKind.TARGET,
            verdict = "○ 出題対象",
            note = "テスト",
            problem = "テスト問題",
            priority = 1.0,
        ),
    )

    private fun saveGame(game: GameRepository, hash: String = "hash-test"): Long {
        return game.saveAnalysis(
            fileName = "test.kif",
            contentHash = hash,
            moves = listOf("7g7f", "3c3d"),
            headers = emptyMap(),
            reports = listOf(sampleReport()),
            rating = 1750,
            coefVersion = "hao_v1",
            analyzedAt = 1_780_000_000L,
            kifText = "KIF原文",
        )
    }

    private fun saveAttempt(drill: DrillRepository, game: GameRepository, gameId: Long, attemptedAt: Long): Long {
        val blunder = game.getReports(gameId).single()
        return drill.saveDrillAttempt(
            blunderReportId = blunder.id,
            userMoveUsi = "7g7f",
            isCorrect = true,
            lossWp = 0.0,
            attemptedAt = attemptedAt,
        )
    }

    private fun buildOrchestrator(
        auth: FakeAuthRepository = FakeAuthRepository(initialUser = AuthUser("uid1")),
        upload: FakeUploadRepository = FakeUploadRepository(),
    ): Built {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShogiSupplementDatabase.Schema.create(driver)
        val database = ShogiSupplementDatabase(driver)
        val game = SqlDelightGameRepository(database)
        val drill = SqlDelightDrillRepository(database)
        val settings = SqlDelightSettingsRepository(database)
        return Built(
            orchestrator = UploadOrchestrator(
                authRepository = auth,
                uploadRepository = upload,
                dbRepository = game,
                drillRepository = drill,
                settingsRepository = settings,
            ),
            upload = upload,
            game = game,
            drill = drill,
            settings = settings,
        )
    }

    // ─── uploadGame ──────────────────────────────────────────────────────────

    @Test
    fun uploadGame_success_recordsUploadedAt() = runTest {
        val (orch, _, db, _, _) = buildOrchestrator()
        val gameId = saveGame(db)
        assertNull(db.getGameById(gameId)?.uploadedAt)

        val result = orch.uploadGame(gameId)

        assertEquals(UploadResult.Success, result)
        assertNotNull("uploaded_at should be set after success", db.getGameById(gameId)?.uploadedAt)
    }

    @Test
    fun uploadGame_duplicate_recordsUploadedAt() = runTest {
        val upload = FakeUploadRepository(result = UploadResult.Duplicate)
        val (orch, _, db, _, _) = buildOrchestrator(upload = upload)
        val gameId = saveGame(db)

        val result = orch.uploadGame(gameId)

        assertEquals(UploadResult.Duplicate, result)
        assertNotNull("uploaded_at should be set on duplicate too", db.getGameById(gameId)?.uploadedAt)
    }

    @Test
    fun uploadGame_failure_doesNotRecordUploadedAt() = runTest {
        val upload = FakeUploadRepository(result = UploadResult.Failure("network error"))
        val (orch, _, db, _, _) = buildOrchestrator(upload = upload)
        val gameId = saveGame(db)

        val result = orch.uploadGame(gameId)

        assertTrue(result is UploadResult.Failure)
        assertNull("uploaded_at should NOT be set on failure", db.getGameById(gameId)?.uploadedAt)
    }

    @Test
    fun uploadGame_notLoggedIn_returnsNull() = runTest {
        val auth = FakeAuthRepository(initialUser = null)  // 未ログイン
        val (orch, upload, db, _, _) = buildOrchestrator(auth = auth)
        val gameId = saveGame(db)

        val result = orch.uploadGame(gameId)

        assertNull("Should return null when not logged in", result)
        assertTrue("upload should not be called", upload.calls.isEmpty())
    }

    @Test
    fun uploadGame_alreadyUploaded_returnsDuplicateWithoutCallingUpload() = runTest {
        val (orch, upload, db, _, _) = buildOrchestrator()
        val gameId = saveGame(db)
        db.updateUploadedAt(gameId, 1_780_000_000L)  // 既にアップロード済みとしてマーク

        val result = orch.uploadGame(gameId)

        assertEquals(UploadResult.Duplicate, result)
        assertTrue("upload should not be called again", upload.calls.isEmpty())
    }

    // ─── deleteUploadedGame ─────────────────────────────────────────────────

    @Test
    fun deleteUploadedGame_whenLoggedIn_returnsTrue() = runTest {
        val (orch, upload, _, _, _) = buildOrchestrator()

        val result = orch.deleteUploadedGame("hash-delete")

        assertTrue(result)
        assertEquals(listOf("uid1" to "hash-delete"), upload.deleteCalls)
    }

    @Test
    fun deleteUploadedGame_whenRepositoryFails_returnsFalse() = runTest {
        val upload = FakeUploadRepository(deleteResult = false)
        val (orch, uploadRepository, _, _, _) = buildOrchestrator(upload = upload)

        val result = orch.deleteUploadedGame("hash-delete")

        assertTrue(!result)
        assertEquals(listOf("uid1" to "hash-delete"), uploadRepository.deleteCalls)
    }

    @Test
    fun deleteUploadedGame_whenNotLoggedIn_returnsFalseWithoutCallingRepository() = runTest {
        val auth = FakeAuthRepository(initialUser = null)
        val (orch, upload, _, _, _) = buildOrchestrator(auth = auth)

        val result = orch.deleteUploadedGame("hash-delete")

        assertTrue(!result)
        assertTrue(upload.deleteCalls.isEmpty())
    }

    // ─── uploadAll ───────────────────────────────────────────────────────────

    @Test
    fun uploadAll_uploadsAllNotUploadedGames() = runTest {
        val (orch, upload, db, _, _) = buildOrchestrator()
        val id1 = saveGame(db, "hash1")
        val id2 = saveGame(db, "hash2")

        val result = orch.uploadAll()

        assertEquals(2, result.gameSuccess)
        assertEquals(0, result.gameFailed)
        assertEquals(0, result.drillFailed)
        assertEquals(0, result.drillPendingRemaining)
        assertEquals(2, upload.calls.size)
        // uploadGame自体が棋譜ごとに問題同期を済ませ、再同期ステップは今回アップロードした
        // 棋譜を除外するため、2棋譜×1回になる（二重upsertを避ける）
        assertEquals(2, upload.drillProblemCalls.size)
        // uploaded_at が記録されていること
        assertNotNull(db.getGameById(id1)?.uploadedAt)
        assertNotNull(db.getGameById(id2)?.uploadedAt)
    }

    @Test
    fun uploadAll_processesGamesThenProblemsThenAttempts_andPersistsAttemptSyncState() = runTest {
        val (orch, upload, db, drill, _) = buildOrchestrator()
        val newGameId = saveGame(db, "hash-new")
        val uploadedGameId = saveGame(db, "hash-uploaded")
        db.updateUploadedAt(uploadedGameId, 1_780_000_000L)
        saveAttempt(drill, db, newGameId, attemptedAt = 100L)
        saveAttempt(drill, db, uploadedGameId, attemptedAt = 200L)

        val result = orch.uploadAll()

        assertEquals(1, result.gameSuccess)
        assertEquals(0, result.gameFailed)
        assertEquals(0, result.drillFailed)
        assertEquals(0, result.drillPendingRemaining)
        assertEquals(1, upload.calls.size)
        // newGameIdはuploadGame自体の問題同期のみ（再同期ステップは今回アップロードした
        // 棋譜を除外）、uploadedGameIdは再同期ステップのみで計2回
        assertEquals(2, upload.drillProblemCalls.size)
        assertEquals(2, upload.drillAttemptCalls.size)

        val firstProblem = upload.events.indexOfFirst { it.startsWith("problem:") }
        val firstAttempt = upload.events.indexOfFirst { it.startsWith("attempt:") }
        assertTrue(firstProblem >= 0 && firstAttempt > firstProblem)
        assertTrue(upload.events.take(firstProblem).all { it.startsWith("game:") })
        assertTrue(upload.events.drop(firstAttempt).all { it.startsWith("attempt:") })

        db.getAllGames().forEach { game ->
            db.getReports(game.id).forEach { report ->
                drill.getDrillAttempts(report.id).forEach { attempt ->
                    assertNotNull(attempt.syncId)
                    assertNotNull(attempt.uploadedAt)
                }
            }
        }
    }

    @Test
    fun uploadAll_notLoggedIn_returnsZeroResult() = runTest {
        val auth = FakeAuthRepository(initialUser = null)
        val (orch, upload, db, _, _) = buildOrchestrator(auth = auth)
        saveGame(db)

        val result = orch.uploadAll()

        assertEquals(0, result.gameSuccess)
        assertEquals(0, result.gameFailed)
        assertEquals(0, result.drillFailed)
        assertEquals(0, result.drillPendingRemaining)
        assertTrue(upload.calls.isEmpty())
    }

    // ─── maybeAutoUpload ─────────────────────────────────────────────────────

    @Test
    fun maybeAutoUpload_whenEnabled_andLoggedIn_uploads() = runTest {
        val (orch, upload, db, _, settings) = buildOrchestrator()
        settings.saveAutoUpload(true)
        val gameId = saveGame(db)

        orch.maybeAutoUpload(gameId)

        assertEquals(1, upload.calls.size)
        assertNotNull(db.getGameById(gameId)?.uploadedAt)
    }

    @Test
    fun maybeAutoUpload_whenDisabled_skips() = runTest {
        val (orch, upload, db, _, settings) = buildOrchestrator()
        settings.saveAutoUpload(false)  // OFF（デフォルトもOFF）
        val gameId = saveGame(db)

        orch.maybeAutoUpload(gameId)

        assertTrue("upload should not be called when auto_upload=OFF", upload.calls.isEmpty())
    }

    @Test
    fun maybeAutoUpload_whenNotLoggedIn_skips() = runTest {
        val auth = FakeAuthRepository(initialUser = null)
        val (orch, upload, db, _, settings) = buildOrchestrator(auth = auth)
        settings.saveAutoUpload(true)
        val gameId = saveGame(db)

        orch.maybeAutoUpload(gameId)

        assertTrue("upload should not be called when not logged in", upload.calls.isEmpty())
    }

    @Test
    fun maybeAutoUpload_whenUploadFails_doesNotThrow() = runTest {
        val upload = FakeUploadRepository(result = UploadResult.Failure("error"))
        val (orch, _, db, _, settings) = buildOrchestrator(upload = upload)
        settings.saveAutoUpload(true)
        val gameId = saveGame(db)

        // 例外が出ないことを確認
        orch.maybeAutoUpload(gameId)
        // uploaded_at は記録されない
        assertNull(db.getGameById(gameId)?.uploadedAt)
    }

    @Test
    fun maybeAutoUploadDrillAttempts_whenEnabled_uploadsAndPersistsSyncId() = runTest {
        val (orch, upload, db, drill, settings) = buildOrchestrator()
        settings.saveAutoUpload(true)
        val gameId = saveGame(db)
        db.updateUploadedAt(gameId, 1_780_000_000L)
        val blunderId = db.getReports(gameId).single().id
        val attemptId = saveAttempt(drill, db, gameId, attemptedAt = 100L)

        orch.maybeAutoUploadDrillAttempts()

        val attempt = drill.getDrillAttempts(blunderId).single()
        assertEquals(attemptId, attempt.id)
        assertNotNull("sync_id should be persisted before upload", attempt.syncId)
        assertNotNull("uploaded_at should be set after success", attempt.uploadedAt)
        assertEquals(1, upload.drillAttemptCalls.size)
        assertEquals(attempt.syncId, upload.drillAttemptCalls.single().attempt.syncId)
    }

    @Test
    fun maybeAutoUploadDrillAttempts_duplicate_marksAttemptUploaded() = runTest {
        val upload = FakeUploadRepository(drillAttemptResult = UploadResult.Duplicate)
        val (orch, _, db, drill, settings) = buildOrchestrator(upload = upload)
        settings.saveAutoUpload(true)
        val gameId = saveGame(db)
        db.updateUploadedAt(gameId, 1_780_000_000L)
        val blunderId = db.getReports(gameId).single().id
        saveAttempt(drill, db, gameId, attemptedAt = 100L)

        orch.maybeAutoUploadDrillAttempts()

        assertNotNull(drill.getDrillAttempts(blunderId).single().uploadedAt)
    }

    @Test
    fun maybeAutoUploadDrillAttempts_gameNotUploaded_excludesAttempt() = runTest {
        // 棋譜自体が未アップロードだと解答送信は必ず外部キー違反で失敗するため、
        // 送信可能な解答を止め続けないよう対象から外れることを確認する（head-of-line blocking対策）。
        val (orch, upload, db, drill, settings) = buildOrchestrator()
        settings.saveAutoUpload(true)
        val gameId = saveGame(db)
        saveAttempt(drill, db, gameId, attemptedAt = 100L)

        orch.maybeAutoUploadDrillAttempts()

        assertTrue("未アップロード棋譜の解答は自動送信の対象にならない", upload.drillAttemptCalls.isEmpty())
    }

    @Test
    fun maybeAutoUploadDrillAttempts_whenUploadFails_doesNotMarkUploaded() = runTest {
        val upload = FakeUploadRepository(drillAttemptResult = UploadResult.Failure("network error"))
        val (orch, _, db, drill, settings) = buildOrchestrator(upload = upload)
        settings.saveAutoUpload(true)
        val gameId = saveGame(db)
        db.updateUploadedAt(gameId, 1_780_000_000L)
        val blunderId = db.getReports(gameId).single().id
        saveAttempt(drill, db, gameId, attemptedAt = 100L)

        orch.maybeAutoUploadDrillAttempts()

        assertNull(drill.getDrillAttempts(blunderId).single().uploadedAt)
    }

    // ─── 失敗の集計（drillFailed） ───────────────────────────────────────────

    @Test
    fun uploadAll_drillProblemSyncFails_countsDrillFailed() = runTest {
        val upload = FakeUploadRepository(drillProblemsResult = UploadResult.Failure("sync error"))
        val (orch, _, db, _, _) = buildOrchestrator(upload = upload)
        val gameId = saveGame(db)
        db.updateUploadedAt(gameId, 1_780_000_000L)  // 再同期ループの対象にする

        val result = orch.uploadAll()

        assertEquals(1, result.drillFailed)
    }

    @Test
    fun uploadAll_drillAttemptUploadFails_countsDrillFailedAndKeepsPending() = runTest {
        val upload = FakeUploadRepository(drillAttemptResult = UploadResult.Failure("attempt error"))
        val (orch, _, db, drill, _) = buildOrchestrator(upload = upload)
        val gameId = saveGame(db)
        db.updateUploadedAt(gameId, 1_780_000_000L)
        val blunderId = db.getReports(gameId).single().id
        saveAttempt(drill, db, gameId, attemptedAt = 100L)

        val result = orch.uploadAll()

        assertEquals(1, result.drillFailed)
        assertEquals(1, result.drillPendingRemaining)
        assertNull(drill.getDrillAttempts(blunderId).single().uploadedAt)
    }
}
