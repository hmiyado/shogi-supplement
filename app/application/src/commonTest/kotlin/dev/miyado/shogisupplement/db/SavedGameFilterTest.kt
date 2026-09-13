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
    fun `7日条件を保存形式へ戻し、それ以外は30日へ丸める`() {
        val now = 1_000_000L
        assertEquals(
            7,
            SavedGameFilter.fromFilter("7日", GameListFilter(dateFrom = now - 7 * 86_400L), now).periodDays,
        )
        assertEquals(
            30,
            SavedGameFilter.fromFilter("全期間", GameListFilter(), now).periodDays,
        )
    }
}
