package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import dev.miyado.shogisupplement.db.*
import dev.miyado.shogisupplement.ui.gamelist.GameListScreen
import dev.miyado.shogisupplement.ui.gamelist.GameListSession
import dev.miyado.shogisupplement.ui.gamelist.rememberGameListSession
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xxhdpi", application = android.app.Application::class)
class GameListSessionTest {
    @get:Rule val rule = createComposeRule()

    private class Reader : GameListReader {
        val reads = CopyOnWriteArrayList<GamePageCursor?>()
        var total = 120
        var renamed = mapOf<Long, String>()
        val filteredCount get() = total - 100

        override fun indexBatch() = 0
        override fun totalCount() = total
        override fun pendingUploadCount() = 0
        override fun facets(owner: String?) = GameListFacets(
            emptyList(), emptyList(), emptyMap(), false, false, listOf("後半"), setOf("後半"),
        )
        override fun summary(owner: String?, filter: GameListFilter) =
            GameListSummary(if (filter.positionLabel == null) total else filteredCount, 0, 0, 0, 0, emptyList())
        override fun page(owner: String?, filter: GameListFilter, after: GamePageCursor?, limit: Int): GamePage {
            reads += after
            val ids = (0 until total).map { it.toLong() }
                .filter { filter.positionLabel == null || it >= 100L }
            val start = after?.id?.let { cursor ->
                ids.indexOfFirst { it > cursor }.let { if (it < 0) ids.size else it }
            } ?: 0
            val pageIds = ids.drop(start).take(limit)
            val records = pageIds.map { id ->
                GameRecord(
                    id = id,
                    fileName = renamed[id] ?: if (owner == "bob") "bob-$id.kif" else "$id.kif",
                    contentHash = "hash$id",
                    moveCount = 10L,
                    senteName = null,
                    goteName = null,
                    analyzedAt = 1_000_000L - id,
                    rating = 1500L,
                    coefVersion = "test",
                )
            }
            return GamePage(
                records,
                if (start + pageIds.size < ids.size) GamePageCursor(1_000_000L - pageIds.last(), pageIds.last()) else null,
            )
        }
    }

    private class BlockingReader(private val gate: CountDownLatch) : GameListReader {
        val pageCalls = AtomicInteger(0)

        override fun indexBatch() = 0
        override fun totalCount() = 1
        override fun pendingUploadCount() = 0
        override fun facets(owner: String?) = GameListFacets(emptyList(), emptyList(), emptyMap(), false, false, emptyList(), emptySet())
        override fun summary(owner: String?, filter: GameListFilter) = GameListSummary(1, 0, 0, 0, 0, emptyList())
        override fun page(owner: String?, filter: GameListFilter, after: GamePageCursor?, limit: Int): GamePage {
            val call = pageCalls.incrementAndGet()
            if (call == 1) gate.await()
            val name = if (call == 1) "stale.kif" else "latest.kif"
            return GamePage(
                listOf(GameRecord(1L, name, "hash", 10, null, null, 1_000_000, 1500, coefVersion = "test")),
                null,
            )
        }
    }

    private fun showList(
        reader: Reader,
        show: androidx.compose.runtime.MutableState<Boolean>,
        owner: androidx.compose.runtime.MutableState<String?>,
        session: androidx.compose.runtime.MutableState<GameListSession?>,
    ) {
        rule.setContent {
            ShogiTheme {
                val listSession = rememberGameListSession()
                session.value = listSession
                val holder = rememberSaveableStateHolder()
                if (show.value) {
                    holder.SaveableStateProvider("GameList") {
                        GameListScreen(
                            games = emptyList(),
                            queries = reader,
                            session = listSession,
                            labelOwner = owner.value,
                            onBack = null,
                            onGameClick = {},
                        )
                    }
                } else {
                    Text("レポート")
                }
            }
        }
    }

    private data class ScrollSnapshot(
        val index: Int,
        val offset: Int,
        val firstKey: Long,
        val fullyVisibleKey: Long,
    )

    private fun captureScroll(session: androidx.compose.runtime.MutableState<GameListSession?>): ScrollSnapshot =
        rule.runOnIdle {
            val state = session.value!!.listState
            val layout = state.layoutInfo
            val firstKey = layout.visibleItemsInfo.first().key as Long
            val fullyVisible = layout.visibleItemsInfo.first { item ->
                item.key is Long && item.offset >= layout.viewportStartOffset &&
                    item.offset + item.size <= layout.viewportEndOffset
            }
            ScrollSnapshot(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset, firstKey, fullyVisible.key as Long)
        }

