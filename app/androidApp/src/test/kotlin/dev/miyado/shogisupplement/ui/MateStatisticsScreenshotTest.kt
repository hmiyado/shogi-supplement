package dev.miyado.shogisupplement.ui

import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.miyado.shogisupplement.strength.MateBucket
import dev.miyado.shogisupplement.strength.MateStatistics
import dev.miyado.shogisupplement.ui.strength.MateStatisticsCard
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w320dp-h900dp-xxhdpi",
    application = android.app.Application::class,
)
class MateStatisticsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun statistics(vararg buckets: MateBucket) = MateStatistics(
        buckets = buckets.toList(),
        analyzedGames = 30,
        verifiedGames = 22,
    )

    private fun setCard(statistics: MateStatistics, dark: Boolean = false) {
        composeRule.setContent {
            ShogiTheme(themeMode = if (dark) "dark" else "light") {
                Surface(Modifier.width(320.dp)) {
                    MateStatisticsCard(statistics)
                }
            }
        }
    }

    private fun capture(name: String) {
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/snapshots/$name.png",
            roborazziOptions = screenshotRoborazziOptions,
        )
    }

    @Test
    fun mate_statistics_320_left() {
        setCard(
            statistics(
                MateBucket(1, found = 5, missed = 1),
                MateBucket(3, found = 4, missed = 2),
                MateBucket(5, found = 2, missed = 3),
                MateBucket(7, found = 1, missed = 2),
                MateBucket(9, found = 1, missed = 3),
                MateBucket(11, found = 1, missed = 3),
            ),
        )
        capture("mate_statistics_320_left")
    }

    @Test
    fun mate_statistics_31plus_scrolled() {
        setCard(
            statistics(
                *((1..31 step 2).map { plies ->
                    MateBucket(plies, found = (plies / 4 + 1), missed = (plies / 6 + 1))
                }.toTypedArray()),
            ),
        )
        repeat(3) {
            composeRule.onNodeWithTag("mate_statistics_scroll", useUnmergedTree = true)
                .performTouchInput { swipeLeft() }
            composeRule.waitForIdle()
        }
        capture("mate_statistics_31plus_scrolled")
    }

    @Test
    fun mate_statistics_100_percent() {
        setCard(statistics(MateBucket(1, found = 5, missed = 0)))
        capture("mate_statistics_100_percent")
    }

    @Test
    fun mate_statistics_0_percent() {
        setCard(statistics(MateBucket(1, found = 0, missed = 5)))
        capture("mate_statistics_0_percent")
    }

    @Test
    fun mate_statistics_missing() {
        setCard(statistics(MateBucket(1, found = 0, missed = 0, unknown = 3)))
        capture("mate_statistics_missing")
    }

    @Test
    fun mate_statistics_empty() {
        setCard(MateStatistics(analyzedGames = 12, verifiedGames = 0))
        capture("mate_statistics_empty")
    }

    @Test
    fun mate_statistics_dark() {
        setCard(
            statistics(
                MateBucket(1, found = 5, missed = 1),
                MateBucket(3, found = 4, missed = 2),
                MateBucket(5, found = 2, missed = 3),
            ),
            dark = true,
        )
        capture("mate_statistics_dark")
    }
}
