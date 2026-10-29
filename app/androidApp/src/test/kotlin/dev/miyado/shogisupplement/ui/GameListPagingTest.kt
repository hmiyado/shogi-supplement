package dev.miyado.shogisupplement.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.miyado.shogisupplement.db.*
import dev.miyado.shogisupplement.ui.gamelist.GameListScreen
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xxhdpi", application = android.app.Application::class)
class GameListPagingTest {
    @get:Rule val rule = createComposeRule()

    @Test fun scrollingLoadsNextPageWithoutChangingTotalAndFilterUsesUnreadMatches() {
        val reads = CopyOnWriteArrayList<GamePageCursor?>()
        val reader = object : GameListReader {
            override fun indexBatch() = 0
            override fun totalCount() = 95
            override fun pendingUploadCount() = 0
            override fun facets(owner: String?) = GameListFacets(emptyList(), emptyList(), emptyMap(), false, false, listOf("後半", "未該当"), setOf("後半"))
            override fun summary(owner: String?, filter: GameListFilter) = GameListSummary(if (filter.positionLabel == null) 95 else 5, 0, 0, 0, 0, emptyList())
            override fun page(owner: String?, filter: GameListFilter, after: GamePageCursor?, limit: Int): GamePage {
                reads += after
                val start = after?.id?.toInt() ?: if (filter.positionLabel == null) 0 else 90
                val end = (start + limit).coerceAtMost(95)
                val records = (start until end).map { n -> GameRecord(n.toLong(), "$n.kif", "$n", 100, null, null, 1000, 1500,
                    coefVersion = "test", positionLabels = if (n >= 90) setOf("後半") else emptySet()) }
                return GamePage(records, if (end < 95) GamePageCursor(1000, end.toLong()) else null)
            }
        }
        rule.setContent { ShogiTheme { GameListScreen(emptyList(), queries = reader, labelOwner = "alice", onBack = null, onGameClick = {}) } }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("95件").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("95件").assertIsDisplayed()
        rule.onNodeWithTag("game_list").performScrollToIndex(36)
        rule.waitUntil(10_000) { reads.any { it != null } }
        rule.onNodeWithTag("game_list").performScrollToIndex(0)
        rule.onNodeWithText("95件").assertIsDisplayed()
        rule.onNodeWithTag("filter_open_button").performClick()
        rule.onNodeWithTag("filter_position_label_未該当").assertIsNotEnabled()
        rule.onNodeWithTag("filter_position_label_後半").performClick()
        rule.onNodeWithTag("filter_apply_button").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("5 / 95件").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("90.kif").assertIsDisplayed()
        assertEquals(null, reads.last())
    }
}
