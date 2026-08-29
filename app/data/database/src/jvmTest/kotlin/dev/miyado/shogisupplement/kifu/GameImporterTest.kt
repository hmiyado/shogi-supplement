package dev.miyado.shogisupplement.kifu

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.miyado.shogisupplement.db.GameAnalysisStatus
import dev.miyado.shogisupplement.db.ShogiSupplementDatabase
import dev.miyado.shogisupplement.db.SqlDelightGameRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GameImporterTest {

    @Test
    fun `KIFを未解析状態で保存し同じ棋譜は重複登録しない`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShogiSupplementDatabase.Schema.create(driver)
        val repository = SqlDelightGameRepository(ShogiSupplementDatabase(driver))
        val importer = GameImporter(repository)
        val kif = """
            手合割：平手
            先手：miyado
            後手：相手
            手数----指手---------消費時間--
            1 ７六歩(77)
            2 ３四歩(33)
            3 投了
        """.trimIndent()

        val first = assertIs<GameImporter.Outcome.Imported>(
            importer.importGame(kif, "game.kif", "sente"),
        )
        val second = assertIs<GameImporter.Outcome.Imported>(
            importer.importGame(kif, "game.kif", "sente"),
        )

        assertEquals(false, first.alreadyExisted)
        assertEquals(true, second.alreadyExisted)
        assertEquals(first.gameId, second.gameId)
        assertEquals(GameAnalysisStatus.PENDING, repository.getGameById(first.gameId)?.analysisStatus)
        assertEquals(1, repository.getAllGames().size)
    }

    @Test
    fun `将棋クエストのKIFはquestと判定され対局者名からレートが分離される`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShogiSupplementDatabase.Schema.create(driver)
        val repository = SqlDelightGameRepository(ShogiSupplementDatabase(driver))
        val importer = GameImporter(repository)
        val kif = """
            棋戦：Shogi Quest
            手合割：平手
            先手：相手A(464)
            後手：miyado(800)
            手数----指手---------消費時間--
            1 ７六歩(77)
            2 ３四歩(33)
            3 投了
        """.trimIndent()

        val outcome = assertIs<GameImporter.Outcome.Imported>(
            importer.importGame(kif, "game.kif", "gote"),
        )
        val game = repository.getGameById(outcome.gameId)
        assertEquals("shogi_quest", game?.sourcePlace)
        assertEquals("相手A", game?.senteName)
        assertEquals(464L, game?.senteRating)
    }

    @Test
    fun `持ち時間ヘッダのあるKIFは持ち時間ルールが判定され未解析状態でも保存される`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShogiSupplementDatabase.Schema.create(driver)
        val repository = SqlDelightGameRepository(ShogiSupplementDatabase(driver))
        val importer = GameImporter(repository)
        val kif = """
            持ち時間：5分+30秒
            手合割：平手
            先手：miyado
            後手：相手
            手数----指手---------消費時間--
            1 ７六歩(77)
            2 ３四歩(33)
            3 投了
        """.trimIndent()

        val outcome = assertIs<GameImporter.Outcome.Imported>(
            importer.importGame(kif, "game.kif", "sente"),
        )
        val game = repository.getGameById(outcome.gameId)
        assertEquals("fischer", game?.timeControlKind)
        assertEquals(5L, game?.timeControlBaseMinutes)
        assertEquals(30L, game?.timeControlIncrementSeconds)
    }
}
