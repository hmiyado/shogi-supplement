package dev.miyado.shogisupplement.ui.gamelist

import androidx.compose.runtime.*
import dev.miyado.shogisupplement.db.*
import dev.miyado.shogisupplement.ui.common.defaultIoDispatcher
import dev.miyado.shogisupplement.util.currentEpochSeconds
import kotlinx.coroutines.CancellationException
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

    suspend fun refresh(savedFilters: List<SavedGameFilter>) {
        if (loading) return
        loading = true
        failed = false
        try {
            val now = currentEpochSeconds()
            snapshot = withContext(dispatcher) {
                while (queries.indexBatch() > 0) yield()
                PagedGameListSnapshot(queries.page(owner, filter), queries.summary(owner, filter), queries.totalCount(),
                    queries.facets(owner), savedFilters.associate { it.name to queries.count(owner, it.currentFilter(now)) })
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }

    suspend fun loadMore() {
        val current = snapshot ?: return
        val cursor = current.page.next ?: return
        if (loading) return
        loading = true
        failed = false
        try {
            val next = withContext(dispatcher) { queries.page(owner, filter, cursor) }
            snapshot = current.copy(page = GamePage((current.page.games + next.games).distinctBy { it.id }, next.next))
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
}

@Composable
internal fun rememberPagedGameList(queries: GameListReader, owner: String?, filter: GameListFilter, revision: Any?, saved: List<SavedGameFilter>): PagedGameListState {
    var databaseRevision by remember(queries) { mutableStateOf(0L) }
    LaunchedEffect(queries) { queries.changes().collect { databaseRevision++ } }
    // 別アカウント・別条件の非同期結果を、現在の一覧へ流し込まない。
    val state = remember(queries, owner, filter, revision, saved, databaseRevision) { PagedGameListState(queries, owner, filter) }
    LaunchedEffect(state) { state.refresh(saved) }
    return state
}
