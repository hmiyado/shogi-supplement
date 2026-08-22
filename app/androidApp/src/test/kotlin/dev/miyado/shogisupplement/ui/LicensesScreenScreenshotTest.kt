package dev.miyado.shogisupplement.ui

import dev.miyado.shogisupplement.ui.license.LicenseInfoHeader
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

    @Test
    fun licenses_screen() {
        captureRoboImage(
            filePath = "src/test/snapshots/licenses_screen.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    LicensesScreen(onBack = {}, onOpenSourceRepo = {})
                }
            }
        }
    }

    @Test
    fun licenses_header() {
        captureRoboImage(
            filePath = "src/test/snapshots/licenses_header.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    LicenseInfoHeader()
                }
            }
        }
    }
}