    private fun assertScroll(session: androidx.compose.runtime.MutableState<GameListSession?>, snapshot: ScrollSnapshot) {
        rule.runOnIdle {
            val state = session.value!!.listState
            assertEquals(snapshot.index, state.firstVisibleItemIndex)
            assertEquals(snapshot.offset, state.firstVisibleItemScrollOffset)
            assertEquals(snapshot.firstKey, state.layoutInfo.visibleItemsInfo.first().key)
        }
    }

    private fun itemCount(session: androidx.compose.runtime.MutableState<GameListSession?>) =
        rule.runOnIdle { session.value!!.listState.layoutInfo.totalItemsCount }

    private fun scrollToIndex(session: androidx.compose.runtime.MutableState<GameListSession?>, index: Int) {
        rule.onNodeWithTag("game_list").performScrollToIndex(index)
        rule.waitForIdle()
    }

    private fun loadUntil(session: androidx.compose.runtime.MutableState<GameListSession?>, reader: Reader, count: Int) {
        while (itemCount(session) < count) {
            val before = reader.reads.size
            scrollToIndex(session, itemCount(session) - 1)
            rule.waitUntil(10_000) { reader.reads.size > before }
        }
    }

    @Test fun returningFromReportKeepsScrollLoadedPagesAndAnchorWithoutLoading() {
        val reader = Reader()
        val show = mutableStateOf(true)
        val owner = mutableStateOf<String?>("alice")
        val session = mutableStateOf<GameListSession?>(null)
        showList(reader, show, owner, session)
        rule.waitUntil(10_000) { rule.onAllNodesWithText("120件").fetchSemanticsNodes().isNotEmpty() }
        loadUntil(session, reader, 121)
        scrollToIndex(session, 70)
        rule.onNodeWithTag("game_list").performTouchInput { swipeUp() }
        rule.waitForIdle()

        val snapshot = captureScroll(session)
        assertTrue(snapshot.index >= 70)

        show.value = false
        rule.waitForIdle()
        rule.onNodeWithText("レポート").assertIsDisplayed()
        val readsBeforeReturn = reader.reads.size

        show.value = true
        rule.waitForIdle()

        rule.onNodeWithTag("games_loading").assertDoesNotExist()
        assertScroll(session, snapshot)
        rule.onNodeWithText("${snapshot.fullyVisibleKey}.kif").assertIsDisplayed()
        rule.waitUntil(10_000) { reader.reads.size > readsBeforeReturn }
    }

