package dev.miyado.shogisupplement.pipeline

import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.judge.CoefficientTable
import dev.miyado.shogisupplement.kifu.KifParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GoldenTest {


    @Serializable
    private data class PyEntry(
        val ply: Int,
        val side: String,
        val move_usi: String,
        val best_usi: String? = null,
        val loss_wp: Double,
        val category: String,
        val diff_material: Int,
        val punish_checks: Int,
        val took_moved_piece: Int,
        val missed_mate_in: String,
        val verdict: String,
        val priority: Double,
    )


    @Serializable
    private data class EvalRecord(
        val file: String,
        val ply: Int,
        val score: ScoreJson? = null,
        val pv: List<String> = emptyList(),
    ) {
        @Serializable
        data class ScoreJson(
            val cp: Int? = null,
            val mate: Int? = null,
        )
    }


    private val json = Json { ignoreUnknownKeys = true }

    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader.getResourceAsStream(name)) { "resource not found: $name" }
            .readBytes().decodeToString()

    private fun loadEvals(ndjsonName: String): List<PositionEval> {
        return resource(ndjsonName).lineSequence()
            .filter { it.isNotBlank() }
            .map { line ->
                val rec = json.decodeFromString<EvalRecord>(line)
                val score: Score? = when {
                    rec.score == null -> null
                    rec.score.mate != null -> Score.Mate(rec.score.mate)
                    rec.score.cp != null -> Score.Cp(rec.score.cp)
                    else -> null
                }
                PositionEval(score, rec.pv)
            }
            .toList()
    }

    private fun loadPyReport(jsonName: String): List<PyEntry> =
        json.decodeFromString<List<PyEntry>>(resource(jsonName))

    private fun loadHaoCoef(): CoefficientTable =
        CoefficientTable.fromJson(resource(CoefficientTable.COEFFICIENTS_FILE_NAME))


    @Test
    fun `game1 - Kotlinパイプラインの出力がPythonレポートと完全一致する`() {
        runGoldenTest(
            kifName = "miyado_game1.kif",
            evalsName = "evals_game1.ndjson",
            pyReportName = "report_game1_hao.json",
        )
    }

    @Test
    fun `game2 - Kotlinパイプラインの出力がPythonレポートと完全一致する`() {
        runGoldenTest(
            kifName = "miyado_game2.kif",
            evalsName = "evals_game2.ndjson",
            pyReportName = "report_game2_hao.json",
        )
    }

    private fun runGoldenTest(
        kifName: String,
        evalsName: String,
        pyReportName: String,
    ) {
        val coef = loadHaoCoef()
        val moves = KifParser().parse(resource(kifName)).moves
        val evals = loadEvals(evalsName)
        val pyReport = loadPyReport(pyReportName)

        val ktResult = ReportPipeline.analyze(
            moves = moves,
            evals = evals,
            sides = setOf("sente", "gote"),
            coef = coef,
        )
        val ktReport = ktResult.reports

        assertEquals(
            pyReport.size, ktReport.size,
            "[$kifName] 悪手件数: Python=${pyReport.size}, Kotlin=${ktReport.size}\n" +
                "Python: ${pyReport.map { it.ply }}\n" +
                "Kotlin: ${ktReport.map { it.ply }}",
        )

        val pyByPly = pyReport.sortedBy { it.ply }
        val ktByPly = ktReport.sortedBy { it.ply }

        for ((py, kt) in pyByPly.zip(ktByPly)) {
            val ctx = "[$kifName] ply=${py.ply}"

            assertEquals(py.ply, kt.ply, "$ctx: ply mismatch")

            assertEquals(py.side, kt.side, "$ctx: side mismatch")

            assertEquals(py.move_usi, kt.moveUsi, "$ctx: move_usi mismatch")

            assertEquals(py.best_usi, kt.bestUsi, "$ctx: best_usi mismatch")

            assertEquals(py.category, kt.classification.category, "$ctx: category mismatch")

            assertEquals(py.diff_material, kt.classification.diffMaterial, "$ctx: diff_material mismatch")

            assertEquals(py.punish_checks, kt.classification.punishChecks, "$ctx: punish_checks mismatch")

            assertEquals(
                py.took_moved_piece != 0, kt.classification.tookMovedPiece,
                "$ctx: took_moved_piece mismatch",
            )

            val pyMissedMate = py.missed_mate_in.toIntOrNull()
            assertEquals(pyMissedMate, kt.classification.missedMateIn, "$ctx: missed_mate_in mismatch")

            val pySymbol = py.verdict.first().toString()
            assertEquals(
                pySymbol, kt.judgement.kind.symbol,
                "$ctx: verdict symbol mismatch. Python='${py.verdict}' Kotlin='${kt.judgement.verdict}'",
            )

            assertEquals(
                py.priority, kt.judgement.priority, 1e-9,
                "$ctx: priority mismatch. Python=${py.priority} Kotlin=${kt.judgement.priority}",
            )

            assertEquals(
                py.loss_wp, kt.lossWp, 5e-4,
                "$ctx: loss_wp mismatch. Python=${py.loss_wp} Kotlin=${kt.lossWp}",
            )
        }
    }
}
