package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Surface
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import dev.miyado.shogisupplement.ui.forceupdate.ForceUpdateScreen
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 強制アップデート画面の VRT。
 * store_url空・message有無の分岐を検証する。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w400dp-h800dp-xxhdpi",
    application = android.app.Application::class,
)
class ForceUpdateScreenScreenshotTest {

    @OptIn(ExperimentalRoborazziApi::class)
    private val roborazziOptions = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5),
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
    )

    @Test
    fun force_update_default() {
        captureRoboImage(
            filePath = "src/test/snapshots/force_update_default.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    ForceUpdateScreen(
                        message = null,
                        storeUrl = "https://play.google.com/store/apps/details?id=dev.miyado.shogisupplement",
                        versionName = "1.2.0",
                        buildNumber = 42,
                    )
                }
            }
        }
    }

    /** store_url未設定: ボタンを出さず案内文のみになることを確認する golden。 */
    @Test
    fun force_update_no_store_url() {
        captureRoboImage(
            filePath = "src/test/snapshots/force_update_no_store_url.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    ForceUpdateScreen(
                        message = null,
                        storeUrl = null,
                        versionName = "1.2.0",
                        buildNumber = 42,
                    )
                }
            }
        }
    }

    /** message設定時: 本文の下に追記表示されることを確認する golden。 */
    @Test
    fun force_update_with_message() {
        captureRoboImage(
            filePath = "src/test/snapshots/force_update_with_message.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    ForceUpdateScreen(
                        message = "本日3時よりメンテナンスを予定しています。",
                        storeUrl = "https://play.google.com/store/apps/details?id=dev.miyado.shogisupplement",
                        versionName = "1.2.0",
                        buildNumber = 42,
                    )
                }
            }
        }
    }

    @Test
    fun force_update_dark() {
        captureRoboImage(
            filePath = "src/test/snapshots/force_update_dark.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme(themeMode = "dark") {
                Surface {
                    ForceUpdateScreen(
                        message = null,
                        storeUrl = "https://play.google.com/store/apps/details?id=dev.miyado.shogisupplement",
                        versionName = "1.2.0",
                        buildNumber = 42,
                    )
                }
            }
        }
    }
}
