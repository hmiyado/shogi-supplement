package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import dev.miyado.shogisupplement.board.*
import dev.miyado.shogisupplement.engine.StudyEngine
import dev.miyado.shogisupplement.engine.PvInfo
import dev.miyado.shogisupplement.repertoire.*
import dev.miyado.shogisupplement.ui.repertoire.InitialPositionEditorScreen
import dev.miyado.shogisupplement.ui.repertoire.RepertoireScreen
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xxhdpi", application = android.app.Application::class)
class RepertoireScreenScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun unmatchedLabelRemainsVisibleAndDisabled() {
        rule.setContent { ShogiTheme { Surface {
            dev.miyado.shogisupplement.ui.gamelist.GameListFilterBar(
                allGames = emptyList(), knownPositionLabels = listOf("終盤の課題"),
                filter = dev.miyado.shogisupplement.db.GameListFilter(), onFilterChange = {})
        } } }
        rule.onNodeWithTag("filter_position_label_終盤の課題").assertIsNotEnabled()
    }

    @Test fun dragBoardToHandAndBackOnFlippedBoard() {
        var applied: String? = null
        rule.setContent { ShogiTheme { Surface { InitialPositionEditorScreen(ShogiBoard().toSfen(), false, {}, { applied = it }) } } }
        rule.onNodeWithText("反転").performClick()
        val source = rule.onNodeWithTag("board_sq_8_8").fetchSemanticsNode().boundsInRoot.center
        val hand = rule.onNodeWithTag("hand_region_gote").fetchSemanticsNode().boundsInRoot.center
        rule.onRoot().performTouchInput { swipe(source, hand, 600) }
        rule.onNodeWithTag("hand_piece_gote_B", useUnmergedTree = true).assertExists()
        val held = rule.onNodeWithTag("hand_piece_gote_B", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
        val target = rule.onNodeWithTag("board_sq_5_5").fetchSemanticsNode().boundsInRoot.center
        rule.onRoot().performTouchInput { swipe(held, target, 600) }
        rule.onNodeWithText("この盤面で開始").performClick()
        rule.runOnIdle {
            val board = ShogiBoard.fromSfen(requireNotNull(applied))
            assertNull(board.pieceAt(ShogiSquare(8, 8)))
            assertEquals(ShogiPiece(PieceType.BISHOP, Side.WHITE), board.pieceAt(ShogiSquare(5, 5)))
            assertTrue(board.getHand(Side.WHITE).isEmpty())
        }
    }

    @Test fun labelsShowWholeTreeAndRequireConfirmationToRemove() {
        val repo = Repository()
        val sfen = ShogiBoard().toSfen()
        repo.addPositionLabel("owner", sfen, PositionLabelScope.ALL, "初期局面")
        val later = ShogiBoard().also { it.push(ShogiMove.fromUsi("7g7f")) }.toSfen()
        repo.addPositionLabel("owner", later, PositionLabelScope.BLACK, "角道")
        val doc = RepertoireDocument("定跡", sfen, listOf(RepertoireLine("7g7f")))
        rule.setContent { ShogiTheme { Surface(Modifier.fillMaxSize()) {
            dev.miyado.shogisupplement.ui.repertoire.PositionLabelEditor(repo, "owner", sfen, {}, document = doc)
        } } }
        rule.onNodeWithText("先手：角道").assertIsDisplayed()
        rule.onNodeWithText("編集").assertDoesNotExist()
        rule.onRoot().captureRoboImage(filePath = "src/test/snapshots/repertoire_labels.png", roborazziOptions = screenshotRoborazziOptions)
        rule.onNodeWithContentDescription("先手：角道 を削除").performClick()
        rule.onNodeWithText("キャンセル").performClick()
        rule.onNodeWithText("先手：角道").assertIsDisplayed()
        rule.onNodeWithContentDescription("先手：角道 を削除").performClick()
        rule.onNodeWithText("削除する").performClick()
        rule.onNodeWithText("先手：角道").assertDoesNotExist()
        rule.onNodeWithText("局面ラベルを付ける").performClick()
        rule.onNodeWithText("ラベル名").performTextInput("自分の形")
        rule.onNodeWithText("追加", useUnmergedTree = true).performClick()
        rule.onAllNodesWithText("先手：自分の形").onFirst().assertIsDisplayed()
    }

    @Test fun gameCardShowsPositionLabels() {
        val game = dev.miyado.shogisupplement.db.GameRecord(id = 1, fileName = "サンプル棋譜", contentHash = "one", moveCount = 10,
            senteName = null, goteName = null, analyzedAt = 0, rating = 0, coefVersion = "", positionLabels = setOf("角道", "居飛車"))
        rule.setContent { ShogiTheme { Surface { dev.miyado.shogisupplement.ui.common.GameCard(game, {}) } } }
        rule.onNodeWithText("角道").assertIsDisplayed()
        rule.onNodeWithText("居飛車").assertIsDisplayed()
        rule.onRoot().captureRoboImage(filePath = "src/test/snapshots/game_card_position_labels.png", roborazziOptions = screenshotRoborazziOptions)
    }

    @Test fun homeRepertoireEntry() {
        rule.setContent { ShogiTheme { Surface {
            dev.miyado.shogisupplement.ui.home.HomeScreen(emptyList(), onOpenKif = {}, onGameClick = {}, onOpenRepertoire = {})
        } } }
        rule.onRoot().captureRoboImage(filePath = "src/test/snapshots/home_repertoire_entry.png", roborazziOptions = screenshotRoborazziOptions)
    }

    @Test fun initialEditor() {
        rule.setContent { ShogiTheme { Surface { InitialPositionEditorScreen(ShogiBoard().toSfen(), false, {}, {}) } } }
        rule.onRoot().captureRoboImage(filePath = "src/test/snapshots/repertoire_editor.png", roborazziOptions = screenshotRoborazziOptions)
    }

    @Test fun selectedPieceMovesToHandByTappingRegionAndApplyConfirmsClearing() {
        var applied: String? = null
        rule.setContent { ShogiTheme { Surface { InitialPositionEditorScreen(ShogiBoard().toSfen(), true, {}, { applied = it }) } } }
        rule.onNodeWithTag("board_sq_7_7").performClick()
        rule.onNodeWithTag("hand_region_gote").performClick()
        rule.onNodeWithTag("hand_piece_gote_P", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("この盤面で開始").performClick()
        rule.onNodeWithText("現在の手順を消して、この盤面から開始します。").assertIsDisplayed()
        assertNull(applied)
        rule.onAllNodesWithText("この盤面で開始").onLast().performClick()
        rule.runOnIdle {
            val board = ShogiBoard.fromSfen(requireNotNull(applied))
            assertNull(board.pieceAt(ShogiSquare(7, 7)))
            assertEquals(1, board.getHand(Side.WHITE)[PieceType.PAWN])
        }
    }

    @Test fun pieceBoxContainsOnlyRemovedPiecesAndReturnsThemToBoard() {
        var applied: String? = null
        rule.setContent { ShogiTheme { Surface { InitialPositionEditorScreen(ShogiBoard().toSfen(), false, {}, { applied = it }) } } }
        rule.onNodeWithTag("board_sq_8_8").performClick()
        rule.onNodeWithTag("editor_piece_box").performClick()
        rule.onNodeWithTag("editor_piece_box").onChildren().filter(hasText("角")).onFirst().performClick()
        rule.onNodeWithTag("board_sq_5_5").performClick()
        rule.onNodeWithText("この盤面で開始").performClick()
        rule.runOnIdle {
            val board = ShogiBoard.fromSfen(requireNotNull(applied))
            assertNull(board.pieceAt(ShogiSquare(8, 8)))
            assertEquals(ShogiPiece(PieceType.BISHOP, Side.BLACK), board.pieceAt(ShogiSquare(5, 5)))
        }
    }

    @Test fun repertoireListAndStudyUseSharedBoardAndStudyPanel() {
        val repo = Repository()
        repo.save("owner", "one", "line", RepertoireCodec.encode(RepertoireDocument("居飛車の出だし", ShogiBoard().toSfen(),
            listOf(RepertoireLine("7g7f", listOf(RepertoireLine("3c3d")))))))
        repo.save("owner", "shape", "labels", RepertoireCodec.encode(PositionLabels(
            positionLabelKey(ShogiBoard().toSfen(), PositionLabelScope.WHITE), listOf("平手の形"))))
        rule.setContent { ShogiTheme { Surface { RepertoireScreen(repo, "owner", null, { Engine() }, {}) } } }
        rule.onNodeWithText("後手：平手の形").assertIsDisplayed()
        rule.onRoot().captureRoboImage(filePath = "src/test/snapshots/repertoire_list.png", roborazziOptions = screenshotRoborazziOptions)
        rule.onNodeWithText("居飛車の出だし").performClick()
        rule.onRoot().captureRoboImage(filePath = "src/test/snapshots/repertoire_study.png", roborazziOptions = screenshotRoborazziOptions)
        rule.onNode(hasText("検討") and hasClickAction()).performClick()
        rule.onNodeWithContentDescription("1手進む").performClick()
        rule.onNodeWithContentDescription("1手進む").performClick()
        rule.onNodeWithTag("board_sq_2_7").performClick()
        rule.onNodeWithTag("board_sq_2_6").performClick()
        rule.waitForIdle()
        rule.runOnIdle {
            val doc = RepertoireCodec.document(repo.entries("owner").single { it.kind == "line" }.payload)
            assertEquals("2g2f", doc.lines.single().children.single().children.single().move)
        }
    }

    @Test fun reportPositionActionsSaveSharedLabel() {
        val repo = Repository()
        val game = dev.miyado.shogisupplement.db.GameRecord(
            id = 1, fileName = "棋譜", contentHash = "one", moveCount = 2, senteName = null, goteName = null,
            analyzedAt = 0, rating = 0, coefVersion = "", movesUsi = listOf("7g7f", "3c3d"),
        )
        rule.setContent { ShogiTheme { Surface {
            dev.miyado.shogisupplement.ui.report.ReportScreen(game, emptyList(), onBack = {}, initialPlyIndex = 1,
                positionActions = { base, moves ->
                    dev.miyado.shogisupplement.ui.repertoire.RepertoirePositionActions(repo, "owner", null, base, moves)
                })
        } } }
        rule.onNodeWithText("操作").performClick()
        rule.onNodeWithText("局面ラベルを付ける").performClick()
        rule.onNodeWithText("先手の形").performClick()
        rule.onNodeWithText("ラベル名").performTextInput("角道")
        rule.onNodeWithText("追加").performClick()
        rule.runOnIdle {
            val labels = RepertoireCodec.labels(repo.entries("owner").single { it.kind == "labels" }.payload)
            val board = ShogiBoard().also { it.push(ShogiMove.fromUsi("7g7f")) }
            assertEquals(positionLabelKey(board.toSfen(), PositionLabelScope.BLACK), labels.positionKey)
            assertEquals(listOf("角道"), labels.labels)
        }
    }

    private class Engine : StudyEngine {
        override suspend fun analyzeSfen(sfen: String, additionalMoves: List<String>, nodes: Int, multiPv: Int): List<PvInfo> = emptyList()
        override fun quit() {}
    }
    private class Repository : RepertoireRepository {
        private val values = mutableMapOf<String, RepertoireEntry>()
        override fun entries(owner: String) = values.values.toList()
        override fun save(owner: String, id: String, kind: String, payload: String) { values[id] = RepertoireEntry(id, kind, payload, 1, null, true) }
        override fun acknowledge(owner: String, id: String, revision: Long, remoteVersion: String) {}
        override fun acceptRemote(owner: String, id: String, kind: String, payload: String, remoteVersion: String) {}
    }
}
