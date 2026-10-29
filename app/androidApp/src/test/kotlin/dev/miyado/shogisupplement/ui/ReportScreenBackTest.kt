package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.ui.report.ReportScreen
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * レポート画面の左上「←」が呼び出し元へ戻せることを、1カラムと2ペインの両方で保証する。
 * マイページ（Web）は一覧へ戻る唯一の導線がここしかない。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class ReportScreenBackTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun game() = GameRecord(
        id = 1L,
        fileName = "wars.kif",
        contentHash = "hash",
        moveCount = 4L,
        senteName = "miyado",
        goteName = "相手",
        analyzedAt = 1_780_000_000L,
        rating = 1750L,
        coefVersion = "hao_v1",
        movesUsi = listOf("7g7f", "3c3d", "2g2f", "8c8d"),
        userSide = "sente",
    )

    private fun clickBack(): Int {
        var backCount = 0
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    ReportScreen(
                        game = game(),
                        reports = emptyList(),
                        flip = false,
                        canDelete = false,
                        canEdit = false,
                        onBack = { backCount++ },
                    )
                }
            }
        }
        composeRule.onNodeWithContentDescription("戻る").performClick()
        composeRule.waitForIdle()
        return backCount
    }

    @Test
    @Config(qualifiers = "w400dp-h800dp-xxhdpi")
    fun `1カラムでは戻るが呼ばれる`() {
        assertEquals(1, clickBack())
    }

    @Test
    @Config(qualifiers = "w1440dp-h900dp-xxhdpi")
    fun `2ペインでも戻るが呼ばれる`() {
        assertEquals(1, clickBack())
    }

    /** 悪手が無い棋譜で一覧を開けると、未解析でも「悪手は見つかりませんでした」と出てしまう。 */
    @Test
    @Config(qualifiers = "w400dp-h800dp-xxhdpi")
    fun `悪手が無いときは悪手一覧タブを押しても切り替わらない`() {
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    ReportScreen(
                        game = game(),
                        reports = emptyList(),
                        flip = false,
                        analysisPending = true,
                        onBack = {},
                    )
                }
            }
        }
        composeRule.onNodeWithText("悪手一覧").performClick()
        composeRule.waitForIdle()

        // 切り替わっていない＝内側タブ（本譜/最善の変化）が出ていない。
        composeRule.onNodeWithText("本譜").assertDoesNotExist()
        composeRule.onNodeWithText("サマリー").assertIsDisplayed()
    }

    /** 検討を持たないホスト（Web版マイページ）では、押しても反応しないタブを出さない。 */
    @Test
    @Config(qualifiers = "w400dp-h800dp-xxhdpi")
    fun `検討の開始口が無いホストでは検討タブを押しても切り替わらない`() {
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    ReportScreen(
                        game = game(),
                        reports = emptyList(),
                        flip = false,
                        onBack = {},
                    )
                }
            }
        }
        composeRule.onNodeWithText("検討").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("検討中").assertDoesNotExist()
        composeRule.onNodeWithText("サマリー").assertIsDisplayed()
    }
}
