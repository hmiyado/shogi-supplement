package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.db.GameAnalysisStatus
import dev.miyado.shogisupplement.ui.gamelist.GameListScreen
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w400dp-h800dp-xxhdpi",
    application = android.app.Application::class,
)
class GameListScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Popupを含む場合も最後に追加された表示rootを取得する。 */
    private fun topRoot(): SemanticsNodeInteraction {
        val allRoots = composeRule.onAllNodes(isRoot())
        val count = allRoots.fetchSemanticsNodes().size
        return allRoots[count - 1]
    }

    private fun gamesWithFullData() = listOf(
        GameRecord(
            id = 1L,
            fileName = "miyado_game1.kif",
            contentHash = "hash1",
            moveCount = 74L,
            senteName = "miyado",
            goteName = "相手A",
            analyzedAt = 1_780_000_000L,
            rating = 1750L,
            coefVersion = "hao_v1",
            sourcePlace = "wars",
            userSide = "sente",
            gameWinner = "sente",
            openingStyle = "角換わり",
            openingCastle = "矢倉",
            openingTags = "角換わり|棒銀",
            timeControlRaw = "0分",
            timeControlByoyomiRaw = "30秒",
        ),
        GameRecord(
            id = 2L,
            fileName = "miyado_game2.kif",
            contentHash = "hash2",
            moveCount = 50L,
            senteName = "相手B",
            goteName = "miyado",
            analyzedAt = 1_779_000_000L,
            rating = 1750L,
            coefVersion = "hao_v1",
            sourcePlace = "lishogi",
            userSide = "gote",
            gameWinner = "sente",
            openingStyle = "四間飛車",
            openingCastle = "本美濃囲い",
            openingTags = "四間飛車",
            timeControlRaw = "10分+30秒",
        ),
        GameRecord(
            id = 3L,
            fileName = "miyado_game3.kif",
            contentHash = "hash3",
            moveCount = 88L,
            senteName = "相手A",
            goteName = "miyado",
            analyzedAt = 1_778_000_000L,
            rating = 1750L,
            coefVersion = "hao_v1",
            sourcePlace = "shogi_quest",
            userSide = "gote",
            gameWinner = "gote",
            senteRating = 464L,
            goteRating = 800L,
            timeControlRaw = "5分+30秒",
        ),
    )

    private fun gamesWithoutSourceOrSideData() = listOf(
        GameRecord(
            id = 1L,
            fileName = "legacy_game1.kif",
            contentHash = "hash1",
            moveCount = 60L,
            senteName = null,
            goteName = null,
            analyzedAt = 1_780_000_000L,
            rating = 1750L,
            coefVersion = "hao_v1",
        ),
    )

    @Test
    fun gameList_headerNoFilter() {
        captureRoboImage(
            filePath = "src/test/snapshots/game_list_header_no_filter.png",
            roborazziOptions = screenshotRoborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    GameListScreen(
                        games = gamesWithFullData(),
                        onBack = {},
                        onGameClick = {},
                    )
                }
            }
        }
    }

    @Test
    fun gameList_emptyGames() {
        captureRoboImage(
            filePath = "src/test/snapshots/game_list_empty.png",
            roborazziOptions = screenshotRoborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    GameListScreen(
                        games = emptyList(),
                        onBack = {},
                        onGameClick = {},
                    )
                }
            }
        }
    }

    @Test
    fun gameList_pendingAnalysis() {
        val pending = gamesWithFullData().first().copy(
            rating = 0,
            coefVersion = "",
            analysisStatus = GameAnalysisStatus.PENDING,
        )
        captureRoboImage(
            filePath = "src/test/snapshots/game_list_pending_analysis.png",
            roborazziOptions = screenshotRoborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    GameListScreen(
                        games = listOf(pending),
                        onBack = {},
                        onGameClick = {},
                    )
                }
            }
        }
    }

    @Test
    fun gameList_filterSheetOpen() {
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    GameListScreen(
                        games = gamesWithFullData(),
                        onBack = {},
                        onGameClick = {},
                    )
                }
            }
        }
        composeRule.onNodeWithTag("filter_open_button").performClick()

        topRoot().captureRoboImage(
            filePath = "src/test/snapshots/game_list_filter_sheet_open.png",
            roborazziOptions = screenshotRoborazziOptions,
        )
    }

    @Test
    fun gameList_filterSheetChipSelected() {
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    GameListScreen(
                        games = gamesWithFullData(),
                        onBack = {},
                        onGameClick = {},
                    )
                }
            }
        }
        composeRule.onNodeWithTag("filter_open_button").performClick()
        composeRule.onNodeWithTag("filter_chip_source_wars").performClick()

        topRoot().captureRoboImage(
            filePath = "src/test/snapshots/game_list_filter_sheet_chip_selected.png",
            roborazziOptions = screenshotRoborazziOptions,
        )
    }

    @Test
    fun gameList_filterApplied() {
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    GameListScreen(
                        games = gamesWithFullData(),
                        onBack = {},
                        onGameClick = {},
                    )
                }
            }
        }
        composeRule.onNodeWithTag("filter_open_button").performClick()
        composeRule.onNodeWithTag("filter_chip_source_wars").performClick()
        composeRule.onNodeWithTag("filter_apply_button").performClick()

        topRoot().captureRoboImage(
            filePath = "src/test/snapshots/game_list_filter_applied.png",
            roborazziOptions = screenshotRoborazziOptions,
        )
    }

    @Test
    fun gameList_filterSheetOnlyPeriodAxisWhenNoOtherData() {
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    GameListScreen(
                        games = gamesWithoutSourceOrSideData(),
                        onBack = {},
                        onGameClick = {},
                    )
                }
            }
        }
        composeRule.onNodeWithTag("filter_open_button").performClick()

        topRoot().captureRoboImage(
            filePath = "src/test/snapshots/game_list_filter_sheet_only_period_axis.png",
            roborazziOptions = screenshotRoborazziOptions,
        )
    }

    private fun gamesWithLabeledTimeControl() = listOf(
        GameRecord(
            id = 1L,
            fileName = "kiou_game1.kif",
            contentHash = "hash1",
            moveCount = 74L,
            senteName = "miyado",
            goteName = "相手A",
            analyzedAt = 1_780_000_000L,
            rating = 1750L,
            coefVersion = "hao_v1",
            sourcePlace = "kiou",
            userSide = "sente",
            gameWinner = "sente",
            timeControlRaw = "3分切れ負け",
        ),
    )

    /** 棋桜のラベル付き持ち時間（「ショート（3分切れ負け）」）がチップでもmono数値で出ることを保証する。 */
    @Test
    fun gameList_filterSheetTimeControlSelected() {
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    GameListScreen(
                        games = gamesWithLabeledTimeControl(),
                        onBack = {},
                        onGameClick = {},
                    )
                }
            }
        }
        composeRule.onNodeWithTag("filter_open_button").performClick()
        composeRule.onNodeWithTag("filter_chip_time_control_ショート（3分切れ負け）").performClick()

        topRoot().captureRoboImage(
            filePath = "src/test/snapshots/game_list_filter_sheet_time_control_selected.png",
            roborazziOptions = screenshotRoborazziOptions,
        )
    }

    private fun gamesWithManyTimeControls() = List(10) { i ->
        GameRecord(
            id = i + 1L,
            fileName = "lishogi_game$i.kif",
            contentHash = "hash$i",
            moveCount = 60L,
            senteName = "miyado",
            goteName = "相手A",
            analyzedAt = 1_780_000_000L - i * 1_000L,
            rating = 1750L,
            coefVersion = "hao_v1",
            sourcePlace = "lishogi",
            userSide = "sente",
            gameWinner = "sente",
            timeControlRaw = "${i + 1}分+${i + 1}秒",
        )
    }

    /** 軸が伸びても「検索」「絞り込みを解除」がシートの外へ押し出されないことを保証する。 */
    @Test
    fun gameList_filterSheetManyTimeControlsKeepsActionsVisible() {
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    GameListScreen(
                        games = gamesWithManyTimeControls(),
                        onBack = {},
                        onGameClick = {},
                    )
                }
            }
        }
        composeRule.onNodeWithTag("filter_open_button").performClick()
        composeRule.onNodeWithTag("filter_apply_button").assertIsDisplayed()
        composeRule.onNodeWithTag("filter_clear_button").assertIsDisplayed()

        topRoot().captureRoboImage(
            filePath = "src/test/snapshots/game_list_filter_sheet_many_time_controls.png",
            roborazziOptions = screenshotRoborazziOptions,
        )
    }

    /** 出典を選ぶと、その出典に無い持ち時間のチップが無効になり、選択済みなら外れることを保証する。 */
    @Test
    fun gameList_filterSheetSourceDisablesOtherTimeControls() {
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    GameListScreen(
                        games = gamesWithFullData(),
                        onBack = {},
                        onGameClick = {},
                    )
                }
            }
        }
        composeRule.onNodeWithTag("filter_open_button").performClick()
        composeRule.onNodeWithTag("filter_chip_time_control_10分秒読み30秒").performClick()
        composeRule.onNodeWithTag("filter_chip_source_wars").performClick()

        topRoot().captureRoboImage(
            filePath = "src/test/snapshots/game_list_filter_sheet_source_disables_time_controls.png",
            roborazziOptions = screenshotRoborazziOptions,
        )
    }

    /** 削除アイコンで選択モードへ入り、1件チェックすると見出しと削除アイコンの状態が変わる。 */
    @Test
    fun gameList_selectionModeWithOneChecked() {
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    GameListScreen(
                        games = gamesWithFullData(),
                        onBack = {},
                        onGameClick = {},
                    )
                }
            }
        }
        composeRule.onNodeWithTag("game_list_select_mode_button").performClick()
        composeRule.onNodeWithTag("game_card_checkbox_1").performClick()

        topRoot().captureRoboImage(
            filePath = "src/test/snapshots/game_list_selection_mode_one_checked.png",
            roborazziOptions = screenshotRoborazziOptions,
        )
    }
}
