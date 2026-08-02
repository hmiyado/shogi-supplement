package dev.miyado.shogisupplement.pv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * PvExtender のユニットテスト。
 * - PV連結
 * - 合法手検証
 */
class PvExtenderTest {

    // ─── concatenate ───────────────────────────────────────────────────────────

    @Test
    fun `currentPvがnullのとき新手列そのものを返す`() {
        val result = PvExtender.concatenate(null, listOf("7g7f", "3c3d"))
        assertEquals("7g7f 3c3d", result)
    }

    @Test
    fun `currentPvが空文字のとき新手列そのものを返す`() {
        val result = PvExtender.concatenate("", listOf("7g7f"))
        assertEquals("7g7f", result)
    }

    @Test
    fun `currentPvがある場合は空白で連結する`() {
        val result = PvExtender.concatenate("7g7f 3c3d", listOf("2g2f", "8c8d"))
        assertEquals("7g7f 3c3d 2g2f 8c8d", result)
    }

    @Test
    fun `空白のみのcurrentPvは空扱いになる`() {
        val result = PvExtender.concatenate("   ", listOf("7g7f"))
        assertEquals("7g7f", result)
    }

    // ─── isLegalFirstMove ────────────────────────────────────────────────────────

    @Test
    fun `初期局面での合法手は検証を通過する`() {
        val initialSfen = "lnsgkgsnl/9/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1"
        assertTrue(PvExtender.isLegalFirstMove(initialSfen, "7g7f"))
    }

    @Test
    fun `初期局面での非合法手は拒否される`() {
        val initialSfen = "lnsgkgsnl/9/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1"
        assertFalse(PvExtender.isLegalFirstMove(initialSfen, "3c3d"))
    }

    @Test
    fun `不正なSFENはfalseを返す`() {
        assertFalse(PvExtender.isLegalFirstMove("invalid_sfen", "7g7f"))
    }
}
