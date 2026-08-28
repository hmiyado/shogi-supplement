package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Surface
import com.github.takahirom.roborazzi.captureRoboImage
import dev.miyado.shogisupplement.ui.forceupdate.ForceUpdateScreen
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
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
class ForceUpdateScreenScreenshotTest {

    @Test
    fun force_update_default() {
        captureRoboImage(
            filePath = "src/test/snapshots/force_update_default.png",
            roborazziOptions = screenshotRoborazziOptions,
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

    @Test
    fun force_update_no_store_url() {
        captureRoboImage(
            filePath = "src/test/snapshots/force_update_no_store_url.png",
            roborazziOptions = screenshotRoborazziOptions,
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

    @Test
    fun force_update_with_message() {
        captureRoboImage(
            filePath = "src/test/snapshots/force_update_with_message.png",
            roborazziOptions = screenshotRoborazziOptions,
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
            roborazziOptions = screenshotRoborazziOptions,
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
