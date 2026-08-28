package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Surface
import com.github.takahirom.roborazzi.captureRoboImage
import dev.miyado.shogisupplement.ui.consent.ConsentScreen
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** はじめに（同意オンボーディング）の VRT。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w400dp-h1000dp-xxhdpi",
)
class ConsentScreenScreenshotTest {

    @Test
    fun consent_default() {
        captureRoboImage(
            filePath = "src/test/snapshots/consent_default.png",
            roborazziOptions = screenshotRoborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    ConsentScreen()
                }
            }
        }
    }
}
