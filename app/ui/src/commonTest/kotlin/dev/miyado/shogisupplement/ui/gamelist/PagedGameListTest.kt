package dev.miyado.shogisupplement.ui.gamelist

import dev.miyado.shogisupplement.db.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PagedGameListTest {
    private class Reader : GameListReader {
        val reads = mutableListOf<Pair<String?, GamePageCursor?>>()
        var fail = false
        var total = 90
        var deleted = emptySet<Int>()
        val renamed = mutableMapOf<Int, String>()
        override fun indexBatch() = 0
        override fun totalCount() = total
        override fun pendingUploadCount() = 0
        override fun facets(owner: String?) = GameListFacets(emptyList(), emptyList(), emptyMap(), true, true, listOf("序盤"), setOf("序盤"))
        override fun summary(owner: String?, filter: GameListFilter) = GameListSummary(total, total, 60, 10, 900, listOf(1500))
        override fun page(owner: String?, filter: GameListFilter, after: GamePageCursor?, limit: Int): GamePage {
            reads += owner to after
            if (fail) error("read failed")
            val ids = (0 until total).filterNot { it in deleted }
            val start = after?.id?.toInt()?.let { cursor ->
                ids.indexOfFirst { it > cursor }.let { if (it < 0) ids.size else it }
            } ?: 0
            val pageIds = ids.drop(start).take(limit)
            val records = pageIds.map { n -> GameRecord(n.toLong(), renamed[n] ?: "$n", "$n", 10, null, null, 1000, 1500,
                coefVersion = "test", positionLabels = setOf("序盤")) }
            return GamePage(records, if (start + pageIds.size < ids.size) GamePageCursor(1000, pageIds.last().toLong()) else null)
        }
    }

    @Test fun pagesAppendWithoutReplacingFullSummaryAndFailuresCanRetry() = runTest {
        val reader = Reader()
        val state = PagedGameListState(reader, "alice", GameListFilter(), StandardTestDispatcher(testScheduler))
        state.refresh(emptyList())
        assertEquals(40, state.snapshot?.page?.games?.size)
        assertEquals(90, state.snapshot?.summary?.games)
        reader.fail = true
        state.loadMore()
        assertTrue(state.failed)
        assertEquals(40, state.snapshot?.page?.games?.size)
        reader.fail = false
        state.loadMore()
        assertFalse(state.failed)
        assertEquals(80, state.snapshot?.page?.games?.size)
        state.loadMore()
        assertEquals(90, state.snapshot?.page?.games?.size)
        assertNull(state.snapshot?.page?.next)
        assertEquals(90, state.snapshot?.summary?.games)
        assertTrue(state.snapshot!!.page.games.all { it.positionLabels == setOf("序盤") })
        val reads = reader.reads.size
        state.loadMore()
        assertEquals(reads, reader.reads.size)
    }

    @Test fun refreshKeepsLoadedPagesAndCursor() = runTest {
        val reader = Reader()
        val state = PagedGameListState(reader, "alice", GameListFilter(), StandardTestDispatcher(testScheduler))
        state.refresh(emptyList())
        state.loadMore()
        assertEquals(80, state.snapshot?.page?.games?.size)

        state.refresh(emptyList())

        assertEquals(80, state.snapshot?.page?.games?.size)
        assertNotNull(state.snapshot?.page?.next)
        state.loadMore()
        assertEquals(90, state.snapshot?.page?.games?.size)
        assertNull(state.snapshot?.page?.next)
    }

    @Test fun failedRefreshKeepsCompatiblePageAndCanRetry() = runTest {
        val reader = Reader()
        val state = PagedGameListState(reader, "alice", GameListFilter(), StandardTestDispatcher(testScheduler))
        state.refresh(emptyList())
        state.loadMore()
        assertEquals(80, state.snapshot?.page?.games?.size)

        reader.fail = true
        state.refresh(emptyList())

        assertTrue(state.failed)
        assertTrue(state.failedRefresh)
        assertEquals(80, state.snapshot?.page?.games?.size)

        reader.fail = false
        state.refresh(emptyList())

        assertFalse(state.failed)
        assertFalse(state.failedRefresh)
        assertEquals(80, state.snapshot?.page?.games?.size)
    }

    @Test fun refreshRevalidatesUpdatesAndDeletionsWhileAway() = runTest {
        val reader = Reader()
        val state = PagedGameListState(reader, "alice", GameListFilter(), StandardTestDispatcher(testScheduler))
        state.refresh(emptyList())
        state.loadMore()
        assertEquals(80, state.snapshot?.page?.games?.size)

        reader.renamed[5] = "更新5"
        reader.deleted = setOf(70)
        reader.total = 75
        state.refresh(emptyList())

        val games = state.snapshot?.page?.games.orEmpty()
        assertEquals("更新5", games.first { it.id == 5L }.fileName)
        assertTrue(games.none { it.id == 70L })
        assertEquals(74, games.size)
        assertEquals(75, state.snapshot?.summary?.games)
    }

    @Test fun ownerAndFilterSwitchStartWithNoOldPageOrCursor() = runTest {
        val reader = Reader()
        val first = PagedGameListState(reader, "alice", GameListFilter(), StandardTestDispatcher(testScheduler))
        first.refresh(emptyList())
        first.loadMore()
        val next = PagedGameListState(reader, "bob", GameListFilter(positionLabel = "序盤"), StandardTestDispatcher(testScheduler))
        assertNull(next.snapshot)
        next.refresh(emptyList())
        assertEquals("bob" to null, reader.reads.last())
        assertEquals(40, next.snapshot?.page?.games?.size)
    }
}
