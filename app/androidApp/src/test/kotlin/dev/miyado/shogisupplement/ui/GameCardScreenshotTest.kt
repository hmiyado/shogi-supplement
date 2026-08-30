package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.ui.common.GameCard
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
class GameCardScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun gameCard_withTimeControl() {
        captureRoboImage(
            filePath = "src/test/snapshots/game_card_with_time_control.png",
            roborazziOptions = screenshotRoborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    GameCard(
                        game = GameRecord(
                            id = 1L,
                            fileName = "kiou_game1.kif",
                            contentHash = "hash1",
                            moveCount = 74L,
                            senteName = "miyado",
                            goteName = "相手",
                            analyzedAt = 1_780_000_000L,
                            rating = 1750L,
                            coefVersion = "hao_v1",
                            sourcePlace = "kiou",
                            timeControlRaw = "5分+5秒追加",
                        ),
                        onClick = {},
                    )
                }
            }
        }
    }

    @Test
    fun gameCard_selectableUnselected() {
        captureRoboImage(
            filePath = "src/test/snapshots/game_card_selectable_unselected.png",
            roborazziOptions = screenshotRoborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    GameCard(
                        game = GameRecord(
                            id = 1L,
                            fileName = "kiou_game1.kif",
                            contentHash = "hash1",
                            moveCount = 74L,
                            senteName = "miyado",
                            goteName = "相手",
                            analyzedAt = 1_780_000_000L,
                            rating = 1750L,
                            coefVersion = "hao_v1",
                            sourcePlace = "kiou",
                            timeControlRaw = "5分+5秒追加",
                        ),
                        onClick = {},
                        selectable = true,
                    )
                }
            }
        }
    }

    @Test
    fun gameCard_selectableSelected() {
        captureRoboImage(
            filePath = "src/test/snapshots/game_card_selectable_selected.png",
            roborazziOptions = screenshotRoborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    GameCard(
                        game = GameRecord(
                            id = 1L,
                            fileName = "kiou_game1.kif",
                            contentHash = "hash1",
                            moveCount = 74L,
                            senteName = "miyado",
                            goteName = "相手",
                            analyzedAt = 1_780_000_000L,
                            rating = 1750L,
                            coefVersion = "hao_v1",
                            sourcePlace = "kiou",
                            timeControlRaw = "5分+5秒追加",
                        ),
                        onClick = {},
                        selectable = true,
                        selected = true,
                    )
                }
            }
        }
    }
}
