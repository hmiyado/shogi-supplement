package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
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

/**
 * 棋譜一覧画面（絞り込み機能）の VRT（スクリーンショットテスト）。
 *
 * 検証観点:
 *   ①フィルタバー（出典・先後・勝敗・期間の各軸チップ）の初期表示
 *   ②チップ選択時の強調表示・件数表示の切り替え（N件 → M / N件）・解除ボタンの出現
 *   ③絞り込み結果0件でもクラッシュせずリストが空になる
 *   ④出典・先後・勝敗のデータが1件も無いゲーム群では該当軸のチップ自体を作らない
 *     （期間軸のみ表示される）
 *
 * フィルタ状態は GameListScreen 内部の remember state のため、テストからは
 * チップを直接タップできない代わりに、フィルタ選択済み画面を screenshotForOptions で
 * 再現するのではなく、実際にタップ操作を行った上でキャプチャする
 * （Roborazzi は Compose の状態変化後の再コンポジションをそのままキャプチャできるため）。
 */
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

    /** 初期表示: フィルタ未選択。出典・先後・勝敗・期間の全軸が表示され、件数は「N件」表記。 */
    @Test
    fun gameList_noFilterActive() {
        captureRoboImage(
            filePath = "src/test/snapshots/game_list_no_filter.png",
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

    /** 出典・先後・勝敗のデータが無いゲーム群: 期間軸のみ表示され、他軸のチップは作られない。 */
    @Test
    fun gameList_onlyPeriodAxisWhenNoOtherData() {
        captureRoboImage(
            filePath = "src/test/snapshots/game_list_only_period_axis.png",
            roborazziOptions = roborazziOptions,
        ) {
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
    }

    /** ゲーム0件: フィルタバー自体を表示しない（データが無い状態でチップを出さない）。 */
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

    /**
     * 出典チップ「将棋ウォーズ」をタップした後: チップが強調表示され、件数が
     * 「M / N件」表記に切り替わり、「絞り込みを解除」ボタンが現れ、非該当のゲームカードが消える。
     * フィルタ状態は GameListScreen 内部の remember state のため、composeRule でタップしてから
     * 同じコンポジションをキャプチャする（他の golden とは異なり performClick を経由する）。
     */
    @Test
    fun gameList_filterActive_sourceSelected() {
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
        composeRule.onNodeWithTag("filter_chip_source_wars").performClick()

        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/snapshots/game_list_filter_active.png",
            roborazziOptions = roborazziOptions,
        )
    }
}
