package dev.miyado.shogisupplement.util

import kotlin.test.Test
import kotlin.test.assertEquals

class JstResetAtTest {

    @Test
    fun 日を跨がない変換() {
        assertEquals("7月27日 19:00", formatResetAtJst("2026-07-27T10:00:00Z"))
    }

    @Test
    fun サーバーが返す当日ちょうどのJST0時起点の変換() {
        assertEquals("7月28日 9:00", formatResetAtJst("2026-07-28T00:00:00Z"))
    }

    @Test
    fun 日を跨ぐ変換() {
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
        assertEquals("2月29日 5:00", formatResetAtJst("2028-02-28T20:00:00Z"))
    }

    @Test
    fun 平年2月末日を跨ぐ変換() {
        assertEquals("3月1日 5:00", formatResetAtJst("2026-02-28T20:00:00Z"))
    }

    @Test
    fun 世紀年のうるう年例外_1900年相当は平年扱い() {
        assertEquals("3月1日 5:00", formatResetAtJst("1900-02-28T20:00:00Z"))
    }

    @Test
    fun 世紀年のうるう年例外_2000年相当はうるう年扱い() {
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
