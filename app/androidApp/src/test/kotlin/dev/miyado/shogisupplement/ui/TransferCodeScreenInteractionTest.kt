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

/**
 * 引き継ぎコード表示画面のマスク/表示切替の単体テスト。
 *
 * パスワード同様の扱いとして、既定は伏字・コピーは常時可能・
 * 生の値は表示トグルの明示操作でのみ見せる、の3点をここで検証する。
 */
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

        // 既定は伏字。生の値がそのままテキストノードに出ていないこと。
        composeRule.onNodeWithTag("transfer_code_value").assertTextEquals(maskedDisplay)
        val maskedTop = copyButtonTop()

        // 表示トグルで生の値が見えること。
        composeRule.onNodeWithTag("transfer_code_reveal_toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("transfer_code_value").assertTextEquals(rawDisplay)

        // トグル操作の前後でコピー行のY座標が動かないこと（DESIGN.md No-jitter原則。
        // 表示はグループ単位の明示改行のため、字幅・フォント解決に関わらず行構成が一致する）。
        assertEquals(
            "表示トグルでレイアウトの高さが動かないこと（No-jitter原則）",
            maskedTop,
            copyButtonTop(),
            0.5f,
        )

        // もう一度タップすると伏字に戻ること。
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

        // マスク中（既定）でもコピーは生のコードを渡す。
        composeRule.onNodeWithTag("transfer_code_copy_button").performClick()
        assertEquals(rawCode, copied)

        copied = null

        // 表示中でもコピーは同じ生のコードを渡す。
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
        // 読み込み中はコード表示行自体が無い（CircularProgressIndicatorのみ）。
        val nodes = composeRule.onAllNodesWithTag("transfer_code_value")
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
        assertTrue(nodes.isEmpty())
    }
}
