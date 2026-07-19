package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiSquare
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.report.ReportScreen
import dev.miyado.shogisupplement.ui.report.StudyState
import dev.miyado.shogisupplement.ui.report.buildInitialStudyState
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * レポート画面の検討モード開始タップのインタラクションテスト。
 *
 * 「駒タップ→検討開始と同時にその駒が選択状態になる」を
 * performClick で検証する。状態遷移は MainViewModel.startStudy と同じ
 * buildInitialStudyState（本体コード）を経由させる。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w400dp-h800dp-xxhdpi",
)
class ReportScreenStudyInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleGame() = GameRecord(
        id = 1L,
        fileName = "miyado_game1.kif",
        contentHash = "hash1",
        moveCount = 74L,
        senteName = "miyado",
        goteName = "相手",
        analyzedAt = 1_780_000_000L,
        rating = 1750L,
        coefVersion = "hao_v1",
        movesUsi = listOf("7g7f", "3c3d", "2g2f", "8c8d"),
        userSide = "sente",
    )

    /**
     * ReportScreen を MainActivity と同じ形で状態ホルダーに接続する。
     * onStartStudy は MainViewModel.startStudy の中身（buildInitialStudyState）を呼ぶ。
     */
    private fun setReportScreenContent(getState: () -> StudyState?, setState: (StudyState?) -> Unit) {
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    ReportScreen(
                        game = sampleGame(),
                        reports = emptyList(),
                        flip = false,
                        onBack = {},
                        studyState = getState(),
                        onStartStudy = { baseSfen, flip, bestPv, ply, idx, absPly, sq ->
                            val board = ShogiBoard.fromSfen(baseSfen)
                            setState(
                                buildInitialStudyState(
                                    baseSfen = baseSfen,
                                    flip = flip,
                                    originIsBestPv = bestPv,
                                    originPlyIndex = ply,
                                    originSelectedIdx = idx,
                                    originAbsolutePly = absPly,
                                    tappedSquare = sq,
                                    board = board,
                                ),
                            )
                        },
                    )
                }
            }
        }
    }

    /** 手番側の駒（開始局面の▲７六歩の歩=7g）をタップ→検討開始と同時に選択状態。 */
    @Test
    fun tappingOwnPieceStartsStudyWithImmediateSelection() {
        var studyState by mutableStateOf<StudyState?>(null)
        setReportScreenContent({ studyState }, { studyState = it })

        // 開始局面（ply=0・先手番）。7g（file=7, rank=7）は先手の歩。
        composeRule.onNodeWithTag("board_sq_7_7").performClick()
        composeRule.waitForIdle()

        val s = studyState
        assertNotNull("駒タップで検討モードが開始されること", s)
        assertEquals("開始タップの駒が即選択されること", ShogiSquare(7, 7), s!!.selectedFrom)
        assertTrue(
            "選択駒の合法手（７六）が legalDestinations に入ること",
            ShogiSquare(7, 6) in s.legalDestinations,
        )
        // UI 反映: 検討中バナー（開始局面から・▲番）が表示される
        composeRule.onNodeWithText(AppStrings.studyBanner(0, senteToMove = true))
            .assertIsDisplayed()
    }

    /** 手番でない側の駒（開始局面の△３四歩の歩=3c）をタップ→選択なし＋手番ヒント表示。 */
    @Test
    fun tappingOpponentPieceStartsStudyWithTurnHint() {
        var studyState by mutableStateOf<StudyState?>(null)
        setReportScreenContent({ studyState }, { studyState = it })

        // 開始局面（先手番）。3c（file=3, rank=3）は後手の歩 = 手番でない側。
        composeRule.onNodeWithTag("board_sq_3_3").performClick()
        composeRule.waitForIdle()

        val s = studyState
        assertNotNull("駒タップで検討モードは開始されること", s)
        assertNull("手番でない駒は選択されないこと", s!!.selectedFrom)
        assertTrue("legalDestinations は空のこと", s.legalDestinations.isEmpty())
        assertTrue("手番ヒントのフラグが立つこと", s.showTurnHint)
        // 検討ナビ行のラベルに形勢サフィックスとして
        // 「（▲番です）」を統合表示する（No-jitter・DESIGN.md Layout節）。
        composeRule.onNodeWithText(AppStrings.studyTurnHint(senteToMove = true), substring = true)
            .assertIsDisplayed()
    }
}
