package dev.miyado.shogisupplement.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.miyado.shogisupplement.strength.MateOpportunities
import dev.miyado.shogisupplement.strength.MateStatistics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MateStatisticsPersistenceTest {

    @Test
    fun `保存した詰み集計は再読込と再解析と削除に追従する`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShogiSupplementDatabase.Schema.create(driver)
        val repo = SqlDelightGameRepository(ShogiSupplementDatabase(driver))
        val moves = List(4) { "m$it" }
        val id = repo.saveAnalysisAtomically(
            GameRepository.AnalysisSaveRequest(
                fileName = "mate.kif",
                contentHash = "mate-persistence",
                moves = moves,
                headers = emptyMap(),
                reports = emptyList(),
                rating = 1500,
                coefVersion = "v1",
                userSide = "sente",
                gameWinner = "sente",
                endReason = "投了",
                positionEvalRows = listOf(
                    PositionEvalRow(0, null, 5, mateVerified = true),
                    PositionEvalRow(1, null, 4, mateVerified = true),
                    PositionEvalRow(2, null, 3, mateVerified = true),
                    PositionEvalRow(3, null, 2, mateVerified = true),
                    PositionEvalRow(4, null, 1, mateVerified = true),
                ),
            ),
        )

        val first = statistics(repo)
        val firstBucket = first.buckets.single { it.plies == 5 }
        assertEquals(1, firstBucket.found)
        assertEquals(5, firstBucket.plies)
        assertEquals(1, first.analyzedGames)
        assertEquals(1, first.verifiedGames)

        val reopened = SqlDelightGameRepository(ShogiSupplementDatabase(driver))
        assertEquals(first, statistics(reopened))

        repo.savePositionEvals(
            id,
            listOf(
                PositionEvalRow(0, 0, null),
                PositionEvalRow(1, 0, null),
                PositionEvalRow(2, 0, null),
                PositionEvalRow(3, 0, null),
                PositionEvalRow(4, 0, null),
            ),
        )
        val reparsed = statistics(reopened)
        assertTrue(reparsed.buckets.isEmpty())
        assertEquals(1, reparsed.analyzedGames)
        assertEquals(0, reparsed.verifiedGames)

        repo.deleteGame(id)
        assertTrue(repo.getGamesWithUserSide().isEmpty())
        assertTrue(repo.getPositionEvals(id).isEmpty())
        val deleted = statistics(reopened)
        assertTrue(deleted.buckets.isEmpty())
        assertEquals(0, deleted.analyzedGames)
        assertEquals(0, deleted.verifiedGames)

        driver.close()
    }

    @Test
    fun `保存した未確認mateは確認済み詰みの見逃しとして分母に入る`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShogiSupplementDatabase.Schema.create(driver)
        val repo = SqlDelightGameRepository(ShogiSupplementDatabase(driver))
        repo.saveAnalysisAtomically(
            GameRepository.AnalysisSaveRequest(
                fileName = "unverified.kif",
                contentHash = "mate-unverified",
                moves = List(2) { "m$it" },
                headers = emptyMap(),
                reports = emptyList(),
                rating = 1500,
                coefVersion = "v1",
                userSide = "sente",
                gameWinner = "sente",
                endReason = "投了",
                positionEvalRows = listOf(
                    PositionEvalRow(0, null, 5, mateVerified = true),
                    PositionEvalRow(1, null, 3, mateVerified = false),
                    PositionEvalRow(2, 0, null, mateVerified = true),
                ),
            ),
        )

        val first = statistics(repo)
        val bucket = first.buckets.single { it.plies == 5 }
        assertEquals(0, bucket.found)
        assertEquals(1, bucket.missed)
        assertEquals(1, bucket.decided)
        assertEquals(0.0, bucket.successRate)
        assertEquals(0, bucket.unknown)

        val reopened = SqlDelightGameRepository(ShogiSupplementDatabase(driver))
        assertEquals(first, statistics(reopened))

        driver.close()
    }

    private fun statistics(repo: GameRepository): MateStatistics {
        val games = repo.getGamesWithUserSide()
        val evaluations = games.associate { it.id to repo.getPositionEvals(it.id) }
        return MateStatistics.aggregate(
            opportunities = games.flatMap { game ->
                MateOpportunities.compute(
                    moves = game.movesUsi,
                    evaluations = evaluations[game.id].orEmpty(),
                    userSide = game.userSide,
                    gameWinner = game.gameWinner,
                    endReason = game.endReason,
                )
            },
            analyzedGames = games.size,
            verifiedGames = evaluations.values.count { rows ->
                rows.any { it.mateVerified && it.mateIn != null }
            },
        )
    }
}
