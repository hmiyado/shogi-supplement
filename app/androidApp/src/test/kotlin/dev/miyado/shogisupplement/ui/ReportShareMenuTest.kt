package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.miyado.shogisupplement.auth.AuthUser
import kotlinx.coroutines.flow.MutableStateFlow
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.gameshare.GameShareDraft
import dev.miyado.shogisupplement.gameshare.GameShareResult
import dev.miyado.shogisupplement.gameshare.GameShareService
import dev.miyado.shogisupplement.gameshare.GameShareSummary
import dev.miyado.shogisupplement.ui.report.ReportScreen
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
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
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xxhdpi")
class ReportShareMenuTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val game = GameRecord(
        id = 1L,
        fileName = "game.kif",
        contentHash = "hash",
        moveCount = 4L,
        senteName = "sente",
        goteName = "gote",
        analyzedAt = 1_780_000_000L,
        rating = 1750L,
        coefVersion = "hao_v1",
        movesUsi = listOf("7g7f", "3c3d", "2g2f", "8c8d"),
        userSide = "sente",
    )

    @Test
    fun shareMenuCreatesAndSharesGameUrlAlongsideScreenCapture() {
        var sharedGameUrl: String? = null
        var sharedScreen = false
        val service = FakeGameShareService()
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    ReportScreen(
                        game = game,
                        reports = emptyList(),
                        flip = false,
                        onBack = {},
                        onShare = { sharedScreen = true },
                        gameShareService = service,
                        onShareGameUrl = { sharedGameUrl = it },
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("共有").performClick()
        composeRule.onNodeWithText("画面を共有").assertIsDisplayed()
        composeRule.onNodeWithText("棋譜のURLを共有").performClick()
        composeRule.onNodeWithText("棋譜を共有").assertIsDisplayed()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("URLを作成して共有").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("URLを作成して共有").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("game_share_url").assertIsDisplayed()
        composeRule.onNodeWithText("共有").performClick()
        composeRule.waitForIdle()

        val url = sharedGameUrl
        assertTrue(url != null && url.startsWith("https://shogi-supplement.miyado.dev/kento.html?game=v1&id="))
        assertEquals(false, sharedScreen)
    }

    @Test
    fun signedOutKeepsScreenSharingButHidesGamePublishing() {
        val service = FakeGameShareService().apply { currentOwner.value = null }
        var screenShared = false
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    ReportScreen(game = game, reports = emptyList(), flip = false, onBack = {},
                        gameShareService = service, onShare = { screenShared = true })
                }
            }
        }
        composeRule.onNodeWithContentDescription("共有").performClick()
        composeRule.onNodeWithText("棋譜のURLを共有").assertDoesNotExist()
        composeRule.onNodeWithText("画面を共有").performClick()
        assertTrue(screenShared)
    }

    @Test
    fun ownerChangeClosesOldDialogAndLoadsOnlyNewOwnersLink() {
        val service = FakeGameShareService()
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    ReportScreen(game = game, reports = emptyList(), flip = false, onBack = {},
                        gameShareService = service)
                }
            }
        }
        composeRule.onNodeWithContentDescription("共有").performClick()
        composeRule.onNodeWithText("棋譜のURLを共有").performClick()
        composeRule.onNodeWithText("URLを作成して共有").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("game_share_url").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("game_share_url").assertTextEquals(
            "https://shogi-supplement.miyado.dev/kento.html?game=v1&id=AbCdEfGhIjKlMnOpQrStUv")
        composeRule.runOnIdle { service.currentOwner.value = AuthUser("owner-2") }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("game_share_url").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("共有").performClick()
        composeRule.onNodeWithText("棋譜のURLを共有").performClick()
        composeRule.onNodeWithText("URLを作成して共有").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("game_share_url").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("game_share_url").assertTextEquals(
            "https://shogi-supplement.miyado.dev/kento.html?game=v1&id=ZbCdEfGhIjKlMnOpQrStUv")
        composeRule.runOnIdle { service.currentOwner.value = null }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("game_share_url").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("共有").performClick()
        composeRule.onNodeWithText("棋譜のURLを共有").assertDoesNotExist()
    }

    private class FakeGameShareService : GameShareService {
        override val currentOwner = MutableStateFlow<AuthUser?>(AuthUser("owner-1"))
        private val id get() = if (currentOwner.value?.id == "owner-2") "ZbCdEfGhIjKlMnOpQrStUv" else "AbCdEfGhIjKlMnOpQrStUv"

        override suspend fun create(draft: GameShareDraft): GameShareResult<GameShareSummary> =
            GameShareResult.Success(
                GameShareSummary(id),
            )
    }
}
