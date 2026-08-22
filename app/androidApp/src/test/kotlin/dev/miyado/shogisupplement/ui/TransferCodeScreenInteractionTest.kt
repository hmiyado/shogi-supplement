package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import dev.miyado.shogisupplement.ui.transfercode.TransferCodeScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    qualifiers = "w400dp-h800dp-xxhdpi",
)
class TransferCodeScreenInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val rawCode = "8QZKM-2XRTN-P9VCB-H4WLD-A7YFE-J3"
    private val maskedDisplay = "*****-*****-*****\n*****-*****-**"
    private val rawDisplay = "8QZKM-2XRTN-P9VCB\nH4WLD-A7YFE-J3"

    private fun copyButtonTop(): Float =
        composeRule.onNodeWithTag("transfer_code_copy_button")
            .fetchSemanticsNode().boundsInRoot.top

    @Test
    fun defaultsToMasked_thenTogglesToRawAndBack() {
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    TransferCodeScreen(code = rawCode, onBack = {})
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("transfer_code_value").assertTextEquals(maskedDisplay)
        val maskedTop = copyButtonTop()

        composeRule.onNodeWithTag("transfer_code_reveal_toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("transfer_code_value").assertTextEquals(rawDisplay)

        assertEquals(
            "表示トグルでレイアウトの高さが動かないこと（No-jitter原則）",
            maskedTop,
            copyButtonTop(),
            0.5f,
        )

        composeRule.onNodeWithTag("transfer_code_reveal_toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("transfer_code_value").assertTextEquals(maskedDisplay)
    }

    @Test
    fun copy_alwaysSendsRawCode_regardlessOfMaskState() {
        var copied: String? = null
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    TransferCodeScreen(
                        code = rawCode,
                        onBack = {},
                        onCopy = { copied = it },
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("transfer_code_copy_button").performClick()
        assertEquals(rawCode, copied)

        copied = null

        composeRule.onNodeWithTag("transfer_code_reveal_toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("transfer_code_copy_button").performClick()
        assertEquals(rawCode, copied)
    }

    @Test
    fun loadingState_showsNoCode() {
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    TransferCodeScreen(code = null, onBack = {})
                }
            }
        }
        composeRule.waitForIdle()
        val nodes = composeRule.onAllNodesWithTag("transfer_code_value")
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
        assertTrue(nodes.isEmpty())
    }
}
