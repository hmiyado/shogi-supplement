package dev.miyado.shogisupplement.ui.manual

import dev.miyado.shogisupplement.kifu.KifParser
import dev.miyado.shogisupplement.ui.common.currentLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ManualKifuDraftTest {

    @Test
    fun `default local datetime uses the KIF header format`() {
        assertTrue(Regex("\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}").matches(currentLocalDateTime()))
    }

    @Test
    fun `generated kif round trips with metadata and resignation`() {
        val draft = ManualKifuDraft(
            moves = listOf("7g7f", "3c3d", "2g2f"),
            senteName = "先手さん",
            goteName = "後手さん",
            startedAt = "2026/08/20 21:45",
            place = "東京・将棋会館",
            resigned = true,
        )

        val parsed = KifParser().parse(draft.toKifText())

        assertEquals(draft.moves, parsed.moves)
        assertEquals("先手さん", parsed.headers["先手"])
        assertEquals("後手さん", parsed.headers["後手"])
        assertEquals("2026/08/20 21:45", parsed.headers["開始日時"])
        assertEquals("東京・将棋会館", parsed.headers["場所"])
        assertEquals("投了", parsed.endReason)
        assertEquals("sente", parsed.winner)
    }

    @Test
    fun `generated kif round trips a drop and promotion`() {
        val source = KifParser().parse(
            """
            手合割：平手
            手数----指手---------消費時間--
               1 ５六歩(57)
               2 ８四歩(83)
               3 ７六歩(77)
               4 ８五歩(84)
               5 ７七角(88)
               6 ６二銀(71)
               7 ５五歩(56)
               8 ３四歩(33)
               9 ５八飛(28)
              10 ７四歩(73)
              11 ６八銀(79)
              12 ７三銀(62)
              13 ５七銀(68)
              14 ６四銀(73)
              15 ６六銀(57)
              16 ４二玉(51)
              17 ４八玉(59)
              18 ３二玉(42)
              19 ３八玉(48)
              20 ４二銀(31)
              21 ２八玉(38)
              22 ３三銀(42)
              23 ３八銀(39)
              24 ４四銀(33)
              25 ５九飛(58)
              26 ５二金(61)
              27 １六歩(17)
              28 １四歩(13)
              29 ４六歩(47)
              30 ７三桂(81)
              31 ７八金(69)
              32 ９四歩(93)
              33 ９六歩(97)
              34 ４二金(52)
              35 ９八香(99)
              36 ６五桂(73)
              37 ６八角(77)
              38 ５五銀(44)
              39 同　銀(66)
              40 同　銀(64)
              41 ７七桂(89)
              42 同　桂成(65)
              43 同　角(68)
              44 ５四歩(53)
              45 ４五銀打
              46 ６五桂打
            """.trimIndent(),
        )
        val draft = ManualKifuDraft(
            moves = source.moves,
            senteName = "",
            goteName = "",
            startedAt = "",
            place = "",
            resigned = false,
        )

        val parsed = KifParser().parse(draft.toKifText())

        assertEquals(draft.moves, parsed.moves)
        assertEquals(true, draft.moves.any { it.endsWith("+") })
        assertEquals(true, draft.moves.any { it.contains("*") })
    }
}
