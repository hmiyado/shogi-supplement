package dev.miyado.shogisupplement.judge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 係数表 v2（Háo 版 coefficients_hao_v1.json）の同梱確認テスト。
 * タスク1: data/coefficients_hao_v1.json がテストリソースとして読み込めること。
 */
class CoefficientsHaoTest {

    private fun loadTable(): CoefficientTable {
        val text = checkNotNull(
            javaClass.classLoader.getResourceAsStream("coefficients_hao_v1.json")
        ) { "coefficients_hao_v1.json not found in test resources" }
            .readBytes().decodeToString()
        return CoefficientTable.fromJson(text)
    }

    @Test
    fun `coefficients_hao_v1がロードできる`() {
        val table = loadTable()
        assertEquals("v1", table.version)
        assertEquals(11547, table.built_from.games)
        assertEquals(5, table.band_names.size)
        assertEquals(6, table.band_edges.size)
    }

    @Test
    fun `band_edgesが正しく読める`() {
        val table = loadTable()
        assertEquals(listOf(0, 1300, 1600, 1900, 2200, 99999), table.band_edges)
    }

    @Test
    fun `帯1600-1899の駒損タクティクスレートがスポットチェック値と一致する`() {
        // タスク1 検収基準: 1600-1899帯 × 「駒損（タクティクス）」= 6.318258632431205
        val table = loadTable()
        val rate = table.ratePer1000("1600-1899", "駒損（タクティクス）")
        assertEquals(6.318258632431205, rate, 1e-9)
    }

    @Test
    fun `全帯x全カテゴリのレートが正である`() {
        val table = loadTable()
        val categories = listOf(
            "位置的・その他", "駒損（即取り）", "詰み見逃し",
            "駒損（タクティクス）", "玉の危険（寄せ）", "頓死",
        )
        for (band in table.band_names) {
            for (cat in categories) {
                assertTrue(table.ratePer1000(band, cat) > 0, "band=$band cat=$cat must be positive")
            }
        }
    }

    @Test
    fun `bandOfがHáo係数表のレートから帯を引ける`() {
        val table = loadTable()
        assertEquals(0 to "<1300", table.bandOf(500))
        assertEquals(1 to "1300-1599", table.bandOf(1300))
        assertEquals(2 to "1600-1899", table.bandOf(1750))
        assertEquals(3 to "1900-2199", table.bandOf(2100))
        assertEquals(4 to "2200+", table.bandOf(2500))
    }
}
