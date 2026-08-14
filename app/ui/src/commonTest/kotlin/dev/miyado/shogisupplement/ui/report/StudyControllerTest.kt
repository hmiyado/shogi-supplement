package dev.miyado.shogisupplement.ui.report

import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.engine.Engine
import dev.miyado.shogisupplement.engine.PvInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 検討木の永続化・分岐操作を保証する。 */
@OptIn(ExperimentalCoroutinesApi::class)
class StudyControllerTest {

    private class NoPvEngine : Engine {
        override fun analyze(moves: List<String>, nodes: Int): List<PvInfo> = emptyList()
        override fun analyzeSfen(sfen: String, additionalMoves: List<String>, nodes: Int): List<PvInfo> = emptyList()
        override fun quit() = Unit
        override fun newGame() = Unit
    }

    private class FakeEngine(private val score: Score = Score.Cp(0)) : Engine {
        var analyzeCallCount = 0
            private set
        var quitCount = 0
            private set

        override fun analyze(moves: List<String>, nodes: Int): List<PvInfo> {
            analyzeCallCount++
            return listOf(PvInfo(multipv = 1, score = score, pv = emptyList(), nodes = 0L))
        }

        override fun analyzeSfen(sfen: String, additionalMoves: List<String>, nodes: Int): List<PvInfo> {
            analyzeCallCount++
            return listOf(PvInfo(multipv = 1, score = score, pv = emptyList(), nodes = 0L))
        }

        override fun quit() {
            quitCount++
        }

        override fun newGame() {}
    }

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val startSfen = ShogiBoard().toSfen()
    private val noOrigin = StudyOrigin(label = "開始局面", userCp = null)

    private fun newController(
        engine: FakeEngine = FakeEngine(),
        localEngineLikelyAvailable: () -> Boolean = { true },
    ) = StudyController(
        scope = testScope,
        ioDispatcher = testDispatcher,
        engineFactory = { engine },
        evalDisplayProvider = { "cp" },
        localEngineLikelyAvailable = localEngineLikelyAvailable,
    ) to engine

