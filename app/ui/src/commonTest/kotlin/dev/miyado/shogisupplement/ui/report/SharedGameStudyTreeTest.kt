package dev.miyado.shogisupplement.ui.report

import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.board.ShogiSquare
import dev.miyado.shogisupplement.gameshare.GameShareBranch
import dev.miyado.shogisupplement.gameshare.GameShareSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SharedGameStudyTreeTest {
    private val testScope = TestScope(UnconfinedTestDispatcher())
    private val startSfen = ShogiBoard().toSfen()
    private val mainline = listOf("7g7f", "3c3d", "2g2f", "8c8d")
    private val origin = StudyOrigin("共有された棋譜", null)

    private fun snapshot(
        selectedPly: Int = 3,
        branch: GameShareBranch? = null,
    ) = GameShareSnapshot(
        id = "AbCdEfGhIjKlMnOpQrStUv",
        initialSfen = startSfen,
        mainline = mainline,
        selectedPly = selectedPly,
        branch = branch,
        userSide = "sente",
        flip = false,
        winner = null,
        endReason = null,
    )

    private fun started(snapshot: GameShareSnapshot): StudyController {
        val controller = StudyController(
            scope = testScope,
            studyEngineFactory = { error("engine is not used") },
            evalDisplayProvider = { "cp" },
            localEngineLikelyAvailable = { false },
        )
        controller.startStudy(
            baseSfen = snapshot.initialSfen,
            flip = snapshot.flip,
            originIsBestPv = false,
            originPlyIndex = 0,
            originSelectedIdx = null,
            originAbsolutePly = 0,
            origin = origin,
            initialTree = snapshot.initialStudyTree(),
            protectedMoves = snapshot.mainline,
        )
        controller.navigateToMoves(snapshot.selectedPath())
        return controller
    }

    private fun sfenAfter(moves: List<String>): String =
        ShogiBoard().also { board -> moves.forEach { board.push(ShogiMove.fromUsi(it)) } }.toSfen()

    @Test
    fun selectedPlyOpensRealBoardAndKeepsWholeMainline() {
        val snapshot = snapshot(selectedPly = 3)
        val controller = started(snapshot)
        val state = assertNotNull(controller.studyState.value)

        assertEquals(mainline.take(3), state.moves)
        assertEquals(mainline, state.displayLine)
        assertEquals(sfenAfter(mainline.take(3)), computeSfenAtStep(state.baseSfen, state.moves, state.moves.size))
        assertTrue(controller.currentTree()!!.hasSameMovesAs(snapshot.initialStudyTree()))
        controller.dispose()
    }

    @Test
    fun mainlineJumpsMoveForwardAndBackFromSeed() {
        val snapshot = snapshot(selectedPly = 3)
        val controller = started(snapshot)

        controller.navigateToMoves(snapshot.mainline.take(4))
        assertEquals(mainline, controller.studyState.value?.moves)
        assertEquals(
            sfenAfter(mainline),
            computeSfenAtStep(startSfen, controller.studyState.value!!.moves, mainline.size),
        )

        controller.navigateToMoves(emptyList())
        assertEquals(emptyList(), controller.studyState.value?.moves)
        assertEquals(startSfen, computeSfenAtStep(startSfen, emptyList(), 0))

        controller.navigateToMoves(snapshot.mainline.take(2))
        assertEquals(mainline.take(2), controller.studyState.value?.moves)
        assertEquals(
            sfenAfter(mainline.take(2)),
            computeSfenAtStep(startSfen, controller.studyState.value!!.moves, 2),
        )
        controller.dispose()
    }

    @Test
    fun selectedBranchOpensAndMainlineStaysNavigable() {
        val branch = GameShareBranch(originPly = 2, moves = listOf("5g5f", "8c8d"))
        val branchPath = mainline.take(2) + branch.moves
        val snapshot = snapshot(selectedPly = 4, branch = branch)
        val controller = started(snapshot)

        assertEquals(branchPath, controller.studyState.value?.moves)
        assertEquals(branchPath, controller.studyState.value?.displayLine)
        assertEquals(
            sfenAfter(branchPath),
            computeSfenAtStep(startSfen, controller.studyState.value!!.moves, branchPath.size),
        )
        assertTrue(controller.currentTree()!!.hasSameMovesAs(snapshot.initialStudyTree()))

        controller.navigateToMoves(snapshot.mainline)
        assertEquals(mainline, controller.studyState.value?.moves)
        assertEquals(
            sfenAfter(mainline),
            computeSfenAtStep(startSfen, controller.studyState.value!!.moves, mainline.size),
        )

        assertTrue(controller.currentTree()!!.hasSameMovesAs(snapshot.initialStudyTree()))
        controller.navigateToMoves(branchPath)
        assertEquals(branchPath, controller.studyState.value?.moves)
        assertTrue(controller.currentTree()!!.hasSameMovesAs(snapshot.initialStudyTree()))
        controller.onStudySquareTapped(ShogiSquare(2, 7))
        controller.onStudySquareTapped(ShogiSquare(2, 6))
        assertEquals(branchPath + "2g2f", controller.studyState.value?.moves)
        assertFalse(controller.currentTree()!!.hasSameMovesAs(snapshot.initialStudyTree()))
        controller.dispose()
    }
    @Test
    fun evaluationAndSelectionAreNotEditsButRemovingReceivedBranchIs() {
        val branch = GameShareBranch(originPly = 2, moves = listOf("5g5f", "8c8d"))
        val baseline = snapshot(selectedPly = 4, branch = branch).initialStudyTree()
        val evaluated = baseline.copy(
            rootEvalState = StudyEvalState.Loading,
            rootChildren = baseline.rootChildren.map { it.copy(evalState = StudyEvalState.Error) },
            selectedChildren = emptyMap(),
        )
        assertTrue(evaluated.hasSameMovesAs(baseline))
        val deleted = baseline.withoutBranch(mainline.take(2) + "5g5f")
        assertFalse(deleted.hasSameMovesAs(baseline))
    }

}
