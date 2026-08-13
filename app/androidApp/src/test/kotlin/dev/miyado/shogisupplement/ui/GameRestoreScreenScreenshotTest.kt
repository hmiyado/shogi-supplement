package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.restore.GameRestoreScreen
import dev.miyado.shogisupplement.ui.restore.GameRestoreUiState
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 件数確認から部分失敗まで、復元画面の全状態を保証するVRT。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w400dp-h800dp-xxhdpi",
)
class GameRestoreScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(ExperimentalRoborazziApi::class)
    private val roborazziOptions = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5),
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
    )

    private fun capture(fileName: String, state: GameRestoreUiState, dark: Boolean = false) {
        composeRule.setContent {
            ShogiTheme(themeMode = if (dark) "dark" else "light") {
                Surface {
                    GameRestoreScreen(state = state)
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/snapshots/$fileName.png",
            roborazziOptions = roborazziOptions,
        )
    }

    @Test
    fun game_restore_loading() = capture("game_restore_loading", GameRestoreUiState.Loading)

    @Test
    fun game_restore_ready() = capture("game_restore_ready", GameRestoreUiState.Ready(count = 12))

    @Test
    fun game_restore_ready_empty() = capture("game_restore_ready_empty", GameRestoreUiState.Ready(count = 0))

    @Test
    fun game_restore_downloading() =
        capture("game_restore_downloading", GameRestoreUiState.Downloading(done = 3, total = 12))

    @Test
    fun game_restore_completed_all() =
        capture("game_restore_completed_all", GameRestoreUiState.Completed(total = 12, succeeded = 12, failed = 0))

    @Test
    fun game_restore_completed_partial() =
        capture(
            "game_restore_completed_partial",
            GameRestoreUiState.Completed(total = 12, succeeded = 10, failed = 2),
        )

    @Test
    fun game_restore_error() =
        capture("game_restore_error", GameRestoreUiState.Error(AppStrings.GAME_RESTORE_ERROR_NETWORK))

    @Test
    fun game_restore_ready_dark() =
        capture("game_restore_ready_dark", GameRestoreUiState.Ready(count = 12), dark = true)
}
