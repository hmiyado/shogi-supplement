package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import dev.miyado.shogisupplement.db.GameRecord
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

    @OptIn(ExperimentalRoborazziApi::class)
    private val roborazziOptions = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5),
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
    )

    /**
     * ModalBottomSheetは独自のPopupウィンドウ（別root）で描画されるため、シートが
     * 開いている間は`onRoot()`（root=1件前提）が「2件見つかった」で失敗する。
     * 最後に追加されたroot（＝シートが開いていればシート、閉じていればメイン画面）を
     * 常に取得することで、シート開閉どちらの状態でも同じキャプチャ処理を使えるようにする。
     */
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
        ),
        GameRecord(
            id = 3L,
            fileName = "miyado_game3.kif",
            contentHash = "hash3",
            moveCount = 88L,
            senteName = "miyado",
            goteName = "相手C",
            analyzedAt = 1_778_000_000L,
            rating = 1750L,
            coefVersion = "hao_v1",
            sourcePlace = "wars",
            userSide = "sente",
            gameWinner = "gote",
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
            roborazziOptions = roborazziOptions,
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
            roborazziOptions = roborazziOptions,
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
            roborazziOptions = roborazziOptions,
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
            roborazziOptions = roborazziOptions,
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
            roborazziOptions = roborazziOptions,
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
            roborazziOptions = roborazziOptions,
        )
    }
}
