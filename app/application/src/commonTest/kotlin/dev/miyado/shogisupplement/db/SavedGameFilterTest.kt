package dev.miyado.shogisupplement.db

import kotlin.test.Test
import kotlin.test.assertEquals

class SavedGameFilterTest {

    @Test
    fun `保存条件は現在期間へ展開し期間比較用の条件を除く`() {
        val saved = SavedGameFilter(
            name = "ウォーズの勝ち",
            source = "wars",
            result = GameResultFilter.WIN.name,
            periodDays = 7,
        )
        assertEquals(
            GameListFilter(source = "wars", result = GameResultFilter.WIN, dateFrom = 393L),
            saved.currentFilter(now = 7 * 86_400L + 393L),
        )
        assertEquals(GameListFilter(source = "wars", result = GameResultFilter.WIN), saved.conditionFilter())
    }

    @Test
    fun `期間条件を保存形式へ戻し、期間なしは期間なしを保つ`() {
        val now = 1_000_000L
        assertEquals(
            7,
            SavedGameFilter.fromFilter("7日", GameListFilter(dateFrom = now - 7 * 86_400L), now).periodDays,
        )
        assertEquals(
            null,
            SavedGameFilter.fromFilter("全期間", GameListFilter(), now).periodDays,
        )
    }

    @Test
    fun `期間なしの保存条件は再適用時にも全期間になる`() {
        val saved = SavedGameFilter(name = "全期間", periodDays = null)
        assertEquals(GameListFilter(), saved.currentFilter(now = 1_000_000L))
    }
}