    @Test fun cancelledRefreshStillLoadsLatestQuery() {
        val gate = CountDownLatch(1)
        val reader = BlockingReader(gate)
        val revision = mutableStateOf(0)
        val session = mutableStateOf<GameListSession?>(null)
        rule.setContent {
            ShogiTheme {
                val listSession = rememberGameListSession()
                session.value = listSession
                GameListScreen(
                    games = emptyList(),
                    queries = reader,
                    session = listSession,
                    labelOwner = "alice",
                    dataRevision = revision.value,
                    onBack = null,
                    onGameClick = {},
                )
            }
        }
        rule.waitUntil(10_000) { reader.pageCalls.get() >= 1 }
        rule.runOnIdle { revision.value = 1 }
        rule.waitForIdle()
        gate.countDown()

        rule.waitUntil(10_000) { rule.onAllNodesWithText("latest.kif").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(reader.pageCalls.get() >= 2)
        rule.onNodeWithText("stale.kif").assertDoesNotExist()
    }

    @Test fun returningFromReportKeepsAppliedFilterScrollAndAnchor() {
        val reader = Reader()
        val show = mutableStateOf(true)
        val owner = mutableStateOf<String?>("alice")
        val session = mutableStateOf<GameListSession?>(null)
        showList(reader, show, owner, session)
        rule.waitUntil(10_000) { rule.onAllNodesWithText("120件").fetchSemanticsNodes().isNotEmpty() }

        rule.onNodeWithTag("filter_open_button").performClick()
        rule.onNodeWithTag("filter_position_label_後半").performClick()
        rule.onNodeWithTag("filter_apply_button").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("20 / 120件").fetchSemanticsNodes().isNotEmpty() }
        scrollToIndex(session, 10)
        val snapshot = captureScroll(session)
        assertTrue(snapshot.index > 0)

        show.value = false
        rule.waitForIdle()
        rule.onNodeWithText("レポート").assertIsDisplayed()

        show.value = true
        rule.waitForIdle()

        rule.onNodeWithTag("games_loading").assertDoesNotExist()
        assertScroll(session, snapshot)
        rule.onNodeWithText("${snapshot.fullyVisibleKey}.kif").assertIsDisplayed()
        scrollToIndex(session, 0)
        rule.waitUntil(10_000) { rule.onAllNodesWithText("20 / 120件").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("20 / 120件").assertIsDisplayed()
    }

    @Test fun actualFilterChangeResetsScrollAndLoadsNewDataset() {
        val reader = Reader()
        val show = mutableStateOf(true)
        val owner = mutableStateOf<String?>("alice")
        val session = mutableStateOf<GameListSession?>(null)
        showList(reader, show, owner, session)
        rule.waitUntil(10_000) { rule.onAllNodesWithText("120件").fetchSemanticsNodes().isNotEmpty() }
        loadUntil(session, reader, 81)
        scrollToIndex(session, 0)

        rule.onNodeWithTag("filter_open_button").performClick()
        rule.runOnIdle { session.value!!.listState.requestScrollToItem(70) }
        rule.waitForIdle()
        assertEquals(70, rule.runOnIdle { session.value!!.listState.firstVisibleItemIndex })
        rule.onNodeWithTag("filter_position_label_後半").performClick()
        rule.onNodeWithTag("filter_apply_button").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("20 / 120件").fetchSemanticsNodes().isNotEmpty() }

        assertEquals(0, rule.runOnIdle { session.value!!.listState.firstVisibleItemIndex })
        rule.onNodeWithText("100.kif").assertIsDisplayed()
        rule.onNodeWithText("99.kif").assertDoesNotExist()
    }

    @Test fun ownerChangeWhileReportOpenLoadsOnlyNewOwnerFromTop() {
        val reader = Reader()
        val show = mutableStateOf(true)
        val owner = mutableStateOf<String?>("alice")
        val session = mutableStateOf<GameListSession?>(null)
        showList(reader, show, owner, session)
        rule.waitUntil(10_000) { rule.onAllNodesWithText("120件").fetchSemanticsNodes().isNotEmpty() }
        loadUntil(session, reader, 81)
        scrollToIndex(session, 70)

        show.value = false
        rule.waitForIdle()
        owner.value = "bob"
        show.value = true
        rule.waitUntil(10_000) { rule.onAllNodesWithText("bob-0.kif").fetchSemanticsNodes().isNotEmpty() }

        assertEquals(0, rule.runOnIdle { session.value!!.listState.firstVisibleItemIndex })
        rule.onNodeWithText("bob-70.kif").assertDoesNotExist()
        rule.onNodeWithText("70.kif").assertDoesNotExist()
    }

    @Test fun updateAndDeletionWhileReportOpenAreRevalidatedOnReturn() {
        val reader = Reader()
        val show = mutableStateOf(true)
        val owner = mutableStateOf<String?>("alice")
        val session = mutableStateOf<GameListSession?>(null)
        showList(reader, show, owner, session)
        rule.waitUntil(10_000) { rule.onAllNodesWithText("120件").fetchSemanticsNodes().isNotEmpty() }
        loadUntil(session, reader, 121)
        scrollToIndex(session, 70)
        val snapshot = captureScroll(session)

        show.value = false
        rule.waitForIdle()
        reader.renamed = mapOf(snapshot.firstKey to "更新.kif", snapshot.fullyVisibleKey to "更新表示.kif")
        reader.total = 119
        show.value = true
        rule.waitForIdle()

        assertScroll(session, snapshot)
        rule.waitUntil(10_000) { rule.onAllNodesWithText("更新表示.kif").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("119.kif").assertDoesNotExist()
        rule.onNodeWithText("更新表示.kif").assertIsDisplayed()
        scrollToIndex(session, 0)
        rule.onNodeWithText("119件").assertIsDisplayed()
    }
}
