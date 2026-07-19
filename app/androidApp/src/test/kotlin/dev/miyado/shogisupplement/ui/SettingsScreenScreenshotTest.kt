package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Surface
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import dev.miyado.shogisupplement.ui.settings.SettingsScreen
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 設定画面の VRT。
 * セクション見出し・行レイアウト・バージョン表示を検証する。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w400dp-h800dp-xxhdpi",
    application = android.app.Application::class,
)
class SettingsScreenScreenshotTest {

    @OptIn(ExperimentalRoborazziApi::class)
    private val roborazziOptions = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5),
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
    )

    @Test
    fun settings_default() {
        captureRoboImage(
            filePath = "src/test/snapshots/settings_default.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    SettingsScreen(
                        versionName = "0.1.0",
                        themeMode = "system",
                        onBack = {},
                        onOpenRatingSettings = {},
                        onOpenAccount = {},
                        onThemeChange = {},
                        onOpenHelp = {},
                        onOpenFeedback = {},
                        onOpenTerms = {},
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
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme(themeMode = "dark") {
                Surface {
                    SettingsScreen(
                        versionName = "0.1.0",
                        themeMode = "dark",
                        onBack = {},
                        onOpenRatingSettings = {},
                        onOpenAccount = {},
                        onThemeChange = {},
                        onOpenHelp = {},
                        onOpenFeedback = {},
                        onOpenTerms = {},
                        onOpenLicenses = {},
                    )
                }
            }
        }
    }

    /**
     * デバッグセクション（DEBUGビルド相当・onOpenDebug 非null）に
     * 「駒台を左右に表示（実験）」トグルが追加されたことを確認する golden。
     * デバッグセクションは設定画面の最下部（スクロール末尾）にあるため、
     * クラス既定の h800dp では画面外に隠れる。このテストだけ縦に長い
     * qualifiers を使い、スクロールせずに全セクションが写るようにする。
     */
    @Config(
        sdk = [34],
        qualifiers = "w400dp-h1400dp-xxhdpi",
        application = android.app.Application::class,
    )
    @Test
    fun settings_debug_section() {
        captureRoboImage(
            filePath = "src/test/snapshots/settings_debug_section.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    SettingsScreen(
                        versionName = "0.1.0",
                        themeMode = "system",
                        onBack = {},
                        onOpenRatingSettings = {},
                        onOpenAccount = {},
                        onThemeChange = {},
                        onOpenHelp = {},
                        onOpenFeedback = {},
                        onOpenTerms = {},
                        onOpenLicenses = {},
                        onOpenDebug = {},
                    )
                }
            }
        }
    }
}
