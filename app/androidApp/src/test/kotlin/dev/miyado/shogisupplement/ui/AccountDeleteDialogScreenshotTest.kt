package dev.miyado.shogisupplement.ui

import dev.miyado.shogisupplement.ui.account.AccountProvidingContent
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * データ削除の確認ダイアログを含む提供中画面の VRT。
 * AlertDialog は別ウィンドウに描画されるため、compose 単体キャプチャではなく
 * captureScreenRoboImage（画面全体撮影）を使う。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w400dp-h800dp-xxhdpi",
)
class AccountDeleteDialogScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(ExperimentalRoborazziApi::class)
    private val roborazziOptions = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5),
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
    )

    /** 提供中画面＋削除確認ダイアログ表示状態。 */
    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun account_deleteConfirmDialog() {
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    AccountProvidingContent(
                        uploadedCount = 5,
                        autoUpload = false,
                        onDeleteAccount = {},
                        showDeleteDialogInitially = true,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(
            filePath = "src/test/snapshots/account_delete_dialog.png",
            roborazziOptions = roborazziOptions,
        )
    }
}
