package dev.miyado.shogisupplement.ui.report

import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.db.GameRepository
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.db.PositionEvalRow
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.engine.Engine
import dev.miyado.shogisupplement.engine.PvInfo
import dev.miyado.shogisupplement.pipeline.BlunderReport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import dev.miyado.shogisupplement.board.ShogiSquare

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModelStudyResetTest {
    @Test
    fun `本譜の評価は検討開始直後に表示され再解析せず新規分岐だけ解析する`() = runTest {
        val raw = "1 ７六歩(77)\n2 ３四歩(33)\n3 投了"
        val game = GameRecord(1, "test.kif", "hash", 2, null, null, 0, 1000, coefVersion = "v1", kifText = raw, movesUsi = listOf("7g7f", "3c3d"))
        val repo = object : GameRepository by FakeGameRepository(game) {
            override fun getPositionEvals(gameId: Long) = listOf(
                PositionEvalRow(0, 10, null), PositionEvalRow(1, 20, null), PositionEvalRow(2, 30, null),
            )
        }
        var created = 0
        val vm = ReportViewModel(this, repo, { created++; FakeEngine() }, { "cp" }, UnconfinedTestDispatcher(testScheduler))
        vm.loadReport(1)
        vm.startStudy(ShogiBoard().toSfen(), false, false, 0, null, 0, StudyOrigin("開始局面", null))
        assertEquals(listOf("+20", "+30"), vm.studyState.value!!.chipEvalStates.map { (it as StudyEvalState.Value).label.text })
        assertEquals("+10", (vm.studyState.value!!.evalState as StudyEvalState.Value).label.text)
        vm.onStudyAutoAnalyze()
        vm.onStudyChipTapped(1)
        advanceUntilIdle()
        assertEquals(0, created)
        assertEquals(false, vm.hasUnsavedStudy())
        vm.onStudySquareTapped(ShogiSquare(8, 3))
        vm.onStudySquareTapped(ShogiSquare(8, 4))
        advanceUntilIdle()
        assertEquals(1, created)
        assertFalse(vm.hasUnsavedStudy())
        vm.dispose()
    }

    @Test
    fun `検討エンジンの初期化もIOディスパッチャで実行する`() = runTest {
        val ioTasks = mutableListOf<Runnable>()
        var inIo = false
        var created = false
        val queuedIo = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) { ioTasks.add(block) }
        }
        val vm = ReportViewModel(this, FakeGameRepository(), {
            assertTrue(inIo)
            created = true
            FakeEngine()
        }, { "cp" }, queuedIo)
        vm.startStudy(ShogiBoard().toSfen(), false, false, 0, null, 0, StudyOrigin("開始局面", null))
        vm.studyController.analyzeCurrentPosition()
        runCurrent()
        assertEquals(false, created)
        assertEquals(StudyEvalState.Loading, vm.studyState.value?.evalState)
        assertTrue(ioTasks.isNotEmpty())
        while (ioTasks.isNotEmpty()) {
            inIo = true
            ioTasks.removeAt(0).run()
            inIo = false
            runCurrent()
        }
        assertTrue(created)
        assertTrue(vm.studyState.value?.evalState != StudyEvalState.Loading)
        vm.dispose()
    }

    @Test
    fun `古い棋譜の遅い読み込みは新しい検討文書を上書きしない`() = runTest {
        val firstKif = "1 ７六歩(77)\n2 投了"
        val secondKif = "1 ２六歩(27)\n2 投了"
        val firstGame = GameRecord(1, "first.kif", "first", 1, null, null, 0, 1000, coefVersion = "v1", kifText = firstKif, movesUsi = listOf("7g7f"))
        val secondGame = firstGame.copy(id = 2, kifText = secondKif, movesUsi = listOf("2g2f"))
        val repository = object : GameRepository by FakeGameRepository(firstGame) {
            override fun getAllGames() = listOf(firstGame, secondGame)
            override fun getStudyKif(gameId: Long) = if (gameId == 1L) firstKif else secondKif
        }
        val ioTasks = mutableListOf<Runnable>()
        val queuedIo = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) { ioTasks.add(block) }
        }
        val vm = ReportViewModel(this, repository, { FakeEngine() }, { "cp" }, queuedIo)
        val older = launch { vm.loadReport(1) }
        runCurrent()
        val newer = launch { vm.loadReport(2) }
        runCurrent()
        assertEquals(2, ioTasks.size)
        ioTasks.removeAt(1).run()
        runCurrent()
        assertTrue(newer.isCompleted)
        assertEquals(secondKif, vm.savedKifForExport(2))
        ioTasks.removeAt(0).run()
        runCurrent()
        assertTrue(older.isCancelled)
        assertNull(vm.savedKifForExport(1))
        assertEquals(secondKif, vm.savedKifForExport(2))
        val sfen = computeSfenAtStep(null, secondGame.movesUsi, 1)
        vm.startStudy(sfen, false, false, 1, null, 1, StudyOrigin("新しい棋譜", null))
        assertNotNull(vm.studyState.value)
        vm.dispose()
        assertNull(vm.savedKifForExport(2))
    }

    @Test
    fun `延長した読み筋の末尾から検討を開始して保存できる`() = runTest {
        val raw = "1 ７六歩(77)\n2 ３四歩(33)\n3 ２六歩(27)\n4 投了"
        val moves = listOf("7g7f", "3c3d", "2g2f")
        val game = GameRecord(1, "test.kif", "hash", 3, null, null, 0, 1000, coefVersion = "v1", kifText = raw, movesUsi = moves)
        val report = BlunderRecord(
            10, 1, 2, "gote", "3c3d", "8c8d", 0.1,
            computeSfenAtStep(null, moves, 1), "", 0, 0, false, null, "", "", "", 0.0,
            bestPv = "8c8d",
        )
        val repo = FakeGameRepository(game, listOf(report))
        val vm = ReportViewModel(this, repo, { FakeEngine(listOf("2g2f")) }, { "cp" }, UnconfinedTestDispatcher(testScheduler))
        vm.loadReport(1)
        val extendedMoves = listOf("7g7f", "8c8d", "2g2f")
        assertEquals(raw, vm.savedKifForExport(1))
        assertEquals(false, vm.hasUnsavedStudy())
        assertNull(vm.savedKifForExport(99))
        var saved: Boolean? = null
        vm.extendBestPv(10, computeSfenAtStep(null, extendedMoves, 2), "8c8d") { _, pv ->
            assertEquals("8c8d 2g2f", pv)
            val sfen = computeSfenAtStep(null, extendedMoves, 3)
            vm.startStudy(sfen, false, true, 2, 0, 3, StudyOrigin("延長末尾", null))
            vm.onStudySquareTapped(ShogiSquare(8, 4))
            vm.onStudySquareTapped(ShogiSquare(8, 5))
            assertTrue(vm.hasUnsavedStudy())
            assertEquals(raw, vm.savedKifForExport(1))
            vm.saveStudy { saved = it }
        }
        advanceUntilIdle()
        assertEquals(true, saved)
        assertEquals(false, vm.hasUnsavedStudy())
        val restored = StudyDocument(repo.studyKif!!) { _, _ -> false }
        assertNotNull(restored.tree.subtree(extendedMoves + "8d8e"))
        assertEquals(repo.studyKif, vm.savedKifForExport(1))
        assertNull(vm.savedKifForExport(99))
        vm.loadReport(99)
        assertNull(vm.savedKifForExport(1))
        vm.studyController.dispose()
    }

    @Test
    fun `古い開始局面のキャッシュから保存しても別局面で保存した枝を消さない`() = runTest {
        val raw = "1 ７六歩(77)\n2 ３四歩(33)\n3 ２六歩(27)\n4 投了"
        val game = GameRecord(1, "test.kif", "hash", 3, null, null, 0, 1000, coefVersion = "v1", kifText = raw, movesUsi = listOf("7g7f", "3c3d", "2g2f"))
        val repo = FakeGameRepository(game)
        val vm = ReportViewModel(this, repo, { FakeEngine() }, { "cp" }, UnconfinedTestDispatcher(testScheduler))
        vm.loadReport(1)
        fun start(ply: Int) = vm.startStudy(computeSfenAtStep(null, game.movesUsi, ply), false, false, ply, null, ply, StudyOrigin("局面", null))
        start(1)
        vm.endStudy()
        start(2)
        vm.onStudySquareTapped(ShogiSquare(7, 9))
        vm.onStudySquareTapped(ShogiSquare(6, 8))
        var saved: Boolean? = null
        vm.saveStudy { saved = it }
        advanceUntilIdle()
        assertEquals(true, saved)
        vm.endStudy()
        start(1)
        vm.onStudySquareTapped(ShogiSquare(8, 3))
        vm.onStudySquareTapped(ShogiSquare(8, 4))
        saved = null
        vm.saveStudy { saved = it }
        advanceUntilIdle()
        assertEquals(true, saved)
        val restored = StudyDocument(repo.studyKif!!) { _, _ -> false }
        assertNotNull(restored.tree.subtree(listOf("7g7f", "3c3d", "7i6h")))
        assertNotNull(restored.tree.subtree(listOf("7g7f", "8c8d")))
        vm.studyController.dispose()
    }

    @Test
    fun `複数開始局面の未保存分岐を一回で保存する`() = runTest {
        val raw = "1 ７六歩(77)\n2 ３四歩(33)\n3 投了"
        val game = GameRecord(1, "test.kif", "hash", 2, null, null, 0, 1000, coefVersion = "v1", kifText = raw, movesUsi = listOf("7g7f", "3c3d"))
        val repo = FakeGameRepository(game)
        val vm = ReportViewModel(this, repo, { FakeEngine() }, { "cp" }, UnconfinedTestDispatcher(testScheduler))
        vm.loadReport(1)
        for (ply in 0..1) {
            val sfen = computeSfenAtStep(null, game.movesUsi, ply)
            vm.startStudy(sfen, false, false, ply, null, ply, StudyOrigin("局面", null))
            vm.onStudySquareTapped(if (ply == 0) ShogiSquare(2, 7) else ShogiSquare(8, 3))
            vm.onStudySquareTapped(if (ply == 0) ShogiSquare(2, 6) else ShogiSquare(8, 4))
            if (ply == 0) vm.endStudy()
        }
        assertTrue(vm.hasUnsavedStudy())
        var saved: Boolean? = null
        vm.saveStudy { saved = it }
        advanceUntilIdle()
        assertEquals(true, saved)
        assertEquals(false, vm.hasUnsavedStudy())
        val restored = StudyDocument(repo.studyKif!!) { _, _ -> false }
        assertNotNull(restored.tree.subtree(listOf("2g2f")))
        assertNotNull(restored.tree.subtree(listOf("7g7f", "8c8d")))
        vm.dispose()
    }

    private class FakeEngine(private val pv: List<String> = emptyList()) : Engine {
        override fun analyze(moves: List<String>, nodes: Int): List<PvInfo> = emptyList()
        override fun analyzeSfen(sfen: String, additionalMoves: List<String>, nodes: Int, multiPv: Int): List<PvInfo> =
            if (pv.isEmpty()) emptyList() else listOf(PvInfo(1, dev.miyado.shogisupplement.blunder.Score.Cp(0), pv, 1))
        override fun quit() = Unit
        override fun newGame() = Unit
    }

    private class FakeGameRepository(val game: GameRecord? = null, val reports: List<BlunderRecord> = emptyList()) : GameRepository {
        var studyKif = game?.kifText
        override fun getStudyKif(gameId: Long): String? = if (gameId == game?.id) studyKif else null
        override fun saveStudyKif(gameId: Long, expectedKif: String, kif: String): Boolean {
            if (gameId != game?.id || studyKif != expectedKif) return false
            studyKif = kif
            return true
        }
        override fun saveAnalysisAtomically(request: GameRepository.AnalysisSaveRequest): Long = 0

        override fun saveAnalysis(
            fileName: String,
            contentHash: String,
            moves: List<String>,
            headers: Map<String, String>,
            reports: List<BlunderReport>,
            rating: Int,
            ratingSampleMoves: Int?,
            coefVersion: String,
            analyzedAt: Long,
            kifText: String?,
            userSide: String?,
            ratingService: String?,
            ratingRaw: Long?,
            ratingRule: String?,
            ratingDeclaredAt: Long?,
            sourcePlace: String?,
            gameWinner: String?,
            endReason: String?,
            openingStyle: String?,
            openingCastle: String?,
            openingTags: String?,
            senteRating: Long?,
            goteRating: Long?,
            timeControlRaw: String?,
            timeControlByoyomiRaw: String?,
            engineMetaJson: String?,
        ): Long = 0

        override fun seedFixtureBlunder(
            fileName: String,
            contentHash: String,
            rating: Int,
            coefVersion: String,
            report: BlunderReport,
            sfenBefore: String,
            userSide: String?,
            senteName: String?,
            goteName: String?,
            analyzedAt: Long,
        ): Long = 0

        override fun getByHash(contentHash: String): Long? = null
        override fun getAllGames(): List<GameRecord> = listOfNotNull(game)
        override fun getGameById(gameId: Long): GameRecord? = null
        override fun getNotUploadedGames(): List<GameRecord> = emptyList()
        override fun getUploadedGameCount(): Int = 0
        override fun getGamesWithUserSide(): List<GameRecord> = emptyList()
        override fun updateUploadedAt(gameId: Long, epochSeconds: Long) = Unit
        override fun updateUserSide(gameId: Long, userSide: String?, ratingService: String?, ratingRaw: Long?) = Unit
        override fun updateGamePlayers(gameId: Long, senteName: String?, goteName: String?) = Unit
        override fun resetAllUploadedAt() = Unit
        override fun getReports(gameId: Long): List<BlunderRecord> = reports
        override fun getBlunderCounts(): Map<Long, Int> = emptyMap()
        override fun updateBestPv(blunderId: Long, newPv: String) = Unit
        override fun savePositionEvals(gameId: Long, rows: List<PositionEvalRow>) = Unit
        override fun getPositionEvals(gameId: Long): List<PositionEvalRow> = emptyList()
        override fun deleteGame(gameId: Long) = Unit
        override fun deleteAllLocalData() = Unit
    }

    @Test
    fun `分岐を自動保存して再読込でき削除も自動保存し元棋譜の注記を保持する`() = runTest {
        val raw = "*元棋譜の注記\n&元のしおり\n1 ７六歩(77)\n2 ３四歩(33)\n3 投了"
        val game = GameRecord(1, "test.kif", "hash", 2, null, null, 0, 1000, coefVersion = "v1", kifText = raw, movesUsi = listOf("7g7f", "3c3d"))
        val repo = FakeGameRepository(game)
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        fun viewModel() = ReportViewModel(this, repo, { FakeEngine() }, { "cp" }, dispatcher)
        val sfen = computeSfenAtStep(null, game.movesUsi, 1)
        val vm = viewModel()
        vm.loadReport(1)
        vm.startStudy(sfen, false, false, 1, null, 1, StudyOrigin("1手目", null))
        vm.onStudySquareTapped(ShogiSquare(8, 3))
        vm.onStudySquareTapped(ShogiSquare(8, 4))
        advanceUntilIdle()
        assertFalse(vm.hasUnsavedStudy())
        assertTrue(repo.studyKif!!.contains("元棋譜の注記"))
        assertTrue(repo.studyKif!!.contains("元のしおり"))
        vm.endStudy()
        val reopened = viewModel()
        reopened.loadReport(1)
        reopened.startStudy(sfen, false, false, 1, null, 1, StudyOrigin("1手目", null))
        assertNotNull(reopened.studyController.currentTree()?.subtree(listOf("8c8d")))
        reopened.onStudySquareTapped(ShogiSquare(8, 3))
        reopened.onStudySquareTapped(ShogiSquare(8, 4))
        assertTrue(reopened.deleteStudyBranch(sfen, listOf("8c8d")))
        advanceUntilIdle()
        assertFalse(reopened.hasUnsavedStudy())
        val afterDelete = StudyDocument(repo.studyKif!!) { _, _ -> false }
        assertNull(afterDelete.tree.subtree(listOf("7g7f", "8c8d")))
        assertNotNull(afterDelete.tree.subtree(listOf("7g7f", "3c3d")))
        reopened.studyController.dispose()
        vm.studyController.dispose()
    }

    @Test
    fun `自動保存の失敗は編集を保持し再試行で保存できる`() = runTest {
        val game = GameRecord(1, "test.kif", "hash", 2, null, null, 0, 1000, coefVersion = "v1",
            kifText = "1 ７六歩(77)\n2 ３四歩(33)\n3 投了", movesUsi = listOf("7g7f", "3c3d"))
        val storage = FakeGameRepository(game)
        var fail = true
        val repo = object : GameRepository by storage {
            override fun saveStudyKif(gameId: Long, expectedKif: String, kif: String): Boolean =
                if (fail) false else storage.saveStudyKif(gameId, expectedKif, kif)
        }
        val vm = ReportViewModel(this, repo, { FakeEngine() }, { "cp" }, UnconfinedTestDispatcher(testScheduler))
        vm.loadReport(1)
        vm.startStudy(ShogiBoard().toSfen(), false, false, 0, null, 0, StudyOrigin("開始", null))
        vm.onStudyCandidateSelected("2g2f")
        vm.onStudyCandidateSelected("8c8d")
        advanceUntilIdle()
        assertTrue(vm.hasUnsavedStudy())
        assertTrue(vm.studyState.value!!.saveFailed)
        assertEquals(listOf("2g2f", "8c8d"), vm.studyState.value!!.moves)
        fail = false
        vm.saveStudy {}
        advanceUntilIdle()
        assertFalse(vm.hasUnsavedStudy())
        assertFalse(vm.studyState.value!!.saveFailed)
        assertNotNull(StudyDocument(storage.studyKif!!) { _, _ -> false }.tree.subtree(listOf("2g2f", "8c8d")))
        vm.dispose()
    }

    @Test
    fun `保存待ち中に追加して削除し別棋譜へ移動しても枝は復活しない`() = runTest {
        val game = GameRecord(1, "test.kif", "hash", 2, null, null, 0, 1000, coefVersion = "v1",
            kifText = "1 ７六歩(77)\n2 ３四歩(33)\n3 投了", movesUsi = listOf("7g7f", "3c3d"))
        val repo = FakeGameRepository(game)
        val vm = ReportViewModel(this, repo, { FakeEngine() }, { "cp" }, UnconfinedTestDispatcher(testScheduler))
        vm.loadReport(1)
        val sfen = ShogiBoard().toSfen()
        vm.startStudy(sfen, false, false, 0, null, 0, StudyOrigin("開始", null))
        vm.onStudyCandidateSelected("2g2f")
        assertTrue(vm.deleteStudyBranch(sfen, listOf("2g2f")))
        vm.loadReport(2)
        advanceUntilIdle()
        val saved = StudyDocument(repo.studyKif!!) { _, _ -> false }
        assertNull(saved.tree.subtree(listOf("2g2f")))
        assertNotNull(saved.tree.subtree(listOf("7g7f", "3c3d")))
        vm.dispose()
    }

    @Test
    fun `loadReportを呼ぶと検討状態が畳まれてnullになる`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val viewModel = ReportViewModel(
            scope = this,
            repository = FakeGameRepository(),
            engineFactory = { FakeEngine() },
            evalDisplayProvider = { "cp" },
            ioDispatcher = dispatcher,
        )

        viewModel.studyController.startStudy(
            baseSfen = ShogiBoard().toSfen(),
            flip = false,
            originIsBestPv = false,
            originPlyIndex = 0,
            originSelectedIdx = null,
            originAbsolutePly = 0,
            origin = StudyOrigin(label = "開始局面", userCp = null),
        )
        assertNotNull(viewModel.studyState.value)

        viewModel.loadReport(1L)

        assertNull(viewModel.studyState.value)
    }
}
