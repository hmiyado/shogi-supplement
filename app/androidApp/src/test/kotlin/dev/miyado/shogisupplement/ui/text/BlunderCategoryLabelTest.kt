package dev.miyado.shogisupplement.classify

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * BlunderCategoryLabels の全キー網羅テスト。
 */
@org.robolectric.annotation.Config(sdk = [34], application = android.app.Application::class)
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
class BlunderCategoryLabelTest {

    @Test
    fun `全カテゴリのラベルが正しい`() = kotlinx.coroutines.test.runTest {
        assertEquals("詰み逃し", BlunderCategoryLabels.of("詰み見逃し").resolveLabel())
        assertEquals("頓死", BlunderCategoryLabels.of("頓死").resolveLabel())
        assertEquals("タダ取られ", BlunderCategoryLabels.of("駒損（即取り）").resolveLabel())
        assertEquals("手筋による駒損", BlunderCategoryLabels.of("駒損（タクティクス）").resolveLabel())
        assertEquals("玉の危険", BlunderCategoryLabels.of("玉の危険（寄せ）").resolveLabel())
        assertEquals("形勢のミス（その他）", BlunderCategoryLabels.of("位置的・その他").resolveLabel())
    }

    @Test
    fun `未知キーはキーをそのままラベルに返す`() = kotlinx.coroutines.test.runTest {
        val label = BlunderCategoryLabels.of("存在しないキー")
        assertEquals("存在しないキー", label.resolveLabel())
    }

    @Test
    fun `knownKeysに全6カテゴリが含まれる`() = kotlinx.coroutines.test.runTest {
        val keys = BlunderCategoryLabels.knownKeys
        assertEquals(6, keys.size)
        val expected = setOf(
            "詰み見逃し", "頓死", "駒損（即取り）",
            "駒損（タクティクス）", "玉の危険（寄せ）", "位置的・その他",
        )
        assertEquals(expected, keys)
    }

    @Test
    fun `全キーのラベルが空でない`() = kotlinx.coroutines.test.runTest {
        for (key in BlunderCategoryLabels.knownKeys) {
            val label = BlunderCategoryLabels.of(key)
            assertTrue(label.resolveLabel().isNotEmpty(), "label for $key should not be empty")
        }
    }
}
