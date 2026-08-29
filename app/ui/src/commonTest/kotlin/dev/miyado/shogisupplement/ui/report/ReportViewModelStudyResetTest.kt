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
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModelStudyResetTest {

    private class FakeEngine : Engine {
        override fun analyze(moves: List<String>, nodes: Int): List<PvInfo> = emptyList()
        override fun analyzeSfen(sfen: String, additionalMoves: List<String>, nodes: Int): List<PvInfo> = emptyList()
        override fun quit() = Unit
        override fun newGame() = Unit
    }

    private class FakeGameRepository : GameRepository {
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
            sourcePlace: String?,
            gameWinner: String?,
            endReason: String?,
            openingStyle: String?,
            openingCastle: String?,
            openingTags: String?,
            senteRating: Long?,
            goteRating: Long?,
            timeControlKind: String?,
            timeControlBaseMinutes: Long?,
            timeControlIncrementSeconds: Long?,
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
        override fun getAllGames(): List<GameRecord> = emptyList()
        override fun getGameById(gameId: Long): GameRecord? = null
        override fun getNotUploadedGames(): List<GameRecord> = emptyList()
        override fun getUploadedGameCount(): Int = 0
        override fun getGamesWithUserSide(): List<GameRecord> = emptyList()
        override fun updateUploadedAt(gameId: Long, epochSeconds: Long) = Unit
        override fun updateUserSide(gameId: Long, userSide: String?, ratingService: String?, ratingRaw: Long?) = Unit
        override fun resetAllUploadedAt() = Unit
        override fun getReports(gameId: Long): List<BlunderRecord> = emptyList()
        override fun updateBestPv(blunderId: Long, newPv: String) = Unit
        override fun savePositionEvals(gameId: Long, rows: List<PositionEvalRow>) = Unit
        override fun getPositionEvals(gameId: Long): List<PositionEvalRow> = emptyList()
        override fun deleteGame(gameId: Long) = Unit
        override fun deleteAllLocalData() = Unit
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
