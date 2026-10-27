package dev.miyado.shogisupplement.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.ui.common.LocalScaffoldContentInsets
import dev.miyado.shogisupplement.navigation.RootTab
import dev.miyado.shogisupplement.ui.navigation.RootTabShell
import dev.miyado.shogisupplement.ui.home.HomeScreen
import dev.miyado.shogisupplement.ui.home.TodaysDrillHint
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w466dp-h800dp-mdpi", application = android.app.Application::class)
class ScaffoldHorizontalInsetsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun homeAvoidsSideInsets() = assertSideInsets(LayoutDirection.Ltr)
    @Test fun homeAvoidsSideInsetsInRtl() = assertSideInsets(LayoutDirection.Rtl)

    private fun assertSideInsets(direction: LayoutDirection) {
        composeRule.setContent {
            ShogiTheme {
                CompositionLocalProvider(
                    LocalLayoutDirection provides direction,
                    LocalScaffoldContentInsets provides WindowInsets(left = 12.dp, right = 84.dp),
                ) {
                    Surface {
                        RootTabShell(selected = RootTab.HOME, visible = true, screenKey = "home", onSelect = {}) {
                        HomeScreen(
                            pastGames = emptyList(), todaysDrillHint = TodaysDrillHint(ply = 12),
                            onOpenKif = {}, onGameClick = {}, onStartDrill = {},
                            onOpenSettings = {}, onOpenStrengthHelp = {},
                        )
                        }
                    }
                }
            }
        }
        val card = composeRule.onNode(hasClickAction() and hasText("次の一手"))
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Card crosses left safe area: $card", card.left >= 12f)
        assertTrue("Card crosses right safe area: $card", card.right <= 466f - 84f)
    }
}
