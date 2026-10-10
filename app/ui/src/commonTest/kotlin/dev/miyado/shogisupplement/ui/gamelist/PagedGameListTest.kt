package dev.miyado.shogisupplement.ui.gamelist

import dev.miyado.shogisupplement.db.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PagedGameListTest {
    private class Reader : GameListReader {
        val reads = mutableListOf<Pair<String?, GamePageCursor?>>()
        var fail = false
        override fun indexBatch() = 0
        override fun totalCount() = 90
        override fun pendingUploadCount() = 0
        override fun facets(owner: String?) = GameListFacets(emptyList(), emptyList(), emptyMap(), true, true, listOf("序盤"), setOf("序盤"))
        override fun summary(owner: String?, filter: GameListFilter) = GameListSummary(90, 90, 60, 10, 900, listOf(1500))
        override fun page(owner: String?, filter: GameListFilter, after: GamePageCursor?, limit: Int): GamePage {
            reads += owner to after
            if (fail) error("read failed")
            val start = after?.id?.toInt() ?: 0
            val end = (start + limit).coerceAtMost(90)
            val records = (start until end).map { n -> GameRecord(n.toLong(), "$n", "$n", 10, null, null, 1000, 1500,
                coefVersion = "test", positionLabels = setOf("序盤")) }
            return GamePage(records, if (end < 90) GamePageCursor(1000, end.toLong()) else null)
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
