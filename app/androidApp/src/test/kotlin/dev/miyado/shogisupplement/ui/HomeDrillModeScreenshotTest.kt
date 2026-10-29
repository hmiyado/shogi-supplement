package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import dev.miyado.shogisupplement.ui.home.HomeScreen
import dev.miyado.shogisupplement.ui.home.TodaysDrillHint
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w320dp-h700dp-xxhdpi", application = android.app.Application::class)
class HomeDrillModeScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun bothButtonsStartTheirOwnMode() {
        var normalStarts = 0
        var mateStarts = 0
        showHome(true, { normalStarts++ }, { mateStarts++ })
        composeRule.onNodeWithText("次の一手").assertIsEnabled().performClick()
        composeRule.onNodeWithText("詰将棋").assertIsEnabled().performClick()
        assertEquals(1, normalStarts)
        assertEquals(1, mateStarts)
        composeRule.onRoot().captureRoboImage("src/test/snapshots/home_drill_modes.png", roborazziOptions = screenshotRoborazziOptions)
    }

    @Test fun mateButtonIsDisabledWithoutCandidates() {
        showHome(false)
        composeRule.onNodeWithText("次の一手").assertIsEnabled()
        composeRule.onNodeWithText("詰将棋").assertIsNotEnabled()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/home_drill_modes_empty.png", roborazziOptions = screenshotRoborazziOptions)
    }

    @Test @Config(qualifiers = "+night") fun darkHome() {
        showHome(true)
        composeRule.onNodeWithText("詰将棋").assertIsEnabled()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/home_drill_modes_dark.png", roborazziOptions = screenshotRoborazziOptions)
    }

    private fun showHome(hasMate: Boolean, normal: () -> Unit = {}, mate: () -> Unit = {}) {
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    HomeScreen(
                        pastGames = emptyList(), isLoggedIn = true,
                        todaysDrillHint = TodaysDrillHint(41, hasMate),
                        onStartDrill = normal, onStartMateDrill = mate,
                        onOpenKif = {}, onGameClick = {},
                    )
                }
            }
        }
    }
}
