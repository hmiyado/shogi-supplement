package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Surface
import com.github.takahirom.roborazzi.captureRoboImage
import dev.miyado.shogisupplement.ui.settings.SettingsScreen
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 設定画面の VRT。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w400dp-h800dp-xxhdpi",
    application = android.app.Application::class,
)
class SettingsScreenScreenshotTest {

    @Test
    fun settings_default() {
        captureRoboImage(
            filePath = "src/test/snapshots/settings_default.png",
            roborazziOptions = screenshotRoborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    SettingsScreen(
                        versionName = "0.1.0",
                        themeMode = "system",
                        onBack = {},
                        onOpenAccount = {},
                        onThemeChange = {},
                        onOpenHelp = {},
                        onOpenFeedback = {},
                        onOpenTerms = {},
                        onOpenReleaseNotes = {},
                        onOpenLicenses = {},
                    )
                }
            }
        }
    }

    /** アカウントを作らずに使っている端末にだけ出る、あとから作る導線。 */
    @Test
    fun settings_create_account_row() {
        captureRoboImage(
            filePath = "src/test/snapshots/settings_create_account_row.png",
            roborazziOptions = screenshotRoborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    SettingsScreen(
                        versionName = "0.1.0",
                        themeMode = "system",
                        onBack = {},
                        onOpenAccount = {},
                        onCreateAccount = {},
                        onThemeChange = {},
                        onOpenHelp = {},
                        onOpenFeedback = {},
                        onOpenTerms = {},
                        onOpenReleaseNotes = {},
                        onOpenLicenses = {},
                    )
                }
            }
        }
    }

    /** ダーク強制の設定画面 golden。 */
    @Test
    fun settings_dark() {
        captureRoboImage(
            filePath = "src/test/snapshots/settings_dark.png",
            roborazziOptions = screenshotRoborazziOptions,
        ) {
            ShogiTheme(themeMode = "dark") {
                Surface {
                    SettingsScreen(
                        versionName = "0.1.0",
                        themeMode = "dark",
                        onBack = {},
                        onOpenAccount = {},
                        onThemeChange = {},
                        onOpenHelp = {},
                        onOpenFeedback = {},
                        onOpenTerms = {},
                        onOpenReleaseNotes = {},
                        onOpenLicenses = {},
                    )
                }
            }
        }
    }

    /** デバッグセクションの駒台トグル。最下部にあるため縦に長い qualifiers で撮る。 */
    @Config(
        sdk = [34],
        qualifiers = "w400dp-h1400dp-xxhdpi",
        application = android.app.Application::class,
    )
    @Test
    fun settings_debug_section() {
        captureRoboImage(
            filePath = "src/test/snapshots/settings_debug_section.png",
            roborazziOptions = screenshotRoborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    SettingsScreen(
                        versionName = "0.1.0",
                        themeMode = "system",
                        onBack = {},
                        onOpenAccount = {},
                        onThemeChange = {},
                        onOpenHelp = {},
                        onOpenFeedback = {},
                        onOpenTerms = {},
                        onOpenReleaseNotes = {},
                        onOpenLicenses = {},
                        onOpenDebug = {},
                    )
                }
            }
        }
    }
}
