package dev.miyado.shogisupplement.pipeline

import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.judge.CoefficientTable
import dev.miyado.shogisupplement.kifu.KifParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Python 版との突合ゴールデンテスト。
 *
 * scripts/report_kifu.py --dump-evals で生成した評価値ダンプ (NDJSON) を入力にして
 * ReportPipeline を実行し、--out で生成したレポート JSON と完全一致を検証する。
 *
 * 検証内容:
 * - 悪手件数・手数（ply）が一致
 * - 分類カテゴリが一致
 * - 相応判定の記号（◎/○/△）が一致
 * - 相応判定の根拠数値（priority）が 1e-9 以内で一致
 * - diff_material / punish_checks / took_moved_piece / missed_mate_in が一致
 */
class GoldenTest {

    // ── Python レポート JSON のデシリアライズ用 ──────────────────────────────

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

    // ── eval dump NDJSON のデシリアライズ用 ─────────────────────────────────

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

    // ────────────────────────────────────────────────────────────────────────

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
        CoefficientTable.fromJson(resource("coefficients_hao_v1.json"))

    // ── ゴールデンテスト本体 ─────────────────────────────────────────────────

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

        // 推定値が 1750 帯（1600-1899）に収まるよう、過去累計を 619/10000 ≈ 61.9件/1000手 に固定。
        // これは Python 版 rating=1750 と同じ帯 (bandIdx) を選ばせ、相応判定が一致することを保証する。
        val ktResult = ReportPipeline.analyze(
            moves = moves,
            evals = evals,
            sides = setOf("sente", "gote"),
            coef = coef,
            prevTotalMoves = 10000,
            prevTotalBlunders = 619,
        )
        val ktReport = ktResult.reports

        // ── 悪手件数が一致 ──
        assertEquals(
            pyReport.size, ktReport.size,
            "[$kifName] 悪手件数: Python=${pyReport.size}, Kotlin=${ktReport.size}\n" +
                "Python: ${pyReport.map { it.ply }}\n" +
                "Kotlin: ${ktReport.map { it.ply }}",
        )

        // ── 並べて全件比較 ──
        val pyByPly = pyReport.sortedBy { it.ply }
        val ktByPly = ktReport.sortedBy { it.ply }

        for ((py, kt) in pyByPly.zip(ktByPly)) {
            val ctx = "[$kifName] ply=${py.ply}"

            // 手数
            assertEquals(py.ply, kt.ply, "$ctx: ply mismatch")

            // 手番
            assertEquals(py.side, kt.side, "$ctx: side mismatch")

            // 指し手 USI
            assertEquals(py.move_usi, kt.moveUsi, "$ctx: move_usi mismatch")

            // 最善手 USI
            assertEquals(py.best_usi, kt.bestUsi, "$ctx: best_usi mismatch")

            // 6カテゴリ分類
            assertEquals(py.category, kt.classification.category, "$ctx: category mismatch")

            // 差分駒損
            assertEquals(py.diff_material, kt.classification.diffMaterial, "$ctx: diff_material mismatch")

            // 相手最善応手列の王手回数
            assertEquals(py.punish_checks, kt.classification.punishChecks, "$ctx: punish_checks mismatch")

            // タダ取られ判定
            assertEquals(
                py.took_moved_piece != 0, kt.classification.tookMovedPiece,
                "$ctx: took_moved_piece mismatch",
            )

            // 詰み見逃し手数
            val pyMissedMate = py.missed_mate_in.toIntOrNull()
            assertEquals(pyMissedMate, kt.classification.missedMateIn, "$ctx: missed_mate_in mismatch")

            // 相応判定記号（◎/○/△）
            val pySymbol = py.verdict.first().toString()
            assertEquals(
                pySymbol, kt.judgement.kind.symbol,
                "$ctx: verdict symbol mismatch. Python='${py.verdict}' Kotlin='${kt.judgement.verdict}'",
            )

            // 根拠数値（priority）
            assertEquals(
                py.priority, kt.judgement.priority, 1e-9,
                "$ctx: priority mismatch. Python=${py.priority} Kotlin=${kt.judgement.priority}",
            )

            // 勝率損失（loss_wp）: Python は round(..., 3) なので 5e-4 の許容誤差
            assertEquals(
                py.loss_wp, kt.lossWp, 5e-4,
                "$ctx: loss_wp mismatch. Python=${py.loss_wp} Kotlin=${kt.lossWp}",
            )
        }
    }
}
