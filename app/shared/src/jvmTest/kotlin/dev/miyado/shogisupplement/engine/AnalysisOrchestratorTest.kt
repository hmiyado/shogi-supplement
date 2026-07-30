package dev.miyado.shogisupplement.engine

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.crash.NoopCrashReporter
import dev.miyado.shogisupplement.db.GameRepository
import dev.miyado.shogisupplement.db.ShogiSupplementDatabase
import dev.miyado.shogisupplement.judge.CoefficientTable
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class AnalysisOrchestratorTest {

    private class FakeEngine : Engine {
        override fun analyze(moves: List<String>, nodes: Int): List<PvInfo> = listOf(
            PvInfo(multipv = 1, score = Score.Cp(0), pv = emptyList(), nodes = 0L),
            PvInfo(multipv = 2, score = Score.Cp(0), pv = emptyList(), nodes = 0L),
        )

        override fun analyzeSfen(sfen: String, additionalMoves: List<String>, nodes: Int): List<PvInfo> =
            analyze(additionalMoves, nodes)

        override fun quit() = Unit
        override fun newGame() = Unit
    }

    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader.getResourceAsStream(name)) { "resource not found: $name" }
            .readBytes().decodeToString()

    private fun newOrchestrator(): Pair<AnalysisOrchestrator, GameRepository> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShogiSupplementDatabase.Schema.create(driver)
        val repository = GameRepository(ShogiSupplementDatabase(driver))
        val coefTable = CoefficientTable.fromJson(resource(CoefficientTable.COEFFICIENTS_FILE_NAME))
        val orchestrator = AnalysisOrchestrator(
            repository = repository,
            coefTable = coefTable,
            analyzer = AnalysisRunner(
                workers = 1,
                crashReporter = NoopCrashReporter,
                engineFactory = { FakeEngine() },
            ),
        )
        return orchestrator to repository
    }

    private fun singleMoveKif(headerLines: List<String>): String =
        (headerLines + listOf("手数----指手---------消費時間--", "   1 ７六歩(77)")).joinToString("\n")

    private fun analyzeAndGetSourcePlace(kif: String, fileName: String): String? = runBlocking {
        val (orchestrator, repository) = newOrchestrator()
        val outcome = orchestrator.analyzeAndSave(kif, fileName = fileName)
        val completed = outcome as? AnalysisOrchestrator.Outcome.Completed
            ?: fail("解析に失敗した: ${(outcome as AnalysisOrchestrator.Outcome.Failed).message}")
        repository.getGameById(completed.gameId)?.sourcePlace
    }

    @Test
    fun `wars判定は保存経路でもsource_placeにwarsとして保存される`() {
        val kif = singleMoveKif(listOf("場所：将棋ウォーズ", "手合割：平手", "先手：太郎", "後手：花子"))
        assertEquals("wars", analyzeAndGetSourcePlace(kif, "wars.kif"))
    }

    @Test
    fun `lishogi判定は保存経路でもsource_placeにlishogiとして保存される（場所の生URLは残らない）`() {
        val kif = singleMoveKif(
            listOf("場所：https://lishogi.org/abcd1234", "手合割：平手", "先手：太郎", "後手：花子"),
        )
        val sourcePlace = analyzeAndGetSourcePlace(kif, "lishogi.kif")
        assertEquals("lishogi", sourcePlace)
    }

    @Test
    fun `kiou判定は保存経路でもsource_placeにkiouとして保存される`() {
        val kif = singleMoveKif(
            listOf("# ----  KIF形式  ----", "手合割：平手", "先手：太郎", "後手：花子"),
        )
        assertEquals("kiou", analyzeAndGetSourcePlace(kif, "kiou.kif"))
    }

    @Test
    fun `場所ヘッダも棋桜マーカーも無ければ保存経路でもsource_placeにotherとして保存される`() {
        val kif = singleMoveKif(listOf("手合割：平手", "先手：太郎", "後手：花子"))
        assertEquals("other", analyzeAndGetSourcePlace(kif, "other.kif"))
    }

    /** pv1/pv2を区別して返すFakeEngine。plyごとに異なる値を返し、先手視点への正規化を検証する。 */
    private class PvAwareFakeEngine : Engine {
        override fun analyze(moves: List<String>, nodes: Int): List<PvInfo> = if (moves.isEmpty()) {
            listOf(
                PvInfo(multipv = 1, score = Score.Cp(100), pv = listOf("7g7f"), nodes = 0L),
                PvInfo(multipv = 2, score = Score.Cp(50), pv = listOf("2g2f"), nodes = 0L),
            )
        } else {
            listOf(
                PvInfo(multipv = 1, score = Score.Cp(10), pv = listOf("3c3d"), nodes = 0L),
                PvInfo(multipv = 2, score = Score.Cp(30), pv = listOf("8c8d"), nodes = 0L),
            )
        }

        override fun analyzeSfen(sfen: String, additionalMoves: List<String>, nodes: Int): List<PvInfo> =
            analyze(additionalMoves, nodes)

        override fun quit() = Unit
        override fun newGame() = Unit
    }

    private fun newPvAwareOrchestrator(): Triple<AnalysisOrchestrator, GameRepository, ShogiSupplementDatabase> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShogiSupplementDatabase.Schema.create(driver)
        val database = ShogiSupplementDatabase(driver)
        val repository = GameRepository(database)
        val coefTable = CoefficientTable.fromJson(resource(CoefficientTable.COEFFICIENTS_FILE_NAME))
        val orchestrator = AnalysisOrchestrator(
            repository = repository,
            coefTable = coefTable,
            analyzer = AnalysisRunner(
                workers = 1,
                crashReporter = NoopCrashReporter,
                engineFactory = { PvAwareFakeEngine() },
            ),
        )
        return Triple(orchestrator, repository, database)
    }

    @Test
    fun `解析後にposition_evalへbest_usiとpv2評価値が保存される`() {
        val (orchestrator, _, database) = newPvAwareOrchestrator()
        val kif = singleMoveKif(listOf("手合割：平手", "先手：太郎", "後手：花子"))
        val outcome = runBlocking { orchestrator.analyzeAndSave(kif, fileName = "pv.kif") }
        val completed = outcome as? AnalysisOrchestrator.Outcome.Completed
            ?: fail("解析に失敗した: ${(outcome as AnalysisOrchestrator.Outcome.Failed).message}")

        val rows = database.shogiSupplementQueries.getPositionEvalsByGameId(completed.gameId).executeAsList()
        val ply0 = rows.first { it.ply == 0L }
        assertEquals("7g7f", ply0.best_usi)
        assertEquals(50L, ply0.second_score_cp)
    }

    @Test
    fun `後手番の局面ではpv2評価値が先手視点へ反転されて保存される`() {
        val (orchestrator, _, database) = newPvAwareOrchestrator()
        val kif = singleMoveKif(listOf("手合割：平手", "先手：太郎", "後手：花子"))
        val outcome = runBlocking { orchestrator.analyzeAndSave(kif, fileName = "pv-flip.kif") }
        val completed = outcome as? AnalysisOrchestrator.Outcome.Completed
            ?: fail("解析に失敗した: ${(outcome as AnalysisOrchestrator.Outcome.Failed).message}")

        val rows = database.shogiSupplementQueries.getPositionEvalsByGameId(completed.gameId).executeAsList()
        val ply1 = rows.first { it.ply == 1L }
        // ply=1(後手)はsecond_score_cp=30を返すが、先手視点正規化のため保存値は-30に反転する。
        assertEquals("3c3d", ply1.best_usi)
        assertEquals(-30L, ply1.second_score_cp)
    }
}
