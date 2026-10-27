package dev.miyado.shogisupplement.ui

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.classify.ClassificationResult
import dev.miyado.shogisupplement.db.ShogiSupplementDatabase
import dev.miyado.shogisupplement.db.SqlDelightDrillRepository
import dev.miyado.shogisupplement.db.SqlDelightGameRepository
import dev.miyado.shogisupplement.db.SqlDelightSettingsRepository
import dev.miyado.shogisupplement.drill.DrillMode
import dev.miyado.shogisupplement.judge.Judgement
import dev.miyado.shogisupplement.judge.VerdictKind
import dev.miyado.shogisupplement.pipeline.BlunderReport
import dev.miyado.shogisupplement.ui.drill.DrillUiState
import dev.miyado.shogisupplement.ui.drill.DrillViewModel
import dev.miyado.shogisupplement.ui.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34], application = android.app.Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MateDrillModeTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    private val database = ShogiSupplementDatabase(driver)
    private val games = SqlDelightGameRepository(database)
    private val drills = SqlDelightDrillRepository(database)
    private val settings = SqlDelightSettingsRepository(database)

    @Before fun setup() {
        ShogiSupplementDatabase.Schema.create(driver)
        Dispatchers.setMain(dispatcher)
    }

    @After fun cleanup() {
        Dispatchers.resetMain()
        driver.close()
    }

    private fun seed(mate: Int?, verified: Boolean = true): Long {
        val game = games.seedFixtureBlunder(
            fileName = "test.kif", contentHash = "fixture-${System.nanoTime()}", rating = 1750,
            coefVersion = "test", userSide = "sente", sfenBefore = ShogiBoard().toSfen(),
            report = BlunderReport(
                ply = 1, side = "sente", moveUsi = "1g1f", bestUsi = "7g7f", lossWp = 0.1,
                classification = ClassificationResult("玉の危険（寄せ）", 0, 0, false, mate),
                judgement = Judgement(VerdictKind.TARGET, "○ 出題対象", "test", "test", 1.0),
                bestPv = "7g7f 3c3d",
            ),
        )
        val id = drills.getDrillCandidatesByGame(game).single().id
        if (!verified) driver.execute(null, "UPDATE blunder_report SET mate_verified = 0 WHERE id = $id", 0)
        return id
    }

    private fun viewModel(mode: DrillMode) = DrillViewModel(
        games, drills, settings, ioDispatcher = dispatcher, mode = mode,
    )

    @Test fun mateModeExcludesOrdinaryUnverifiedAndInvalidMateCandidates() {
        seed(null)
        seed(3, verified = false)
        seed(0)
        seed(-3)
        val mate = seed(5)
        val question = viewModel(DrillMode.MATE).state.value as DrillUiState.Question
        assertEquals(mate, question.blunder.id)
        assertEquals(1, question.totalCandidates)
        assertEquals(5, (viewModel(DrillMode.NORMAL).state.value as DrillUiState.Question).totalCandidates)
    }

    @Test fun nextQuestionAndAttemptStayWithinSelectedMode() {
        val ordinary = seed(null)
        val first = seed(3)
        val second = seed(5)
        val vm = viewModel(DrillMode.MATE)
        val shown = (vm.state.value as DrillUiState.Question).blunder.id
        assertTrue(shown in setOf(first, second))
        vm.onSurrender()
        vm.loadNextQuestion()
        val next = vm.state.value as DrillUiState.Question
        assertTrue(next.blunder.id in setOf(first, second))
        assertNotEquals(shown, next.blunder.id)
        assertEquals(2, next.totalCandidates)
        assertEquals(1, drills.getDrillAttempts(shown).size)
        assertTrue(drills.getDrillAttempts(ordinary).isEmpty())
    }

    @Test fun noMateCandidatesDoesNotFallbackToOrdinaryProblems() {
        seed(null)
        seed(3, verified = false)
        assertEquals(DrillUiState.NoCandidates, viewModel(DrillMode.MATE).state.value)
    }

    @Test fun homeAvailabilityTracksVerifiedCandidates() = runTest {
        seed(null)
        val home = HomeViewModel(games, drills, settings, dispatcher)
        assertFalse(home.loadHomeData().todaysDrillHint!!.hasMateDrill)
        seed(3, verified = false)
        assertFalse(home.loadHomeData().todaysDrillHint!!.hasMateDrill)
        seed(5)
        assertTrue(home.loadHomeData().todaysDrillHint!!.hasMateDrill)
    }
    @Test fun largePoolReturnsOnlyOneQuestionWithExactModeCount() {
        repeat(120) { seed(null) }
        repeat(50) { seed(3) }
        repeat(30) { seed(5, verified = false) }
        val mate = drills.getNextDrillQuestion(DrillMode.MATE)
        assertEquals(50, mate.totalCandidates)
        assertEquals(3L, mate.candidate!!.missedMateIn)
        assertEquals(0, mate.attemptCount)
        val normal = drills.getNextDrillQuestion(DrillMode.NORMAL)
        assertEquals(200, normal.totalCandidates)
        assertNotNull(normal.candidate)
        assertTrue(drills.hasDrillCandidates(DrillMode.MATE))
    }

    @Test fun emptyPoolHasNoQuestionAndZeroCounts() {
        for (mode in DrillMode.entries) {
            val selected = drills.getNextDrillQuestion(mode)
            assertNull(selected.candidate)
            assertEquals(0, selected.totalCandidates)
            assertEquals(0, selected.attemptCount)
            assertFalse(drills.hasDrillCandidates(mode))
        }
    }

}
