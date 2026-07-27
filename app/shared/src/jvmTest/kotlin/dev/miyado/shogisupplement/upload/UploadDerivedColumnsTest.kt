package dev.miyado.shogisupplement.upload

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UploadDerivedColumnsTest {

    @Test
    fun `開始日時をJSTのISO-8601に変換する`() {
        assertEquals(
            "2026-06-25T11:34:00+09:00",
            UploadDerivedColumns.parseStartedAtJst("2026/06/25 11:34"),
        )
    }

    @Test
    fun `曜日入り・1桁の月日時でも変換できる`() {
        assertEquals(
            "2026-07-05T09:05:00+09:00",
            UploadDerivedColumns.parseStartedAtJst("2026/7/5(日) 9:05"),
        )
    }

    @Test
    fun `解釈できない形式はnull`() {
        assertNull(UploadDerivedColumns.parseStartedAtJst("不明"))
        assertNull(UploadDerivedColumns.parseStartedAtJst(null))
    }

    @Test
    fun `sideに応じて段級をユーザー側と相手側へ割り付ける`() {
        val headers = mapOf("先手段級" to "三段", "後手段級" to "1級")
        assertEquals("三段", UploadDerivedColumns.rankFor(headers, "sente", own = true))
        assertEquals("1級", UploadDerivedColumns.rankFor(headers, "sente", own = false))
        assertEquals("1級", UploadDerivedColumns.rankFor(headers, "gote", own = true))
        assertEquals("三段", UploadDerivedColumns.rankFor(headers, "gote", own = false))
    }

    @Test
    fun `side未申告なら段級は割り付けない`() {
        assertNull(UploadDerivedColumns.rankFor(mapOf("先手段級" to "三段"), null, own = true))
    }
}