    @Test
    fun `検討木はendStudyでは破棄されず同じ分岐元で再開すると続きから辿れる`() {
        val (controller, _) = newController()
        controller.startStudy(
            baseSfen = startSfen,
            flip = false,
            originIsBestPv = false,
            originPlyIndex = 0,
            originSelectedIdx = null,
            originAbsolutePly = 0,
            origin = noOrigin,
        )
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(7, 7))
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(7, 6))
        assertEquals(listOf("7g7f"), controller.studyState.value?.moves)

        controller.endStudy()
        assertNull(controller.studyState.value, "endStudyでstudyStateはnullに戻る")

        controller.startStudy(
            baseSfen = startSfen,
            flip = false,
            originIsBestPv = false,
            originPlyIndex = 0,
            originSelectedIdx = null,
            originAbsolutePly = 0,
            origin = noOrigin,
        )
        assertEquals(emptyList(), controller.studyState.value?.moves, "再開直後はルート（moves空）から")

        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(7, 7))
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(7, 6))
        controller.studyStepBack()
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(2, 7))
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(2, 6))
        assertEquals(listOf("2g2f"), controller.studyState.value?.moves)
        assertEquals(listOf(true), controller.studyState.value?.branchFlags)
    }

    @Test
    fun `disposeで検討木が破棄され再開しても新しい木になる`() {
        val (controller, _) = newController()
        controller.startStudy(
            baseSfen = startSfen,
            flip = false,
            originIsBestPv = false,
            originPlyIndex = 0,
            originSelectedIdx = null,
            originAbsolutePly = 0,
            origin = noOrigin,
        )
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(7, 7))
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(7, 6))
        controller.endStudy()

        controller.dispose()

        controller.startStudy(
            baseSfen = startSfen,
            flip = false,
            originIsBestPv = false,
            originPlyIndex = 0,
            originSelectedIdx = null,
            originAbsolutePly = 0,
            origin = noOrigin,
        )
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(2, 7))
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(2, 6))
        assertEquals(listOf("2g2f"), controller.studyState.value?.moves)
        assertEquals(listOf(false), controller.studyState.value?.branchFlags)
    }

    @Test
    fun `異なる分岐元は別々の木として扱われる`() {
        val (controller, _) = newController()
        val altSfen = ShogiBoard().also { it.push(dev.miyado.shogisupplement.board.ShogiMove.fromUsi("7g7f")) }.toSfen()

        controller.startStudy(
            baseSfen = startSfen,
            flip = false,
            originIsBestPv = false,
            originPlyIndex = 0,
            originSelectedIdx = null,
            originAbsolutePly = 0,
            origin = noOrigin,
        )
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(2, 7))
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(2, 6))
        controller.endStudy()

        controller.startStudy(
            baseSfen = altSfen,
            flip = false,
            originIsBestPv = false,
            originPlyIndex = 1,
            originSelectedIdx = null,
            originAbsolutePly = 1,
            origin = noOrigin,
        )
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(2, 3))
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(2, 4))
        assertEquals(listOf(false), controller.studyState.value?.branchFlags)
    }

    @Test
    fun `指し直しは兄弟ノードを作り既存の変化を消さない`() {
        val (controller, _) = newController()
        controller.startStudy(
            baseSfen = startSfen,
            flip = false,
            originIsBestPv = false,
            originPlyIndex = 0,
            originSelectedIdx = null,
            originAbsolutePly = 0,
            origin = noOrigin,
        )
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(7, 7))
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(7, 6))
        assertEquals(listOf("7g7f"), controller.studyState.value?.moves)

        controller.studyStepBack()
        assertEquals(emptyList(), controller.studyState.value?.moves)

        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(2, 7))
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(2, 6))
        assertEquals(listOf("2g2f"), controller.studyState.value?.moves)
        assertEquals(
            listOf(true),
            controller.studyState.value?.branchFlags,
            "2手が兄弟として存在するので branchFlags[0] は true",
        )

        controller.onChipTapped(0)
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(7, 7))
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(7, 6))
        assertEquals(listOf("7g7f"), controller.studyState.value?.moves, "既存の変化(7g7f)が消えずに残っている")
    }

    @Test
    fun `分岐チップタップでポップの中身が兄弟変化になり選択で切り替わる`() {
        val (controller, _) = newController()
        controller.startStudy(
            baseSfen = startSfen,
            flip = false,
            originIsBestPv = false,
            originPlyIndex = 0,
            originSelectedIdx = null,
            originAbsolutePly = 0,
            origin = noOrigin,
        )
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(7, 7))
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(7, 6))
        controller.studyStepBack()
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(2, 7))
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(2, 6))
        assertEquals(listOf("2g2f"), controller.studyState.value?.moves)

        controller.onBranchChipTapped(0)
        val options = controller.studyState.value?.branchPopupOptions
        assertNotNull(options)
        assertEquals(setOf("7g7f", "2g2f"), options.map { it.moveUsi }.toSet())
        assertTrue(options.first { it.moveUsi == "2g2f" }.isCurrent)
        assertFalse(options.first { it.moveUsi == "7g7f" }.isCurrent)

        controller.onBranchOptionSelected(0, "7g7f")
        assertEquals(listOf("7g7f"), controller.studyState.value?.moves)
        assertNull(controller.studyState.value?.openBranchPopupDepth, "選択後はポップが閉じる")
    }

    @Test
    fun `analyzeCurrentPositionはmovesが空のときは何もしない`() {
        val (controller, engine) = newController()
        controller.startStudy(
            baseSfen = startSfen,
            flip = false,
            originIsBestPv = false,
            originPlyIndex = 0,
            originSelectedIdx = null,
            originAbsolutePly = 0,
            origin = noOrigin,
        )
        controller.analyzeCurrentPosition()
        assertEquals(0, engine.analyzeCallCount)
        assertEquals(StudyEvalState.None, controller.studyState.value?.evalState)
    }

    @Test
    fun `engineFactoryが例外を投げるとStudyEvalStateはErrorになる`() {
        val controller = StudyController(
            scope = testScope,
            ioDispatcher = testDispatcher,
            engineFactory = { error("engine unavailable") },
            evalDisplayProvider = { "cp" },
            localEngineLikelyAvailable = { false },
        )
        controller.startStudy(
            baseSfen = startSfen,
            flip = false,
            originIsBestPv = false,
            originPlyIndex = 0,
            originSelectedIdx = null,
            originAbsolutePly = 0,
            origin = noOrigin,
        )
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(7, 7))
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(7, 6))
        assertEquals(
            StudyEvalState.Preparing,
            controller.studyState.value?.evalState,
            "自動発火は無効化しているので着手直後はPreparingのまま",
        )

        controller.analyzeCurrentPosition()

        assertEquals(StudyEvalState.Error, controller.studyState.value?.evalState)
    }

    @Test
    fun `analyzeCurrentPositionは解析結果をevalStateに反映する`() {
        val (controller, engine) = newController(
            FakeEngine(score = Score.Cp(120)),
            localEngineLikelyAvailable = { false },
        )
        controller.startStudy(
            baseSfen = startSfen,
            flip = false,
            originIsBestPv = false,
            originPlyIndex = 0,
            originSelectedIdx = null,
            originAbsolutePly = 0,
            origin = noOrigin,
        )
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(7, 7))
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(7, 6))
        assertEquals(0, engine.analyzeCallCount, "自動発火は無効化しているので着手だけでは呼ばれない")

        controller.analyzeCurrentPosition()

        assertEquals(1, engine.analyzeCallCount)
        val evalState = controller.studyState.value?.evalState
        assertTrue(evalState is StudyEvalState.Value)
    }

    @Test
    fun `指せる手が無くなった局面で読み筋が無ければ詰みとして表示する`() {
        // 5三の金を5二へ動かすと後手玉が詰む（5九の飛車が金を支える）。
        val controller = StudyController(
            scope = testScope,
            ioDispatcher = testDispatcher,
            engineFactory = { NoPvEngine() },
            evalDisplayProvider = { "cp" },
        )
        controller.startStudy(
            baseSfen = "4k4/9/4G4/9/9/9/9/9/4R4 b - 1",
            flip = false,
            originIsBestPv = false,
            originPlyIndex = 0,
            originSelectedIdx = null,
            originAbsolutePly = 0,
            origin = noOrigin,
        )
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(5, 3))
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(5, 2))

        assertIs<StudyEvalState.Value>(controller.studyState.value?.evalState)
    }

    @Test
    fun `指せる手があるのに読み筋が無ければエラーにする`() {
        val controller = StudyController(
            scope = testScope,
            ioDispatcher = testDispatcher,
            engineFactory = { NoPvEngine() },
            evalDisplayProvider = { "cp" },
        )
        controller.startStudy(
            baseSfen = startSfen,
            flip = false,
            originIsBestPv = false,
            originPlyIndex = 0,
            originSelectedIdx = null,
            originAbsolutePly = 0,
            origin = noOrigin,
        )
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(7, 7))
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(7, 6))

        assertEquals(StudyEvalState.Error, controller.studyState.value?.evalState)
    }

    @Test
    fun `着手すると自動的に解析が走りevalStateがValueになる`() {
        val (controller, engine) = newController(FakeEngine(score = Score.Cp(120)))
        controller.startStudy(
            baseSfen = startSfen,
            flip = false,
            originIsBestPv = false,
            originPlyIndex = 0,
            originSelectedIdx = null,
            originAbsolutePly = 0,
            origin = noOrigin,
        )
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(7, 7))
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(7, 6))

        assertEquals(1, engine.analyzeCallCount, "ボタン操作なしで自動的に解析が走る")
        assertIs<StudyEvalState.Value>(controller.studyState.value?.evalState)
    }

    @Test
    fun `ローカルエンジンが使える見込みが無いときは自動発火せずPreparingになる`() {
        val (controller, engine) = newController(localEngineLikelyAvailable = { false })
        controller.startStudy(
            baseSfen = startSfen,
            flip = false,
            originIsBestPv = false,
            originPlyIndex = 0,
            originSelectedIdx = null,
            originAbsolutePly = 0,
            origin = noOrigin,
        )
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(7, 7))
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(7, 6))

        assertEquals(0, engine.analyzeCallCount, "サーバークォータ保護のため着手だけでは解析しない")
        assertEquals(StudyEvalState.Preparing, controller.studyState.value?.evalState)
    }

    @Test
    fun `Preparing中にローカルエンジンが使えるようになるとユーザー操作なしで解析される`() {
        var available = false
        val (controller, engine) = newController(localEngineLikelyAvailable = { available })
        controller.startStudy(
            baseSfen = startSfen,
            flip = false,
            originIsBestPv = false,
            originPlyIndex = 0,
            originSelectedIdx = null,
            originAbsolutePly = 0,
            origin = noOrigin,
        )
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(7, 7))
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(7, 6))
        assertEquals(StudyEvalState.Preparing, controller.studyState.value?.evalState)
        assertEquals(0, engine.analyzeCallCount)

        available = true
        testScope.testScheduler.advanceUntilIdle()

        assertEquals(1, engine.analyzeCallCount)
        assertIs<StudyEvalState.Value>(controller.studyState.value?.evalState)
    }

    /** 局面Aの解析中にBへ進むレースで、AをキャッシュしBを取りこぼさないことを保証する。 */
    @Test
    fun `解析中に次の手を指すと古い局面の結果は捨てずにキャッシュしつつ現在局面が解析される`() {
        val engine = FakeEngine(score = Score.Cp(50))
        val ioTestDispatcher = StandardTestDispatcher(testScope.testScheduler)
        val controller = StudyController(
            scope = testScope,
            ioDispatcher = ioTestDispatcher,
            engineFactory = { engine },
            evalDisplayProvider = { "cp" },
        )
        controller.startStudy(
            baseSfen = startSfen,
            flip = false,
            originIsBestPv = false,
            originPlyIndex = 0,
            originSelectedIdx = null,
            originAbsolutePly = 0,
            origin = noOrigin,
        )
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(7, 7))
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(7, 6))
        assertEquals(StudyEvalState.Loading, controller.studyState.value?.evalState)
        assertEquals(0, engine.analyzeCallCount, "ioDispatcherをまだ進めていないのでエンジン未呼び出し")

        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(3, 3))
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(3, 4))
        assertEquals(listOf("7g7f", "3c3d"), controller.studyState.value?.moves)
        assertEquals(
            StudyEvalState.None,
            controller.studyState.value?.evalState,
            "Aの解析実行中はBの自動発火が保留される（空スロットのまま）",
        )

        testScope.testScheduler.advanceUntilIdle()

        assertEquals(2, engine.analyzeCallCount, "AがBに割り込まれず、Bも取りこぼされない")
        assertEquals(
            listOf("7g7f", "3c3d"),
            controller.studyState.value?.moves,
            "現在局面はBのまま",
        )
        assertIs<StudyEvalState.Value>(controller.studyState.value?.evalState)
        assertIs<StudyEvalState.Value>(controller.studyState.value?.chipEvalStates?.getOrNull(0))
    }

    @Test
    fun `1手戻ってもdisplayLineは縮まず先の手が残り続ける`() {
        val (controller, _) = newController()
        controller.startStudy(
            baseSfen = startSfen,
            flip = false,
            originIsBestPv = false,
            originPlyIndex = 0,
            originSelectedIdx = null,
            originAbsolutePly = 0,
            origin = noOrigin,
        )
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(7, 7))
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(7, 6))
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(3, 3))
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(3, 4))
        assertEquals(listOf("7g7f", "3c3d"), controller.studyState.value?.moves)
        assertEquals(listOf("7g7f", "3c3d"), controller.studyState.value?.displayLine)

        controller.studyStepBack()

        assertEquals(
            listOf("7g7f"),
            controller.studyState.value?.moves,
            "1手戻ったので現在局面はmoves=[7g7f]",
        )
        assertEquals(
            listOf("7g7f", "3c3d"),
            controller.studyState.value?.displayLine,
            "displayLineは縮めず先の手(3c3d)のチップを表示し続ける",
        )
    }

    @Test
    fun `先のチップタップ相当(onChipTapped)で戻らずdisplayLineの先まで進める`() {
        val (controller, _) = newController()
        controller.startStudy(
            baseSfen = startSfen,
            flip = false,
            originIsBestPv = false,
            originPlyIndex = 0,
            originSelectedIdx = null,
            originAbsolutePly = 0,
            origin = noOrigin,
        )
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(7, 7))
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(7, 6))
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(3, 3))
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(3, 4))
        controller.studyStepBack()
        assertEquals(listOf("7g7f"), controller.studyState.value?.moves)

        controller.onChipTapped(2)

        assertEquals(
            listOf("7g7f", "3c3d"),
            controller.studyState.value?.moves,
            "displayLineの先のチップをタップすると、そこまで指し直さずに進める",
        )
        assertEquals(listOf("7g7f", "3c3d"), controller.studyState.value?.displayLine)
    }

    @Test
    fun `戻ってから別の手を指すとdisplayLineが新しいラインに置き換わる`() {
        val (controller, _) = newController()
        controller.startStudy(
            baseSfen = startSfen,
            flip = false,
            originIsBestPv = false,
            originPlyIndex = 0,
            originSelectedIdx = null,
            originAbsolutePly = 0,
            origin = noOrigin,
        )
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(7, 7))
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(7, 6))
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(3, 3))
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(3, 4))
        controller.studyResetToStart()
        assertEquals(emptyList(), controller.studyState.value?.moves)
        assertEquals(
            listOf("7g7f", "3c3d"),
            controller.studyState.value?.displayLine,
            "リセット直後はまだ旧ラインを表示している",
        )

        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(2, 7))
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(2, 6))

        assertEquals(listOf("2g2f"), controller.studyState.value?.moves)
        assertEquals(
            listOf("2g2f"),
            controller.studyState.value?.displayLine,
            "別の手を指したのでdisplayLineは新しいラインに置き換わる（旧ラインは木には残る）",
        )
    }
}
