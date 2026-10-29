package dev.miyado.shogisupplement.kifu

import kotlin.test.Test
import kotlin.test.assertEquals

class KifPositionNotesTest {
    @Test
    fun terminalNotesWithCrLfBelongToTheLastPlayedPosition() {
        val game = KifParser().parse("1 ７六歩(77)\r\n2 投了\r\n*終局後  \r\n&振り返り\r\n")
        assertEquals(listOf("7g7f"), game.moves)
        assertEquals(
            mapOf(1 to KifuPositionNotes(listOf("終局後  "), listOf("振り返り"))),
            game.positionNotes,
        )
    }

    @Test
    fun notesBelongToThePositionAfterThePreviousMove() {
        val game = KifParser().parse("""
            手合割：平手
            *開始局面
            &開始
            手数----指手---------消費時間--
            1 ７六歩(77)
            *一行目
            *
            *二行目
            &確認する
            &別のしおり
            2 ３四歩(33)
            *後手の応手
            3 投了
        """.trimIndent())
        assertEquals(KifuPositionNotes(listOf("開始局面"), listOf("開始")), game.positionNotes[0])
        assertEquals(KifuPositionNotes(listOf("一行目", "", "二行目"), listOf("確認する", "別のしおり")), game.positionNotes[1])
        assertEquals(listOf("後手の応手"), game.positionNotes[2]?.comments)
        assertEquals(listOf("7g7f", "3c3d"), game.moves)
    }

    @Test
    fun variationNotesDoNotLeakIntoTheMainLine() {
        val game = KifParser().parse("1 ７六歩(77)\n*本譜\n変化：1手\n1 ２六歩(27)\n*分岐")
        assertEquals(listOf("本譜"), game.positionNotes[1]?.comments)
    }

    @Test
    fun commentWhitespaceIsNotStripped() {
        val game = KifParser().parse("*  メモ  \n&  名前  ")
        assertEquals(KifuPositionNotes(listOf("  メモ  "), listOf("  名前  ")), game.positionNotes[0])
    }
}
