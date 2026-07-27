package dev.miyado.shogisupplement.util

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [formatResetAtJst] の単体テスト。日跨ぎ・月跨ぎ・年跨ぎ・うるう年の境界値を確認する。
 */
class JstResetAtTest {

    @Test
    fun 日を跨がない変換() {
        // 10:00 UTC + 9h = 19:00 JST（同日）。
        assertEquals("7月27日 19:00", formatResetAtJst("2026-07-27T10:00:00Z"))
    }

    @Test
    fun サーバーが返す当日ちょうどのJST0時起点の変換() {
        // AnalysisService.nextQuotaResetInstant はJST日境界(0時)を起点にするため、
        // 実運用ではこの形（時刻部が00:00:00Z、日付はJSTでの当日）が中心的なケース。
        assertEquals("7月28日 9:00", formatResetAtJst("2026-07-28T00:00:00Z"))
    }

    @Test
    fun 日を跨ぐ変換() {
        // 20:00 UTC + 9h = 翌日05:00 JST。
        assertEquals("7月28日 5:00", formatResetAtJst("2026-07-27T20:00:00Z"))
    }

    @Test
    fun 月を跨ぐ変換_31日から翌月1日() {
        assertEquals("8月1日 5:00", formatResetAtJst("2026-07-31T20:00:00Z"))
    }

    @Test
    fun 月を跨ぐ変換_30日までの月から翌月1日() {
        assertEquals("5月1日 5:00", formatResetAtJst("2026-04-30T20:00:00Z"))
    }

    @Test
    fun 年を跨ぐ変換() {
        assertEquals("1月1日 5:00", formatResetAtJst("2026-12-31T20:00:00Z"))
    }

    @Test
    fun うるう年2月末日を跨ぐ変換() {
        // 2028年はうるう年（4で割り切れ、100で割り切れない）→ 2/29が存在する。
        assertEquals("2月29日 5:00", formatResetAtJst("2028-02-28T20:00:00Z"))
    }

    @Test
    fun 平年2月末日を跨ぐ変換() {
        // 2026年は平年（4で割り切れない）→ 2/28の翌日は3/1。
        assertEquals("3月1日 5:00", formatResetAtJst("2026-02-28T20:00:00Z"))
    }

    @Test
    fun 世紀年のうるう年例外_1900年相当は平年扱い() {
        // 100で割り切れるが400では割り切れない年は平年（西暦4/100/400年ルール）。
        assertEquals("3月1日 5:00", formatResetAtJst("1900-02-28T20:00:00Z"))
    }

    @Test
    fun 世紀年のうるう年例外_2000年相当はうるう年扱い() {
        // 400で割り切れる年はうるう年。
        assertEquals("2月29日 5:00", formatResetAtJst("2000-02-28T20:00:00Z"))
    }

    @Test
    fun フラクショナル秒付きの入力も解釈できる() {
        assertEquals("7月27日 19:00", formatResetAtJst("2026-07-27T10:00:00.123Z"))
    }

    @Test
    fun パース不能な入力はそのまま返す() {
        assertEquals("", formatResetAtJst(""))
        assertEquals("not-a-date", formatResetAtJst("not-a-date"))
    }
}
