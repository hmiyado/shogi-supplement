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
    @Test
    fun `復元後の再解析は取得済み世代を比較元にし最新世代を上書きしない`() = runTest {
        val built = buildOrchestrator()
        val original = "1 ７六歩(77)\n2 投了"
        val id = built.game.savePendingGame("restore.kif", "restored", listOf("7g7f"), emptyMap(), kifText = original, userSide = "sente")
        built.game.confirmRestoredGame(id, "uid1", "restored-generation", 100)
        built.game.saveAnalysisAtomically(GameRepository.AnalysisSaveRequest(
            "restore.kif", "restored", listOf("7g7f"), emptyMap(), emptyList(), 1000, coefVersion = "local", kifText = original))
        built.upload.remoteState = UploadRepository.AnalysisRemoteState("newer-remote", false)
        built.upload.analysisOutcome = UploadRepository.AnalysisUploadOutcome.Conflict("newer-remote")
        assertTrue(built.orchestrator.uploadGame(id) is UploadResult.Failure)
        assertEquals(0, built.upload.remoteStateCalls)
        assertEquals("restored-generation", built.upload.analysisTargets.single().expectedGeneration)
        assertNull(built.game.getGameById(id)?.uploadedAt)
    }

    @Test
    fun `解析状態取得中や送信中のアカウント切替では送信済みにしない`() = runTest {
        for (duringState in listOf(true, false)) {
            val auth = FakeAuthRepository(initialUser = AuthUser("owner-a"))
            val built = buildOrchestrator(auth = auth)
            val id = saveGame(built.game)
            val switch: suspend () -> Unit = { auth.importSession("owner-b") }
            if (duringState) built.upload.onRemoteState = switch else built.upload.onAnalysisResponse = switch
            assertTrue(built.orchestrator.uploadGame(id) is UploadResult.Failure)
            assertNull(built.game.getGameById(id)?.uploadedAt)
            assertEquals(if (duringState) 0 else 1, built.upload.analysisSnapshots.size)
        }
    }

    @Test
    fun `初回応答喪失後の再送は元のメモだけを確認済みにして追記を残す`() = runTest {
        val built = buildOrchestrator()
        val original = "1 ７六歩(77)\n2 投了"
        val id = built.game.savePendingGame("study.kif", "lost-reply", listOf("7g7f"), emptyMap(), kifText = original, userSide = "sente")
        val first = "$original\n*初回メモ"
        val second = "$original\n*初回メモ\n*追記"
        assertTrue(built.game.saveStudyKif(id, original, first))
        built.upload.analysisOutcome = UploadRepository.AnalysisUploadOutcome.Failure("reply lost")
        assertTrue(built.orchestrator.uploadGame(id) is UploadResult.Failure)
        assertTrue(built.game.saveStudyKif(id, first, second))
        built.upload.analysisOutcome = UploadRepository.AnalysisUploadOutcome.Applied(true)
        assertEquals(UploadResult.Success, built.orchestrator.uploadGame(id))
        assertEquals(listOf(first, first), built.upload.analysisSnapshots.map { it.game.studyKif })
        val pending = built.game.getPendingStudyUploads().single()
        assertEquals(first, pending.expectedRemoteKif)
        assertEquals(second, pending.studyKif)
        assertEquals(second, built.game.getStudyKif(id))
    }

    @Test
    fun `再送は固定した比較世代を使い競合や旧世代を送信済みにしない`() = runTest {
        val (orch, upload, db, _, _) = buildOrchestrator()
        val id = saveGame(db)
        upload.remoteState = UploadRepository.AnalysisRemoteState("base", false)
        upload.analysisOutcome = UploadRepository.AnalysisUploadOutcome.Failure("network")
        assertTrue(orch.uploadGame(id) is UploadResult.Failure)
        upload.remoteState = UploadRepository.AnalysisRemoteState("deleted-new", true)
        upload.analysisOutcome = UploadRepository.AnalysisUploadOutcome.Conflict("deleted-new")
        assertTrue(orch.uploadGame(id) is UploadResult.Failure)
        upload.analysisOutcome = UploadRepository.AnalysisUploadOutcome.Superseded("newer")
        assertTrue(orch.uploadGame(id) is UploadResult.Failure)
        assertEquals(1, upload.remoteStateCalls)
        assertTrue(upload.analysisTargets.all { it.expectedGeneration == "base" })
        assertNull(db.getGameById(id)?.uploadedAt)
        assertTrue(upload.drillProblemCalls.isEmpty())
    }

    @Test
    fun `削除済み送信先は自動で再作成せず世代も固定しない`() = runTest {
        val (orch, upload, db, _, _) = buildOrchestrator()
        val id = saveGame(db)
        upload.remoteState = UploadRepository.AnalysisRemoteState("deleted", true)
        assertTrue(orch.uploadGame(id) is UploadResult.Failure)
        assertTrue(upload.analysisSnapshots.isEmpty())
        assertNull(db.getGameById(id)?.uploadedAt)
        val generation = requireNotNull(db.getAnalysisUploadSnapshot(id)?.generation)
        assertNull(db.getAnalysisSyncTarget(id, "user-1", generation))
    }

    @Test
    fun `棋譜初回送信も送った検討版だけを確認済みにする`() = runTest {
        val built = buildOrchestrator()
        val original = "1 ７六歩(77)\n2 投了"
        val id = built.game.savePendingGame("study.kif", "study", listOf("7g7f"), emptyMap(), kifText = original, userSide = "sente")
        val first = "$original\n*初回に送る編集"
        val next = "$original\n*送信中の編集"
        assertTrue(built.game.saveStudyKif(id, original, first))
        built.upload.onGameUpload = { assertTrue(built.game.saveStudyKif(id, first, next)) }
        assertEquals(UploadResult.Success, built.orchestrator.uploadGame(id))
        assertEquals(first, built.upload.calls.single().second.studyKif)
        val pending = built.game.getPendingStudyUploads().single()
        assertEquals(first, pending.expectedRemoteKif)
        assertEquals(next, pending.studyKif)
    }

    @Test
    fun `検討送信は問題と回答に触れず追加編集と競合を未送信に残す`() = runTest {
        val built = buildOrchestrator()
        val original = "1 ７六歩(77)\n2 投了"
        val id = built.game.savePendingGame("study.kif", "study", listOf("7g7f"), emptyMap(), kifText = original, userSide = "sente")
        built.game.updateUploadedAt(id, 100)
        val first = "$original\n*先の編集"
        val next = "$original\n*送信中の編集"
        assertTrue(built.game.saveStudyKif(id, original, first))
        built.upload.onStudyUpload = { assertTrue(built.game.saveStudyKif(id, first, next)) }
        assertEquals(1 to 0, built.orchestrator.uploadPendingStudies())
        assertEquals(next, built.game.getPendingStudyUploads().single().studyKif)
        assertEquals(first, built.game.getPendingStudyUploads().single().expectedRemoteKif)
        built.upload.onStudyUpload = null
        built.upload.studyResult = UploadResult.Failure("conflict")
        assertEquals(0 to 1, built.orchestrator.uploadPendingStudies())
        assertEquals(1, built.game.getPendingStudyUploads().size)
        built.upload.studyResult = UploadResult.Success
        assertEquals(1 to 0, built.orchestrator.uploadPendingStudies())
        assertEquals(0, built.game.getPendingStudyUploads().size)
        assertTrue(built.upload.calls.isEmpty())
        assertTrue(built.upload.drillProblemCalls.isEmpty())
        assertTrue(built.upload.drillAttemptCalls.isEmpty())
        assertEquals(100L, built.game.getGameById(id)?.uploadedAt)
    }

    @Test
    fun `まとめて送信は検討の競合件数を結果へ含める`() = runTest {
        val built = buildOrchestrator()
        val original = "1 ７六歩(77)\n2 投了"
        val id = built.game.savePendingGame("study.kif", "study", listOf("7g7f"), emptyMap(), kifText = original, userSide = "sente")
        built.game.updateUploadedAt(id, 100)
        assertTrue(built.game.saveStudyKif(id, original, "$original\n*メモ"))
        built.upload.studyResult = UploadResult.Failure("conflict")
        val result = built.orchestrator.uploadAll()
        assertEquals(1, result.studyFailed)
        assertEquals(1, built.game.getPendingStudyUploads().size)
    }

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
    fun uploadGame_reanalysisDuringUpload_keepsSnapshotAndNewResultPending() = runTest {
        val (orch, upload, db, drill, _) = buildOrchestrator()
        val gameId = saveGame(db)
        val oldProblems = drill.getDrillCandidatesByGame(gameId)
        val oldReports = db.getReports(gameId)
        assertTrue(oldProblems.isNotEmpty())
        upload.onGameUpload = { saveGame(db) }

        orch.uploadGame(gameId)

        assertEquals(oldReports, upload.calls.single().third)
        assertEquals(oldProblems, upload.analysisSnapshots.single().problems)
        assertTrue(upload.drillProblemCalls.isEmpty())
        assertNull(db.getGameById(gameId)?.uploadedAt)
        upload.onGameUpload = null
        orch.uploadGame(gameId)
        assertNotNull(db.getGameById(gameId)?.uploadedAt)
    }

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
    fun uploadGame_alreadyAppliedGeneration_recordsUploadedAt() = runTest {
        val upload = FakeUploadRepository(result = UploadResult.Duplicate)
        val (orch, _, db, _, _) = buildOrchestrator(upload = upload)
        val gameId = saveGame(db)

        val result = orch.uploadGame(gameId)

        assertEquals(UploadResult.Success, result)
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
        val (orch, upload, db, _, _) = buildOrchestrator()
        saveGame(db, "hash-delete")

        val result = orch.deleteUploadedGame("hash-delete")

        assertTrue(result)
        assertEquals(listOf("uid1" to "hash-delete"), upload.deleteCalls)
    }

    @Test
    fun deleteUploadedGame_whenRepositoryFails_returnsFalse() = runTest {
        val upload = FakeUploadRepository(deleteResult = false)
        val (orch, uploadRepository, db, _, _) = buildOrchestrator(upload = upload)
        saveGame(db, "hash-delete")

        val result = orch.deleteUploadedGame("hash-delete")

        assertTrue(!result)
        assertEquals(listOf("uid1" to "hash-delete"), uploadRepository.deleteCalls)
        upload.remoteState = UploadRepository.AnalysisRemoteState("newer", false)
        orch.deleteUploadedGame("hash-delete")
        assertEquals(1, upload.remoteStateCalls)
        assertEquals(upload.deletionTargets.first(), upload.deletionTargets.last())
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
        assertEquals(0, result.drillProblemSyncFailed)
        assertEquals(0, result.drillPendingRemaining)
        assertEquals(2, upload.calls.size)
        // 問題も解析snapshotで送信するため、別の問題同期は呼ばない。
        assertEquals(2, upload.analysisSnapshots.size)
        assertTrue(upload.drillProblemCalls.isEmpty())
        // uploaded_at が記録されていること
        assertNotNull(db.getGameById(id1)?.uploadedAt)
        assertNotNull(db.getGameById(id2)?.uploadedAt)
    }

    @Test
    fun uploadAll_partialGameUploadFailsUntilProblemSyncSucceeds() = runTest {
        val upload = FakeUploadRepository(drillProblemsResult = UploadResult.Failure("sync error"))
        val (orch, _, db, _, _) = buildOrchestrator(upload = upload)
        val gameId = saveGame(db)
        val failed = orch.uploadAll()
        assertEquals(0, failed.gameSuccess)
        assertEquals(1, failed.gameFailed)
        assertNull(db.getGameById(gameId)?.uploadedAt)
        upload.setDrillProblemsResult(UploadResult.Success)
        val retried = orch.uploadAll()
        assertEquals(1, retried.gameSuccess)
        assertEquals(0, retried.gameFailed)
        assertNotNull(db.getGameById(gameId)?.uploadedAt)
        assertEquals(2, upload.calls.size)
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
        assertEquals(0, result.drillProblemSyncFailed)
        assertEquals(0, result.drillPendingRemaining)
        assertEquals(1, upload.calls.size)
        assertTrue(upload.drillProblemCalls.isEmpty())
        assertEquals(2, upload.drillAttemptCalls.size)
        assertEquals(2, upload.attemptGenerations.size)

        val firstAttempt = upload.events.indexOfFirst { it.startsWith("attempt:") }
        assertTrue(firstAttempt > 0)
        assertTrue(upload.events.take(firstAttempt).all { it.startsWith("analysis:") })
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
        assertEquals(0, result.drillProblemSyncFailed)
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

    // ─── 失敗の集計 ─────────────────────────────────────────────────────────

    @Test
    fun uploadAll_uploadedGame_doesNotResendProblems() = runTest {
        val upload = FakeUploadRepository(drillProblemsResult = UploadResult.Failure("sync error"))
        val (orch, _, db, _, _) = buildOrchestrator(upload = upload)
        val gameId = saveGame(db)
        db.updateUploadedAt(gameId, 1_780_000_000L)

        val result = orch.uploadAll()

        assertEquals(0, result.drillProblemSyncFailed)
        assertEquals(0, result.drillPendingRemaining)
        assertTrue(upload.drillProblemCalls.isEmpty())
    }

    @Test
    fun uploadAll_drillAttemptUploadFails_staysPendingWithoutDoubleCounting() = runTest {
        val upload = FakeUploadRepository(drillAttemptResult = UploadResult.Failure("attempt error"))
        val (orch, _, db, drill, _) = buildOrchestrator(upload = upload)
        val gameId = saveGame(db)
        db.updateUploadedAt(gameId, 1_780_000_000L)
        val blunderId = db.getReports(gameId).single().id
        saveAttempt(drill, db, gameId, attemptedAt = 100L)

        val result = orch.uploadAll()

        // 送信に失敗した解答は未送信のまま残る。問題同期の失敗数には数えない。
        assertEquals(0, result.drillProblemSyncFailed)
        assertEquals(1, result.drillPendingRemaining)
        assertNull(drill.getDrillAttempts(blunderId).single().uploadedAt)
        // 同じ1件を2件として見せない。
        assertEquals("アップロード完了: 成功0局／次の一手の成績は1件送信できませんでした", result.resultMessage())
    }

    @Test
    fun uploadAll_onlyGenerationAttemptFails_countsOnce() = runTest {
        val upload = FakeUploadRepository(
            drillProblemsResult = UploadResult.Failure("sync error"),
            drillAttemptResult = UploadResult.Failure("attempt error"),
        )
        val (orch, _, db, drill, _) = buildOrchestrator(upload = upload)
        val gameId = saveGame(db)
        db.updateUploadedAt(gameId, 1_780_000_000L)
        saveAttempt(drill, db, gameId, attemptedAt = 100L)

        val result = orch.uploadAll()

        assertEquals(0, result.drillProblemSyncFailed)
        assertEquals(1, result.drillPendingRemaining)
        assertTrue(upload.drillProblemCalls.isEmpty())
        assertEquals("アップロード完了: 成功0局／次の一手の成績は1件送信できませんでした", result.resultMessage())
    }
}
