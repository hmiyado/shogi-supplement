package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.miyado.shogisupplement.ui.common.DeleteGameConfirmDialog
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
class GameDeleteDialogScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(ExperimentalRoborazziApi::class)
    private val roborazziOptions = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5),
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
    )

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun game_deleteConfirmDialog() {
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    DeleteGameConfirmDialog(
                        show = true,
                        canDeleteServer = false,
                        onConfirm = { _, _ -> },
                        onDismiss = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(
            filePath = "src/test/snapshots/game_delete_dialog.png",
            roborazziOptions = roborazziOptions,
        )
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun game_deleteConfirmDialog_withServerCheckbox() {
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    DeleteGameConfirmDialog(
                        show = true,
                        canDeleteServer = true,
                        onConfirm = { _, _ -> },
                        onDismiss = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(
            filePath = "src/test/snapshots/game_delete_dialog_with_server_checkbox.png",
            roborazziOptions = roborazziOptions,
        )
    }
}
