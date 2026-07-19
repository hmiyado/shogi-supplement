package dev.miyado.shogisupplement.ui

import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import androidx.compose.material3.Surface
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.account.AccountNotProvidingContent
import dev.miyado.shogisupplement.ui.account.AccountProvidingContent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * アカウント（棋譜提供）画面の VRT（スクリーンショットテスト）。
 *
 * 4 状態をゴールデン画像として記録する:
 *   1. 未提供（データ提供していない）
 *   2. 提供中（自動アップロード OFF）
 *   3. 提供中（自動アップロード ON）
 *   4. エラー表示（サインイン失敗など）
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w400dp-h800dp-xxhdpi",
    application = android.app.Application::class,
)
class AccountScreenScreenshotTest {

    @OptIn(ExperimentalRoborazziApi::class)
    private val roborazziOptions = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5),
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
    )

    /** 未提供: 初期状態（エラーなし）。 */
    @Test
    fun account_notLoggedIn() {
        captureRoboImage(
            filePath = "src/test/snapshots/account_not_logged_in.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    AccountNotProvidingContent()
                }
            }
        }
    }

    /** 提供中: 自動アップロード OFF。 */
    @Test
    fun account_loggedIn() {
        captureRoboImage(
            filePath = "src/test/snapshots/account_logged_in.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    AccountProvidingContent(
                        uploadedCount = 12,
                        autoUpload = false,
                    )
                }
            }
        }
    }

    /** 提供中: 自動アップロード ON。 */
    @Test
    fun account_loggedIn_autoUploadOn() {
        captureRoboImage(
            filePath = "src/test/snapshots/account_logged_in_auto_upload_on.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    AccountProvidingContent(
                        uploadedCount = 12,
                        autoUpload = true,
                    )
                }
            }
        }
    }

    /** エラー表示: 匿名サインイン失敗（汎用エラー）。 */
    @Test
    fun account_error() {
        captureRoboImage(
            filePath = "src/test/snapshots/account_error.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    AccountNotProvidingContent(
                        error = AppStrings.AUTH_ERROR_ANON_SIGN_IN_GENERIC,
                    )
                }
            }
        }
    }
}
