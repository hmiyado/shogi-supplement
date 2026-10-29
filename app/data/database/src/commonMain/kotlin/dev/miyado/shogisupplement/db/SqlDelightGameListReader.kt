package dev.miyado.shogisupplement.db

import kotlinx.coroutines.channels.awaitClose
import dev.miyado.shogisupplement.util.sha256Hex
import dev.miyado.shogisupplement.kifu.KifuSource
import dev.miyado.shogisupplement.kifu.isKnownTimeControlRule
import dev.miyado.shogisupplement.kifu.timeControlDisplayText
import dev.miyado.shogisupplement.opening.OpeningClassifier
import dev.miyado.shogisupplement.repertoire.RepertoireCodec
import dev.miyado.shogisupplement.repertoire.displayLabels
import dev.miyado.shogisupplement.repertoire.positionKeys

/** 索引は端末内だけの派生データ。更新トリガーで無効化し、未処理分だけ再作成する。 */
class SqlDelightGameListReader(
    private val database: ShogiSupplementDatabase,
    private val games: GameRepository,
) : GameListReader {
    private val queries = database.gameListQueries

    override fun changes(): kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.callbackFlow {
        val watched = listOf(database.shogiSupplementQueries.getAllGames(), database.repertoireQueries.entries(""),
            database.shogiSupplementQueries.getBlunderCountAll())
        val listener = app.cash.sqldelight.Query.Listener { trySend(Unit) }
        watched.forEach { it.addListener(listener) }
        trySend(Unit)
        awaitClose { watched.forEach { it.removeListener(listener) } }
    }

    override fun indexBatch(): Int = database.transactionWithResult {
        val pendingGames = queries.unindexedGames().executeAsList()
        for (row in pendingGames) {
            val game = row.toGameRecord()
            queries.clearPositionKeys(game.id)
            game.positionKeys().forEach { queries.insertPositionKey(game.id, sha256Hex(it)) }
            queries.clearOpeningKeys(game.id)
            game.openingTagList().forEach { queries.insertOpeningKey(game.id, it) }
            val display = timeControlDisplayText(game.sourcePlace, game.timeControlRaw, game.timeControlByoyomiRaw)
            val timeControl = display?.let {
                if (isKnownTimeControlRule(game.sourcePlace, game.timeControlRaw, game.timeControlByoyomiRaw)) it else TIME_CONTROL_OTHER
            }
            queries.markIndexed(game.id, timeControl, game.sourcePlace)
            queries.finishGameIndex(game.id)
        }
        val labels = queries.unindexedLabels().executeAsList()
        for (entry in labels) {
            queries.clearLabelIndex(entry.owner, entry.id)
            if (entry.kind == "labels") {
                val value = RepertoireCodec.labels(entry.payload)
                value.displayLabels().forEach { queries.insertLabelIndex(entry.owner, entry.id, sha256Hex(value.positionKey), it) }
            }
            queries.markLabelIndexed(entry.owner, entry.id)
        }
        pendingGames.size + labels.size
    }

    override fun page(owner: String?, filter: GameListFilter, after: GamePageCursor?, limit: Int): GamePage = database.transactionWithResult {
        require(limit in 1..200)
        val ids = queries.pageIds(filter.source, filter.userSide, filter.result?.name, filter.dateFrom, filter.dateTo,
            filter.openingStyle, filter.timeControl, filter.positionLabel, owner.orEmpty(), after?.analyzedAt, after?.id ?: 0, limit.toLong() + 1).executeAsList()
        val records = ids.take(limit).mapNotNull { id ->
            games.getGameById(id)?.copy(positionLabels = queries.labelsForGame(id, owner.orEmpty()).executeAsList().toSet())
        }
        GamePage(records, if (ids.size > limit) records.lastOrNull()?.let { GamePageCursor(it.analyzedAt, it.id) } else null)
    }

    override fun count(owner: String?, filter: GameListFilter): Int =
        queries.filteredCount(filter.source, filter.userSide, filter.result?.name, filter.dateFrom, filter.dateTo,
            filter.openingStyle, filter.timeControl, filter.positionLabel, owner.orEmpty()).executeAsOne().toInt()

    override fun summary(owner: String?, filter: GameListFilter): GameListSummary = database.transactionWithResult {
        val row = queries.summary(filter.source, filter.userSide, filter.result?.name, filter.dateFrom, filter.dateTo,
            filter.openingStyle, filter.timeControl, filter.positionLabel, owner.orEmpty()).executeAsOne()
        val ratings = queries.ratings(filter.source, filter.userSide, filter.result?.name, filter.dateFrom, filter.dateTo,
            filter.openingStyle, filter.timeControl, filter.positionLabel, owner.orEmpty()).executeAsList()
        GameListSummary(row.games.toInt(), row.decided.toInt(), row.wins.toInt(), row.blunders.toInt(), row.user_moves.toInt(), ratings.map { it.toInt() })
    }

    override fun facets(owner: String?): GameListFacets {
        val rows = queries.facets().executeAsList()
        val sources = rows.mapNotNull { it.source }.toSet()
        val openings = queries.openingFacets().executeAsList()
        val priority = OpeningClassifier.PRIMARY_STYLE_PRIORITY.filter { it in openings }
        return GameListFacets(
            sources = KifuSource.entries.map { it.wireValue }.filter { it in sources },
            openings = priority + (openings - priority.toSet()).sorted(),
            timeControlsBySource = rows.groupBy { it.source }.mapValues { (_, values) -> values.mapNotNull { it.time_control }.distinct() },
            hasSide = rows.any { it.user_side != null }, hasResult = rows.any { it.user_side != null && it.game_winner != null },
            knownLabels = queries.knownLabels(owner.orEmpty()).executeAsList(),
            matchingLabels = queries.matchingLabels(owner.orEmpty()).executeAsList().toSet(),
        )
    }

    override fun totalCount(): Int = queries.totalGames().executeAsOne().toInt()
    override fun pendingUploadCount(): Int = queries.pendingCount().executeAsOne().toInt()
}
