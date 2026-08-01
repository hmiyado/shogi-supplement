package dev.miyado.shogisupplement.kifu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * UserSideSuggester の単体テスト（先後推定と確認省略の判定）。
 */
class UserSideSuggesterTest {

    private val accounts = setOf("miyado", "miyado_wars")

    // ─── suggest: アカウント名一致 ─────────────────────────────────────────────

    @Test
    fun `先手名がアカウント名に一致したら sente と一致フラグを返す`() {
        val s = UserSideSuggester.suggest("miyado", "匿名", accounts, lastUserSide = null)
        assertEquals("sente", s.side)
        assertTrue(s.matchedByAccount)
    }

    @Test
    fun `後手名がアカウント名に一致したら gote と一致フラグを返す`() {
        val s = UserSideSuggester.suggest("匿名", "miyado_wars", accounts, lastUserSide = null)
        assertEquals("gote", s.side)
        assertTrue(s.matchedByAccount)
    }

    @Test
    fun `両者が一致する場合は先手を優先する（従来挙動）`() {
        val s = UserSideSuggester.suggest("miyado", "miyado", accounts, lastUserSide = null)
        assertEquals("sente", s.side)
        assertTrue(s.matchedByAccount)
    }

    // ─── suggest: フォールバック ───────────────────────────────────────────────

    @Test
    fun `一致しなければ last_user_side にフォールバックし一致フラグは false`() {
        val s = UserSideSuggester.suggest("匿名A", "匿名B", accounts, lastUserSide = "gote")
        assertEquals("gote", s.side)
        assertFalse(s.matchedByAccount)
    }

    @Test
    fun `一致もフォールバックもなければ side は null`() {
        val s = UserSideSuggester.suggest("匿名A", "匿名B", accounts, lastUserSide = null)
        assertNull(s.side)
        assertFalse(s.matchedByAccount)
    }

    @Test
    fun `対局者名が null でもクラッシュしない`() {
        val s = UserSideSuggester.suggest(null, null, accounts, lastUserSide = "sente")
        assertEquals("sente", s.side)
        assertFalse(s.matchedByAccount)
    }

    // ─── shouldSkipConfirm ─────────────────────────────────────────────────────

    @Test
    fun `設定ONかつアカウント一致なら省略できる`() {
        val s = SideSuggestion("sente", matchedByAccount = true)
        assertTrue(UserSideSuggester.shouldSkipConfirm(s, skipEnabled = true))
    }

    @Test
    fun `設定OFFなら一致していても省略しない`() {
        val s = SideSuggestion("sente", matchedByAccount = true)
        assertFalse(UserSideSuggester.shouldSkipConfirm(s, skipEnabled = false))
    }

    @Test
    fun `フォールバック推定（last_user_side）では設定ONでも省略しない`() {
        val s = SideSuggestion("gote", matchedByAccount = false)
        assertFalse(UserSideSuggester.shouldSkipConfirm(s, skipEnabled = true))
    }

    @Test
    fun `side が null なら省略しない`() {
        val s = SideSuggestion(null, matchedByAccount = false)
        assertFalse(UserSideSuggester.shouldSkipConfirm(s, skipEnabled = true))
    }
}
