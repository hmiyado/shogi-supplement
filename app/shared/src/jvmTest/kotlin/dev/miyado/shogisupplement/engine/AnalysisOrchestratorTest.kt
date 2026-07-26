package dev.miyado.shogisupplement.engine

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.db.GameRepository
import dev.miyado.shogisupplement.db.ShogiSupplementDatabase
import dev.miyado.shogisupplement.judge.CoefficientTable
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * AnalysisOrchestrator の保存経路テスト。
 *
 * 実エンジンは使わず固定スコアを返す FakeEngine で解析パイプライン全体
 * （KIFパース→解析→悪手判定→DB保存）を素通しし、[GameRepository] に保存された
 * source_place が [dev.miyado.shogisupplement.kifu.KifuDecomposer.classifySource] の
 * 正規化値（wireValue）になっていることを確認する。
 *
 * 出典判定ロジック自体の網羅テスト（実KIFサンプルでの判定）は KifuStructuredCodecTest 側が
 * 担う。ここで検証したいのは「保存経路（AnalysisOrchestrator）が分解処理（KifuDecomposer）と
 * 同じ判定ロジックを実際に使っている」という配線であり、判定ロジックを再検証するものではない。
 */
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
        val coefTable = CoefficientTable.fromJson(resource("coefficients_hao_v1.json"))
        val orchestrator = AnalysisOrchestrator(
            repository = repository,
            coefTable = coefTable,
            workers = 1,
            engineFactory = { FakeEngine() },
        )
        return orchestrator to repository
    }

    // KifParser が要求する最低限のヘッダ + 1手だけのミニマムKIF。
    // KifuStructuredCodecTest の合成サンプル（lishogi判定・other判定のテスト）と同じ形。
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
}
