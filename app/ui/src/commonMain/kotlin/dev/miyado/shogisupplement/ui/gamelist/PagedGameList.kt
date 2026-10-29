package dev.miyado.shogisupplement.ui.gamelist

import androidx.compose.runtime.*
import dev.miyado.shogisupplement.db.*
import dev.miyado.shogisupplement.ui.common.defaultIoDispatcher
import dev.miyado.shogisupplement.util.currentEpochSeconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/** 件数・集計は全対象、本文は読み込んだページだけを保持する。 */
internal data class PagedGameListSnapshot(
    val page: GamePage,
    val summary: GameListSummary,
    val total: Int,
    val facets: GameListFacets,
    val savedCounts: Map<String, Int>,
)

internal class PagedGameListState(
    private val queries: GameListReader,
    private val owner: String?,
    private val filter: GameListFilter,
    private val dispatcher: kotlinx.coroutines.CoroutineDispatcher = defaultIoDispatcher,
) {
    var snapshot by mutableStateOf<PagedGameListSnapshot?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    var failedRefresh by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set

    private val mutex = Mutex()
    private var latestSavedFilters: List<SavedGameFilter> = emptyList()

    internal fun matches(queries: GameListReader, owner: String?, filter: GameListFilter): Boolean =
        this.queries === queries && this.owner == owner && this.filter == filter

    suspend fun refresh(savedFilters: List<SavedGameFilter>) {
        latestSavedFilters = savedFilters
        mutex.withLock {
            loading = true
            failed = false
            failedRefresh = false
            try {
                val pagesToRestore = snapshot?.page?.games?.size ?: 0
                val now = currentEpochSeconds()
                val filters = latestSavedFilters
                snapshot = withContext(dispatcher) { readSnapshot(pagesToRestore, filters, now) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                failed = true
                failedRefresh = true
            } finally {
                loading = false
            }
        }
    }

    private suspend fun readSnapshot(pagesToRestore: Int, savedFilters: List<SavedGameFilter>, now: Long): PagedGameListSnapshot {
        while (queries.indexBatch() > 0) yield()
        val first = queries.page(owner, filter)
        var games = first.games
        var next = first.next
        while (games.size < pagesToRestore && next != null) {
            val page = queries.page(owner, filter, next)
            games = (games + page.games).distinctBy { it.id }
            next = page.next
        }
        return PagedGameListSnapshot(
            GamePage(games, next),
            queries.summary(owner, filter),
            queries.totalCount(),
            queries.facets(owner),
            savedFilters.associate { it.name to queries.count(owner, it.currentFilter(now)) },
        )
    }

    suspend fun loadMore() {
        mutex.withLock {
            val current = snapshot ?: return
            val cursor = current.page.next ?: return
            loading = true
            loadingMore = true
            failed = false
            failedRefresh = false
            try {
                val next = withContext(dispatcher) { queries.page(owner, filter, cursor) }
                snapshot = current.copy(page = GamePage((current.page.games + next.games).distinctBy { it.id }, next.next))
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { failed = true }
            finally {
                loading = false
                loadingMore = false
            }
        }
    }
}

@Composable
internal fun rememberPagedGameList(
    queries: GameListReader,
    owner: String?,
    filter: GameListFilter,
    revision: Any?,
    saved: List<SavedGameFilter>,
    session: GameListSession? = null,
): PagedGameListState {
    var databaseRevision by remember(queries) { mutableStateOf(0L) }
    LaunchedEffect(queries) { queries.changes().collect { databaseRevision++ } }
    // 別アカウント・別条件の非同期結果を、現在の一覧へ流し込まない。
    val state = remember(session, queries, owner, filter) {
        session?.paged?.takeIf { it.matches(queries, owner, filter) } ?: PagedGameListState(queries, owner, filter)
    }
    if (session != null) session.paged = state
    LaunchedEffect(state, revision, saved, databaseRevision) { state.refresh(saved) }
    return state
}
