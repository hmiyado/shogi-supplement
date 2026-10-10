package dev.miyado.shogisupplement.db

/** ページの取得件数が全対象の統計に影響しないよう、本文と集計を分離する。同期処理のためUIスレッドでは実行しない。 */
interface GameListReader {
    fun changes(): kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.flowOf(Unit)
    /** 0になるまで処理してから読み取る。小分けにすることで途中中断・再開できる。 */
    fun indexBatch(): Int
    fun page(owner: String?, filter: GameListFilter, after: GamePageCursor? = null, limit: Int = 40): GamePage
    fun count(owner: String?, filter: GameListFilter): Int = summary(owner, filter).games
    fun summary(owner: String?, filter: GameListFilter): GameListSummary
    fun facets(owner: String?): GameListFacets
    fun totalCount(): Int
    fun pendingUploadCount(): Int
}

data class GamePageCursor(val analyzedAt: Long, val id: Long)
data class GamePage(val games: List<GameRecord>, val next: GamePageCursor?)

data class GameListFacets(
    val sources: List<String>,
    val openings: List<String>,
    val timeControlsBySource: Map<String?, List<String>>,
    val hasSide: Boolean,
    val hasResult: Boolean,
    val knownLabels: List<String>,
    val matchingLabels: Set<String>,
) {
    fun timeControls(source: String?): List<String> {
        val values = (if (source == null) timeControlsBySource.values.flatten() else timeControlsBySource[source].orEmpty()).distinct()
        return values.filterNot { it == TIME_CONTROL_OTHER }.sorted() + values.filter { it == TIME_CONTROL_OTHER }
    }
}
