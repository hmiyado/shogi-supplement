package dev.miyado.shogisupplement.ui

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.classify.ClassificationResult
import dev.miyado.shogisupplement.db.DrillRepository
import dev.miyado.shogisupplement.db.GameRepository
import dev.miyado.shogisupplement.db.SettingsRepository
import dev.miyado.shogisupplement.db.ShogiSupplementDatabase
import dev.miyado.shogisupplement.engine.Engine
import dev.miyado.shogisupplement.engine.PvInfo
import dev.miyado.shogisupplement.judge.Judgement
import dev.miyado.shogisupplement.judge.VerdictKind
import dev.miyado.shogisupplement.pipeline.BlunderReport
import dev.miyado.shogisupplement.ui.common.PvExtState
import dev.miyado.shogisupplement.ui.drill.DrillUiState
import dev.miyado.shogisupplement.ui.drill.DrillViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * DrillViewModel.extendBestPv（読み筋のオンデマンド延長・結果画面の「最善」タブ）の単体テスト。
 *
 * インメモリDB + FakeEngine を注入して検証する（AccountViewModelTest と同じ流儀）。
 * PvExtensionRunner 本体の合法性検証ロジックは ReportViewModel 側と共通（PvExtensionRunner.kt）
 * のため、ここでは DrillViewModel 経由の結線（状態遷移・DB反映・Result.blunder差し替え）を検証する。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DrillViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    // 平手初期局面。sente 番。
    private val sfenBefore = ShogiBoard().toSfen()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** analyzeSfen の呼び出しに対し、固定の pv（1本のみ）を返すフェイクエンジン。 */
    private class FixedPvEngine(private val pv: List<String>) : Engine {
        var quitCount = 0
            private set

        override fun analyze(moves: List<String>, nodes: Int): List<PvInfo> =
            listOf(PvInfo(multipv = 1, score = Score.Cp(0), pv = pv, nodes = 0L))

        override fun analyzeSfen(sfen: String, additionalMoves: List<String>, nodes: Int): List<PvInfo> =
            listOf(PvInfo(multipv = 1, score = Score.Cp(0), pv = pv, nodes = 0L))

        override fun quit() {
            quitCount++
        }

        override fun newGame() {}
    }

    /** 同一インメモリDBを共有する GameRepository/DrillRepository/SettingsRepository の組。 */
    private class TestRepos(
        val game: GameRepository,
        val drill: DrillRepository,
        val settings: SettingsRepository,
    )

    private fun createDb(): TestRepos {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShogiSupplementDatabase.Schema.create(driver)
        val database = ShogiSupplementDatabase(driver)
        return TestRepos(GameRepository(database), DrillRepository(database), SettingsRepository(database))
    }

    /**
     * ドリル候補を1件seedし、DrillViewModel を生成して「降参」で即座に結果画面
     * （DrillUiState.Result）へ遷移させる。best_pv は "7g7f 3c3d"（2手）を持つ。
     */
    private fun buildVmAtResult(
        repos: TestRepos = createDb(),
        engineFactory: (() -> Engine)? = null,
    ): DrillViewModel {
        val report = BlunderReport(
            ply = 1,
            side = "sente",
            moveUsi = "1g1f",
            bestUsi = "7g7f",
            lossWp = 0.1,
            classification = ClassificationResult(
                category = "駒損（タクティクス）",
                diffMaterial = 0,
                punishChecks = 0,
                tookMovedPiece = false,
                missedMateIn = null,
            ),
            judgement = Judgement(
                kind = VerdictKind.TARGET,
                verdict = "○ 出題対象",
                note = "テスト用",
                problem = "テスト問題",
                priority = 1.0,
            ),
            bestPv = "7g7f 3c3d",
        )
        repos.game.seedFixtureBlunder(
            fileName = "test.kif",
            contentHash = "test-hash-${System.nanoTime()}",
            rating = 1750,
            coefVersion = "test",
            report = report,
            sfenBefore = sfenBefore,
            userSide = "sente",
        )
        val vm = DrillViewModel(
            gameRepository = repos.game,
            drillRepository = repos.drill,
            settingsRepository = repos.settings,
            engineFactory = engineFactory,
            ioDispatcher = testDispatcher,
        )
        vm.onSurrender()
        return vm
    }

    private fun sfenAtLineEnd(bestPv: String): String {
        val board = ShogiBoard.fromSfen(sfenBefore)
        bestPv.split(" ").filter { it.isNotBlank() }.forEach { board.push(ShogiMove.fromUsi(it)) }
        return board.toSfen()
    }

    // ─── 成功: bestPv が伸びる ─────────────────────────────────────────────────

    @Test
    fun extendBestPv_success_appendsToStateAndDb() {
        val repos = createDb()
        val engine = FixedPvEngine(pv = listOf("2g2f"))
        val vm = buildVmAtResult(repos, engineFactory = { engine })

        val before = vm.state.value as DrillUiState.Result
        assertEquals("7g7f 3c3d", before.blunder.bestPv)

        vm.extendBestPv(sfenAtLineEnd(before.blunder.bestPv!!))

        val after = vm.state.value as DrillUiState.Result
        assertEquals("7g7f 3c3d 2g2f", after.blunder.bestPv)
        assertTrue("延長成功後は pvExtState からエントリが消える", vm.pvExtState.value[after.blunder.id] == null)
        // DB側も更新されていること
        val persisted = repos.game.getReports(after.blunder.gameId).first { it.id == after.blunder.id }
        assertEquals("7g7f 3c3d 2g2f", persisted.bestPv)
        assertEquals(1, engine.quitCount)
    }

    // ─── 非合法PVの切り詰め ────────────────────────────────────────────────────

    @Test
    fun extendBestPv_truncatesIllegalContinuation() {
        // 2g2f の次（後手番）に "9i9h"（先手専用の表記・後手番では非合法）を続けて返す
        // フェイク。先頭手（2g2f）は合法だが2手目が非合法なため、truncateToLegalPrefix で
        // 2手目を切り捨てて1手だけ連結されることを検証する。
        val engine = FixedPvEngine(pv = listOf("2g2f", "9i9h"))
        val vm = buildVmAtResult(engineFactory = { engine })

        val before = vm.state.value as DrillUiState.Result
        vm.extendBestPv(sfenAtLineEnd(before.blunder.bestPv!!))

        val after = vm.state.value as DrillUiState.Result
        assertEquals("7g7f 3c3d 2g2f", after.blunder.bestPv)
    }

    // ─── エラー状態への遷移 ────────────────────────────────────────────────────

    @Test
    fun extendBestPv_emptyPv_setsErrorState() {
        val engine = FixedPvEngine(pv = emptyList())
        val vm = buildVmAtResult(engineFactory = { engine })

        val before = vm.state.value as DrillUiState.Result
        vm.extendBestPv(sfenAtLineEnd(before.blunder.bestPv!!))

        val after = vm.state.value as DrillUiState.Result
        // bestPv は変化しない（DB更新前にエラーになるため）
        assertEquals("7g7f 3c3d", after.blunder.bestPv)
        assertTrue(vm.pvExtState.value[after.blunder.id] is PvExtState.Error)
    }

    @Test
    fun extendBestPv_illegalFirstMove_setsErrorState() {
        // 開始局面（sfenAtLineEnd）で明らかに非合法な手（自陣の空きマスへの着手）を返す。
        val engine = FixedPvEngine(pv = listOf("5e5d"))
        val vm = buildVmAtResult(engineFactory = { engine })

        val before = vm.state.value as DrillUiState.Result
        vm.extendBestPv(sfenAtLineEnd(before.blunder.bestPv!!))

        val after = vm.state.value as DrillUiState.Result
        assertEquals("7g7f 3c3d", after.blunder.bestPv)
        assertTrue(vm.pvExtState.value[after.blunder.id] is PvExtState.Error)
    }

    @Test
    fun extendBestPv_noEngineFactory_setsErrorState() {
        val vm = buildVmAtResult(engineFactory = null)

        val before = vm.state.value as DrillUiState.Result
        vm.extendBestPv(sfenAtLineEnd(before.blunder.bestPv!!))

        val after = vm.state.value as DrillUiState.Result
        assertEquals("7g7f 3c3d", after.blunder.bestPv)
        assertTrue(vm.pvExtState.value[after.blunder.id] is PvExtState.Error)
    }

    @Test
    fun extendBestPv_notInResultState_isNoOp() {
        // Question 状態のまま呼んでも何も起きない（対象は現在出題中の blunder のみ）。
        val repos = createDb()
        val report = BlunderReport(
            ply = 1,
            side = "sente",
            moveUsi = "1g1f",
            bestUsi = "7g7f",
            lossWp = 0.1,
            classification = ClassificationResult(
                category = "駒損（タクティクス）",
                diffMaterial = 0,
                punishChecks = 0,
                tookMovedPiece = false,
                missedMateIn = null,
            ),
            judgement = Judgement(
                kind = VerdictKind.TARGET,
                verdict = "○ 出題対象",
                note = "テスト用",
                problem = "テスト問題",
                priority = 1.0,
            ),
            bestPv = "7g7f 3c3d",
        )
        repos.game.seedFixtureBlunder(
            fileName = "test.kif",
            contentHash = "test-hash-question-${System.nanoTime()}",
            rating = 1750,
            coefVersion = "test",
            report = report,
            sfenBefore = sfenBefore,
            userSide = "sente",
        )
        val vm = DrillViewModel(
            gameRepository = repos.game,
            drillRepository = repos.drill,
            settingsRepository = repos.settings,
            ioDispatcher = testDispatcher,
        )
        assertTrue(vm.state.value is DrillUiState.Question)

        vm.extendBestPv(sfenAtLineEnd("7g7f 3c3d"))

        assertTrue(vm.state.value is DrillUiState.Question)
        assertNull(vm.pvExtState.value[(vm.state.value as DrillUiState.Question).blunder.id])
    }
}
