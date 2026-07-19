package dev.miyado.shogisupplement.ui

import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import androidx.compose.material3.Surface
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * OSSライセンス一覧画面の VRT（スクリーンショットテスト）。
 *
 * 1. 画面全体: 手書きヘッダ（本アプリGPLv3・やねうら王・Háo）＋AboutLibraries一覧の冒頭。
 *    一覧データは res/raw/aboutlibraries.json（コミット済み）から同期読み込みされるため
 *    決定的に描画できる。依存を更新して JSON を再生成した場合は golden も更新すること。
 * 2. ヘッダ単体: GPLv3表記の手書き部分。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w400dp-h800dp-xxhdpi",
    application = android.app.Application::class,
)
class LicensesScreenScreenshotTest {

    @OptIn(ExperimentalRoborazziApi::class)
    private val roborazziOptions = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5),
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
    )

    /** ライセンス画面全体（TopAppBar + 手書きヘッダ + 一覧冒頭）。 */
    @Test
    fun licenses_screen() {
        captureRoboImage(
            filePath = "src/test/snapshots/licenses_screen.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    LicensesScreen(onBack = {})
                }
            }
        }
    }

    /** 手書きヘッダ単体（GPLv3表記）。 */
    @Test
    fun licenses_header() {
        captureRoboImage(
            filePath = "src/test/snapshots/licenses_header.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    LicensesHeader()
                }
            }
        }
    }
}
