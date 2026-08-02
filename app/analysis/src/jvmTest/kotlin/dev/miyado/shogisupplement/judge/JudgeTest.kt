package dev.miyado.shogisupplement.judge

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class JudgeTest {

    @Serializable
    private data class ReportEntry(
        val ply: Int,
        val category: String,
        val missed_mate_in: String,
        val verdict: String,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader.getResourceAsStream(name)) { "resource not found: $name" }
            .readBytes().decodeToString()

    private fun loadTable(): CoefficientTable = CoefficientTable.fromJson(resource("coefficients_v1.json"))

    /**
     * ゴールデンフィクスチャ: data/report_game2.json（Pythonパイプラインの出力）の
     * 各悪手を同一入力で判定し、verdictの種別（◎/○/△）が一致すること。
     * report_game2.json はレート1750（帯1600-1899）で生成されている。
     */
    @Test
    fun `report_game2の全悪手でverdict種別が一致する`() {
        val table = loadTable()
        val entries = json.decodeFromString<List<ReportEntry>>(resource("report_game2.json"))
        check(entries.isNotEmpty())
        val bandIndex = table.band_names.indexOf("1600-1899")

        for (entry in entries) {
            val input = JudgeInput(
                category = entry.category,
                missedMateIn = entry.missed_mate_in.toIntOrNull(),
            )
            val result = Judge.judge(input, bandIndex, table)
            val expectedSymbol = entry.verdict.first().toString()
            assertEquals(
                expectedSymbol, result.kind.symbol,
                "ply=${entry.ply} category=${entry.category} expected='${entry.verdict}' actual='${result.verdict}'",
            )
        }
    }

    // ---- ロジック単体 ----

    @Test
    fun `スイング系 - 自帯率2未満は見送り`() {
        val table = loadTable()
        // 頓死は1600-1899帯で1.86件/1000手 < 2.0
        val result = Judge.judge(JudgeInput("頓死"), bandIndex = 2, coef = table)
        assertEquals(VerdictKind.SKIP, result.kind)
    }

    @Test
    fun `スイング系 - 最上位帯比4倍以上は優先出題`() {
        val table = loadTable()
        // <1300帯の駒損（即取り）: 15.11 / 3.46 = 4.37倍 >= 4
        val result = Judge.judge(JudgeInput("駒損（即取り）"), bandIndex = 0, coef = table)
        assertEquals(VerdictKind.PRIORITY, result.kind)
    }

    @Test
    fun `スイング系 - それ以外は出題対象`() {
        val table = loadTable()
        // 1600-1899帯の位置的・その他: 40.1 / 16.1 = 2.5倍
        val result = Judge.judge(JudgeInput("位置的・その他"), bandIndex = 2, coef = table)
        assertEquals(VerdictKind.TARGET, result.kind)
    }

    @Test
    fun `詰み見逃し - 見逃し率5パーセント未満は見送り`() {
        val table = loadTable()
        // 2200+帯の1手詰は見逃し率が低い想定
        val rate = table.mateMissRate("2200+", "1手")!!
        val result = Judge.judge(JudgeInput("詰み見逃し", missedMateIn = 1), bandIndex = 4, coef = table)
        if (rate < 0.05) {
            assertEquals(VerdictKind.SKIP, result.kind)
        } else {
            assertEquals(VerdictKind.PRIORITY, result.kind)
        }
    }

    @Test
    fun `詰み見逃し - 見逃し率5〜60パーセントは優先出題`() {
        val table = loadTable()
        // 1600-1899帯の5手詰: 16.7% → ◎
        val result = Judge.judge(JudgeInput("詰み見逃し", missedMateIn = 5), bandIndex = 2, coef = table)
        assertEquals(VerdictKind.PRIORITY, result.kind)
    }

    @Test
    fun `詰み手数バケット変換`() {
        assertEquals("1手", Judge.mateBucket(1))
        assertEquals("3手", Judge.mateBucket(3))
        assertEquals("5手", Judge.mateBucket(5))
        assertEquals("7手+", Judge.mateBucket(7))
        assertEquals("7手+", Judge.mateBucket(11))
    }
}
