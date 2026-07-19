package dev.miyado.shogisupplement.upload

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.miyado.shogisupplement.auth.AuthUser
import dev.miyado.shogisupplement.auth.FakeAuthRepository
import dev.miyado.shogisupplement.classify.ClassificationResult
import dev.miyado.shogisupplement.db.GameRepository
import dev.miyado.shogisupplement.db.SettingsRepository
import dev.miyado.shogisupplement.db.ShogiSupplementDatabase
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

    /** UploadOrchestrator と、検証に使う同一DB上の GameRepository/SettingsRepository の組。 */
    private data class Built(
        val orchestrator: UploadOrchestrator,
        val upload: FakeUploadRepository,
        val game: GameRepository,
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

    private fun buildOrchestrator(
        auth: FakeAuthRepository = FakeAuthRepository(initialUser = AuthUser("uid1")),
        upload: FakeUploadRepository = FakeUploadRepository(),
    ): Built {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShogiSupplementDatabase.Schema.create(driver)
        val database = ShogiSupplementDatabase(driver)
        val game = GameRepository(database)
        val settings = SettingsRepository(database)
        return Built(UploadOrchestrator(auth, upload, game, settings), upload, game, settings)
    }

    // ─── uploadGame ──────────────────────────────────────────────────────────

    @Test
    fun uploadGame_success_recordsUploadedAt() = runTest {
        val (orch, _, db, _) = buildOrchestrator()
        val gameId = saveGame(db)
        assertNull(db.getGameById(gameId)?.uploadedAt)

        val result = orch.uploadGame(gameId)

        assertEquals(UploadResult.Success, result)
        assertNotNull("uploaded_at should be set after success", db.getGameById(gameId)?.uploadedAt)
    }

    @Test
    fun uploadGame_duplicate_recordsUploadedAt() = runTest {
        val upload = FakeUploadRepository(result = UploadResult.Duplicate)
        val (orch, _, db, _) = buildOrchestrator(upload = upload)
        val gameId = saveGame(db)

        val result = orch.uploadGame(gameId)

        assertEquals(UploadResult.Duplicate, result)
        assertNotNull("uploaded_at should be set on duplicate too", db.getGameById(gameId)?.uploadedAt)
    }

    @Test
    fun uploadGame_failure_doesNotRecordUploadedAt() = runTest {
        val upload = FakeUploadRepository(result = UploadResult.Failure("network error"))
        val (orch, _, db, _) = buildOrchestrator(upload = upload)
        val gameId = saveGame(db)

        val result = orch.uploadGame(gameId)

        assertTrue(result is UploadResult.Failure)
        assertNull("uploaded_at should NOT be set on failure", db.getGameById(gameId)?.uploadedAt)
    }

    @Test
    fun uploadGame_notLoggedIn_returnsNull() = runTest {
        val auth = FakeAuthRepository(initialUser = null)  // 未ログイン
        val (orch, upload, db, _) = buildOrchestrator(auth = auth)
        val gameId = saveGame(db)

        val result = orch.uploadGame(gameId)

        assertNull("Should return null when not logged in", result)
        assertTrue("upload should not be called", upload.calls.isEmpty())
    }

    @Test
    fun uploadGame_alreadyUploaded_returnsDuplicateWithoutCallingUpload() = runTest {
        val (orch, upload, db, _) = buildOrchestrator()
        val gameId = saveGame(db)
        db.updateUploadedAt(gameId, 1_780_000_000L)  // 既にアップロード済みとしてマーク

        val result = orch.uploadGame(gameId)

        assertEquals(UploadResult.Duplicate, result)
        assertTrue("upload should not be called again", upload.calls.isEmpty())
    }

    // ─── uploadAll ───────────────────────────────────────────────────────────

    @Test
    fun uploadAll_uploadsAllNotUploadedGames() = runTest {
        val (orch, upload, db, _) = buildOrchestrator()
        val id1 = saveGame(db, "hash1")
        val id2 = saveGame(db, "hash2")

        val results = orch.uploadAll()

        assertEquals(2, results.size)
        assertEquals(UploadResult.Success, results[id1])
        assertEquals(UploadResult.Success, results[id2])
        assertEquals(2, upload.calls.size)
        // uploaded_at が記録されていること
        assertNotNull(db.getGameById(id1)?.uploadedAt)
        assertNotNull(db.getGameById(id2)?.uploadedAt)
    }

    @Test
    fun uploadAll_notLoggedIn_returnsEmpty() = runTest {
        val auth = FakeAuthRepository(initialUser = null)
        val (orch, upload, db, _) = buildOrchestrator(auth = auth)
        saveGame(db)

        val results = orch.uploadAll()

        assertTrue(results.isEmpty())
        assertTrue(upload.calls.isEmpty())
    }

    // ─── maybeAutoUpload ─────────────────────────────────────────────────────

    @Test
    fun maybeAutoUpload_whenEnabled_andLoggedIn_uploads() = runTest {
        val (orch, upload, db, settings) = buildOrchestrator()
        settings.saveAutoUpload(true)
        val gameId = saveGame(db)

        orch.maybeAutoUpload(gameId)

        assertEquals(1, upload.calls.size)
        assertNotNull(db.getGameById(gameId)?.uploadedAt)
    }

    @Test
    fun maybeAutoUpload_whenDisabled_skips() = runTest {
        val (orch, upload, db, settings) = buildOrchestrator()
        settings.saveAutoUpload(false)  // OFF（デフォルトもOFF）
        val gameId = saveGame(db)

        orch.maybeAutoUpload(gameId)

        assertTrue("upload should not be called when auto_upload=OFF", upload.calls.isEmpty())
    }

    @Test
    fun maybeAutoUpload_whenNotLoggedIn_skips() = runTest {
        val auth = FakeAuthRepository(initialUser = null)
        val (orch, upload, db, settings) = buildOrchestrator(auth = auth)
        settings.saveAutoUpload(true)
        val gameId = saveGame(db)

        orch.maybeAutoUpload(gameId)

        assertTrue("upload should not be called when not logged in", upload.calls.isEmpty())
    }

    @Test
    fun maybeAutoUpload_whenUploadFails_doesNotThrow() = runTest {
        val upload = FakeUploadRepository(result = UploadResult.Failure("error"))
        val (orch, _, db, settings) = buildOrchestrator(upload = upload)
        settings.saveAutoUpload(true)
        val gameId = saveGame(db)

        // 例外が出ないことを確認
        orch.maybeAutoUpload(gameId)
        // uploaded_at は記録されない
        assertNull(db.getGameById(gameId)?.uploadedAt)
    }
}
