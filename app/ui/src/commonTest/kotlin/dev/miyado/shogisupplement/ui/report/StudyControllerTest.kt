package dev.miyado.shogisupplement.ui.report

import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.engine.Engine
import dev.miyado.shogisupplement.engine.PvInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * StudyController の検討木の永続化・分岐操作を検証する。
 *
 * レポート画面を開いている間は検討木を保持し、endStudy では破棄せず、
 * dispose（画面を離れる）で初めて破棄する、という挙動が主眼。
 *
 * エンジンは固定PVを返すFakeEngineを注入する。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StudyControllerTest {

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

    private fun newController(engine: FakeEngine = FakeEngine()) =
        StudyController(
            scope = testScope,
            ioDispatcher = testDispatcher,
            engineFactory = { engine },
            evalDisplayProvider = { "cp" },
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

        // 同じ baseSfen で再開: moves はルート（空）に戻るが、木は保持されているため
        // 同じ手を指し直せば新規ノードではなく既存ノードが再利用される
        // （検討木の再利用ロジック。ここではnodeId比較はできないが、
        // branchFlagsを介した間接検証として「2本目の別の手を指した後の分岐数」で確認する）。
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

        // 前回と同じ7g7fを指す。
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(7, 7))
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(7, 6))
        // 別の手（2g2f）を指すため、いったん戻って別の駒を選ぶ。
        controller.studyStepBack()
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(2, 7))
        controller.onStudySquareTapped(dev.miyado.shogisupplement.board.ShogiSquare(2, 6))
        assertEquals(listOf("2g2f"), controller.studyState.value?.moves)
        // 7g7fが1本目の木に既に存在していたため、2g2fを指した時点で兄弟が2本
        // （7g7f・2g2f）になっている＝木が保持されていたことの間接証拠。
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
        // dispose で木が破棄されたため、2g2fは兄弟なし（1本道）のはず。
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
        // altSfen は初めての分岐元なので、2g2f を指しても兄弟は無い（1本道）。
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

        // チップタップ相当（onChipTapped(0)）でルートへ戻り、7g7fがまだ辿れることを確認。
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

        controller.analyzeCurrentPosition()

        assertEquals(StudyEvalState.Error, controller.studyState.value?.evalState)
    }

    @Test
    fun `analyzeCurrentPositionは解析結果をevalStateに反映する`() {
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

        controller.analyzeCurrentPosition()

        assertEquals(1, engine.analyzeCallCount)
        val evalState = controller.studyState.value?.evalState
        assertTrue(evalState is StudyEvalState.Value)
    }

    // ─── displayLine（先の手を消さず表示し続ける。実機確認対応）───────────────────

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

        // 別の手（2g2f）を指す＝分岐に入る。
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
