package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.restore.GameRestoreScreen
import dev.miyado.shogisupplement.ui.restore.GameRestoreUiState
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 復元成功画面の状態遷移・ボタン活性・No-jitter（状態間でレイアウト高さが動かないこと）を検証する。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w400dp-h800dp-xxhdpi",
)
class GameRestoreScreenInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setState(state: GameRestoreUiState, onStart: () -> Unit = {}, onRetry: () -> Unit = {}, onFinish: () -> Unit = {}) {
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    GameRestoreScreen(state = state, onStart = onStart, onRetry = onRetry, onFinish = onFinish)
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun homeButtonTop(): Float =
        composeRule.onNodeWithTag("game_restore_home_button").fetchSemanticsNode().boundsInRoot.top

    @Test
    fun 件数確認中はホームへボタンだけ操作可で主ボタンは無効() {
        setState(GameRestoreUiState.Loading)
        composeRule.onNodeWithTag("game_restore_home_button").assertIsEnabled()
        composeRule.onNodeWithTag("game_restore_primary_button").assertIsNotEnabled()
    }

    @Test
    fun 件数0件超なら棋譜を復元するボタンでonStartが呼ばれる() {
        var started = false
        setState(GameRestoreUiState.Ready(count = 3), onStart = { started = true })
        composeRule.onNodeWithTag("game_restore_primary_button").assertIsEnabled()
        composeRule.onNodeWithTag("game_restore_primary_button").performClick()
        assertTrue(started)
    }

    @Test
    fun 件数0件なら主ボタンは無効でホームへのみ操作可() {
        setState(GameRestoreUiState.Ready(count = 0))
        composeRule.onNodeWithTag("game_restore_primary_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("game_restore_home_button").assertIsEnabled()
    }

    @Test
    fun ダウンロード中はホームへも主ボタンも無効() {
        setState(GameRestoreUiState.Downloading(done = 1, total = 3))
        composeRule.onNodeWithTag("game_restore_home_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("game_restore_primary_button").assertIsNotEnabled()
    }

    @Test
    fun 完了後はホームへボタンでonFinishが呼ばれる() {
        var finished = false
        setState(GameRestoreUiState.Completed(total = 3, succeeded = 3, failed = 0), onFinish = { finished = true })
        composeRule.onNodeWithTag("game_restore_home_button").performClick()
        assertTrue(finished)
    }

    @Test
    fun エラー時は再試行ボタンでonRetryが呼ばれる() {
        var retried = false
        setState(GameRestoreUiState.Error(AppStrings.GAME_RESTORE_ERROR_NETWORK), onRetry = { retried = true })
        composeRule.onNodeWithTag("game_restore_primary_button").assertIsEnabled()
        composeRule.onNodeWithTag("game_restore_primary_button").performClick()
        assertTrue(retried)
    }

    @Test
    fun 状態が入れ替わってもホームへボタンのY座標が変わらないこと() {
        var state by mutableStateOf<GameRestoreUiState>(GameRestoreUiState.Loading)
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    GameRestoreScreen(state = state)
                }
            }
        }
        composeRule.waitForIdle()
        val loadingTop = homeButtonTop()

        state = GameRestoreUiState.Ready(count = 12)
        composeRule.waitForIdle()
        val readyTop = homeButtonTop()

        state = GameRestoreUiState.Downloading(done = 5, total = 12)
        composeRule.waitForIdle()
        val downloadingTop = homeButtonTop()

        state = GameRestoreUiState.Completed(total = 12, succeeded = 10, failed = 2)
        composeRule.waitForIdle()
        val completedTop = homeButtonTop()

        assertEquals("Ready状態でNo-jitter", loadingTop, readyTop, 0.5f)
        assertEquals("Downloading状態でNo-jitter", loadingTop, downloadingTop, 0.5f)
        assertEquals("Completed状態（一部失敗）でNo-jitter", loadingTop, completedTop, 0.5f)
    }
}
