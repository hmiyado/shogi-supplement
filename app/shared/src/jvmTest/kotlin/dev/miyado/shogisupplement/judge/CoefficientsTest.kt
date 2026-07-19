package dev.miyado.shogisupplement.judge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CoefficientsTest {

    private fun loadTable(): CoefficientTable {
        val text = checkNotNull(javaClass.classLoader.getResourceAsStream("coefficients_v1.json"))
            .readBytes().decodeToString()
        return CoefficientTable.fromJson(text)
    }

    @Test
    fun `coefficients_v1がロードできる`() {
        val table = loadTable()
        assertEquals("v1", table.version)
        assertEquals(11547, table.built_from.games)
        assertEquals(5, table.band_names.size)
        assertEquals(6, table.band_edges.size)
    }

    @Test
    fun `帯1600-1899の位置的その他レートが取得できる`() {
        val table = loadTable()
        val rate = table.ratePer1000("1600-1899", "位置的・その他")
        assertEquals(40.10752029517636, rate, 1e-9)
    }

    @Test
    fun `全帯x全カテゴリのレートが正である`() {
        val table = loadTable()
        val categories = listOf("位置的・その他", "駒損（即取り）", "詰み見逃し", "駒損（タクティクス）", "玉の危険（寄せ）", "頓死")
        for (band in table.band_names) {
            for (cat in categories) {
                assertTrue(table.ratePer1000(band, cat) > 0, "band=$band cat=$cat")
            }
        }
    }

    @Test
    fun `mate_missの帯xバケットが読める`() {
        val table = loadTable()
        val rate = table.mateMissRate("1600-1899", "1手")
        assertNotNull(rate)
        assertEquals(0.06103958035288507, rate, 1e-9)
    }

    @Test
    fun `bandOfがレートから帯を引ける`() {
        val table = loadTable()
        assertEquals(0 to "<1300", table.bandOf(500))
        assertEquals(1 to "1300-1599", table.bandOf(1300))
        assertEquals(2 to "1600-1899", table.bandOf(1750))
        assertEquals(3 to "1900-2199", table.bandOf(2199))
        assertEquals(4 to "2200+", table.bandOf(2500))
    }
}
