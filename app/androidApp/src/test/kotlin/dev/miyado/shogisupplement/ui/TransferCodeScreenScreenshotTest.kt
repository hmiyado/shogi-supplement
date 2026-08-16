package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import dev.miyado.shogisupplement.ui.transfercode.TransferCodeScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 引き継ぎコード表示のVRT（伏字・生値・作り直し）。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w400dp-h800dp-xxhdpi",
)
class TransferCodeScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(ExperimentalRoborazziApi::class)
    private val roborazziOptions = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5),
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
    )

    private val sampleCode = "8QZKM-2XRTN-P9VCB-H4WLD-A7YFE-J3"

    @Test
    fun transfer_code_masked_default() {
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    TransferCodeScreen(code = sampleCode, onBack = {})
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/snapshots/transfer_code_masked_default.png",
            roborazziOptions = roborazziOptions,
        )
    }

    @Test
    fun transfer_code_regenerate_available() {
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    TransferCodeScreen(code = sampleCode, onBack = {}, onRegenerate = {})
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/snapshots/transfer_code_regenerate_available.png",
            roborazziOptions = roborazziOptions,
        )
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun transfer_code_regenerate_confirm() {
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    TransferCodeScreen(
                        code = sampleCode,
                        onBack = {},
                        onRegenerate = {},
                        showRegenerateDialogInitially = true,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        // ダイアログは別ウィンドウに描画されるため、画面全体で撮る。
        captureScreenRoboImage(
            filePath = "src/test/snapshots/transfer_code_regenerate_confirm.png",
            roborazziOptions = roborazziOptions,
        )
    }

    @Test
    fun transfer_code_revealed() {
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    TransferCodeScreen(code = sampleCode, onBack = {})
                }
            }
        }
        composeRule.onNodeWithTag("transfer_code_reveal_toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/snapshots/transfer_code_revealed.png",
            roborazziOptions = roborazziOptions,
        )
    }

    @Test
    fun transfer_code_masked_dark() {
        composeRule.setContent {
            ShogiTheme(themeMode = "dark") {
                Surface {
                    TransferCodeScreen(code = sampleCode, onBack = {})
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/snapshots/transfer_code_masked_dark.png",
            roborazziOptions = roborazziOptions,
        )
    }
}
