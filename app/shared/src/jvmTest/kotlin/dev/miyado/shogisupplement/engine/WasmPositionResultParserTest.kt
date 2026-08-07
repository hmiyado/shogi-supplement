package dev.miyado.shogisupplement.engine

import dev.miyado.shogisupplement.blunder.Score
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [parseWasmPositionResult] の単体テスト（analysis-worker.js の出力形式に対するパース層）。 */
class WasmPositionResultParserTest {

    @Test
    fun `cpスコアとmultipv2をPvInfo2件に変換する`() {
        val json = """
            {"ply":3,"score":{"cp":120},"nodes":400000,"pv":["7g7f","3c3d"],
             "multipv2":{"score":{"cp":80},"pv":["2g2f"]}}
        """.trimIndent()

        val result = parseWasmPositionResult(json)

        assertEquals(3, result.ply)
        assertEquals(2, result.pvs.size)
        val pv1 = result.pvs.first { it.multipv == 1 }
        assertEquals(Score.Cp(120), pv1.score)
        assertEquals(listOf("7g7f", "3c3d"), pv1.pv)
        assertEquals(400000L, pv1.nodes)
        val pv2 = result.pvs.first { it.multipv == 2 }
        assertEquals(Score.Cp(80), pv2.score)
        assertEquals(listOf("2g2f"), pv2.pv)
    }

    @Test
    fun `mateスコアを変換する`() {
        val json = """{"ply":0,"score":{"mate":5},"nodes":1000,"pv":["5i4h"]}"""

        val result = parseWasmPositionResult(json)

        val pv1 = result.pvs.first { it.multipv == 1 }
        assertEquals(Score.Mate(5), pv1.score)
    }

    @Test
    fun `multipv2が無ければPvInfoは1件だけになる`() {
        val json = """{"ply":1,"score":{"cp":10},"nodes":1000,"pv":["7g7f"]}"""

        val result = parseWasmPositionResult(json)

        assertEquals(1, result.pvs.size)
        assertEquals(1, result.pvs.single().multipv)
    }

    @Test
    fun `scoreがnullの局面はPvInfoを1件も積まない`() {
        val json = """{"ply":2,"score":null,"pv":[]}"""

        val result = parseWasmPositionResult(json)

        assertTrue(result.pvs.isEmpty())
    }

    @Test
    fun `multipv2のnodesは常に0を積む`() {
        // analysis-worker.jsのresult.multipv2は{score,pv}のみでnodesを返さない
        // （Web版=:webAppのRawPv2と同じ形。PositionEval変換ではpv2のnodesを参照しないため実害なし）。
        val json = """
            {"ply":0,"score":{"cp":50},"nodes":400000,"pv":["7g7f"],
             "multipv2":{"score":{"cp":40},"pv":["2g2f"]}}
        """.trimIndent()

        val result = parseWasmPositionResult(json)

        val pv2 = result.pvs.first { it.multipv == 2 }
        assertEquals(0L, pv2.nodes)
    }
}
