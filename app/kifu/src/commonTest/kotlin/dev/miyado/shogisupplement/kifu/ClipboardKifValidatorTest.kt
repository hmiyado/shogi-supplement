package dev.miyado.shogisupplement.kifu

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClipboardKifValidatorTest {

    // ─── 無効ケース ──────────────────────────────────────────────────────────────

    @Test
    fun `空文字はfalse`() {
        assertFalse(ClipboardKifValidator.isValidKif(""))
    }

    @Test
    fun `空白のみはfalse`() {
        assertFalse(ClipboardKifValidator.isValidKif("   \n\t  "))
    }

    @Test
    fun `ランダムな日本語テキストはfalse`() {
        assertFalse(ClipboardKifValidator.isValidKif("今日は天気がいいですね"))
    }

    @Test
    fun `英文テキストはfalse`() {
        assertFalse(ClipboardKifValidator.isValidKif("Hello, world!"))
    }

    @Test
    fun `数字だけはfalse`() {
        assertFalse(ClipboardKifValidator.isValidKif("12345"))
    }

    // ─── 有効ケース ──────────────────────────────────────────────────────────────

    @Test
    fun `先手ヘッダありはtrue`() {
        val kif = """
            先手：miyado
            後手：相手
        """.trimIndent()
        assertTrue(ClipboardKifValidator.isValidKif(kif))
    }

    @Test
    fun `手合割と指し手ありはtrue`() {
        val kif = """
            手合割：平手
            先手：miyado
            後手：相手
            手数----指手---------消費時間--
               1 ７六歩(77)
               2 ３四歩(33)
        """.trimIndent()
        assertTrue(ClipboardKifValidator.isValidKif(kif))
    }

    @Test
    fun `指し手のみでも先後ヘッダなしは0手だが先手なし後手なしで判定される`() {
        // 手合割も先手も後手もない純粋に指し手のみの行列: KifParser は moves を返すが
        // ヘッダなし→ moves > 0 があれば true
        val kif = """
            手合割：平手
            手数----指手---------消費時間--
               1 ７六歩(77)
        """.trimIndent()
        assertTrue(ClipboardKifValidator.isValidKif(kif))
    }

    @Test
    fun `後手ヘッダのみでもtrue`() {
        val kif = "後手：テストユーザー"
        assertTrue(ClipboardKifValidator.isValidKif(kif))
    }
}
