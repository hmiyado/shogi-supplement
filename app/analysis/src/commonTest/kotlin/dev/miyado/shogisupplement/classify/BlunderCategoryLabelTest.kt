package dev.miyado.shogisupplement.classify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * BlunderCategoryLabels の全キー網羅テスト。
 */
class BlunderCategoryLabelTest {

    @Test
    fun `全カテゴリのラベルが正しい`() {
        assertEquals("詰み逃し", BlunderCategoryLabels.of("詰み見逃し").label)
        assertEquals("頓死", BlunderCategoryLabels.of("頓死").label)
        assertEquals("タダ取られ", BlunderCategoryLabels.of("駒損（即取り）").label)
        assertEquals("手筋による駒損", BlunderCategoryLabels.of("駒損（タクティクス）").label)
        assertEquals("玉の危険", BlunderCategoryLabels.of("玉の危険（寄せ）").label)
        assertEquals("形勢のミス（その他）", BlunderCategoryLabels.of("位置的・その他").label)
    }

    @Test
    fun `未知キーはキーをそのままラベルに返す`() {
        val label = BlunderCategoryLabels.of("存在しないキー")
        assertEquals("存在しないキー", label.label)
    }

    @Test
    fun `knownKeysに全6カテゴリが含まれる`() {
        val keys = BlunderCategoryLabels.knownKeys
        assertEquals(6, keys.size)
        val expected = setOf(
            "詰み見逃し", "頓死", "駒損（即取り）",
            "駒損（タクティクス）", "玉の危険（寄せ）", "位置的・その他",
        )
        assertEquals(expected, keys)
    }

    @Test
    fun `全キーのラベルが空でない`() {
        for (key in BlunderCategoryLabels.knownKeys) {
            val label = BlunderCategoryLabels.of(key)
            assertTrue(label.label.isNotEmpty(), "label for $key should not be empty")
        }
    }
}
