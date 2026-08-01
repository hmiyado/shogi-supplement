package dev.miyado.shogisupplement.kifu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class KifParserTest {

    private val parser = KifParser()

    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader.getResourceAsStream(name)) { "resource not found: $name" }
            .readBytes().decodeToString()

    // ---- ゴールデンフィクスチャ: python-shogi の USI 出力と完全一致 ----

    @Test
    fun `game1 - USI手列がpython-shogiと完全一致する`() {
        val game = parser.parse(resource("miyado_game1.kif"))
        val expected = resource("miyado_game1_expected_usi.txt").trim().split(" ")
        assertEquals(74, game.moves.size)
        assertEquals(expected, game.moves)
    }

    @Test
    fun `game2 - USI手列がpython-shogiと完全一致する`() {
        val game = parser.parse(resource("miyado_game2.kif"))
        val expected = resource("miyado_game2_expected_usi.txt").trim().split(" ")
        assertEquals(100, game.moves.size)
        assertEquals(expected, game.moves)
    }

    @Test
    fun `game1 - ヘッダが読める`() {
        val game = parser.parse(resource("miyado_game1.kif"))
        assertEquals("平手", game.headers["手合割"])
        assertEquals("匿名", game.senteName)
    }

    @Test
    fun `kiou_game1 - 棋桜エクスポートのUSI手列がpython-shogiと完全一致する`() {
        // 棋桜（KIOU）アプリのKIFエクスポート実サンプル（2026-07-14 miyadoさん提供）。
        // lishogiサンプルに無いパターンを含む: 出所括弧なしの打（４五銀打）・同＋成の複合
        // （同　桂成）・成駒「と」の移動（４二と(53)）・行頭#コメント・投了での打ち切り
        val game = parser.parse(resource("kiou_game1.kif"))
        val expected = resource("kiou_game1_expected_usi.txt").trim().split(" ")
        assertEquals(103, game.moves.size)
        assertEquals(expected, game.moves)
        assertEquals("匿名", game.senteName)
        assertEquals("miyado", game.goteName)
    }

    @Test
    fun `kiou_game2 - 3分切れ負けの棋桜エクスポートも完全一致する`() {
        // 同＋飛成（同　飛成(29)→2i2c+）を含む45手の実サンプル
        val game = parser.parse(resource("kiou_game2.kif"))
        val expected = resource("kiou_game2_expected_usi.txt").trim().split(" ")
        assertEquals(45, game.moves.size)
        assertEquals(expected, game.moves)
        assertEquals("miyado", game.senteName)
        assertEquals("匿名", game.goteName)
    }

    @Test
    fun `kiou_game3 - 5分フィッシャーの棋桜エクスポートも完全一致する`() {
        // 異体字「竜」の移動（４九竜(47)→4g4i。コード内は「龍」表記のため異体字の検証）と
        // 同＋歩成を含む42手の実サンプル
        val game = parser.parse(resource("kiou_game3.kif"))
        val expected = resource("kiou_game3_expected_usi.txt").trim().split(" ")
        assertEquals(42, game.moves.size)
        assertEquals(expected, game.moves)
        assertEquals("miyado", game.goteName)
    }

    @Test
    fun `wars_game1 - 将棋ウォーズ3切れエクスポートも完全一致する`() {
        // ウォーズKIFの特徴: 「同」を使わず同地点も明示座標・投了行に消費時間つき・
        // 末尾に「まで90手で後手の勝ち」の結果行・先手段級/後手段級ヘッダ
        val game = parser.parse(resource("wars_game1.kif"))
        val expected = resource("wars_game1_expected_usi.txt").trim().split(" ")
        assertEquals(90, game.moves.size)
        assertEquals(expected, game.moves)
        assertEquals("miyado", game.senteName)
        assertEquals("初段", game.headers["先手段級"])
    }

    @Test
    fun `wars_game2 - 10秒将棋の127手ゲームがパースできる`() {
        // wars_game2.kif: 10秒将棋・127手・先手の勝ち。
        // 107手目 ▲１五歩（1f1e）に後手歩が1dに対面する形を含む（歩のサフィックスなし回帰用）。
        val game = parser.parse(resource("wars_game2.kif"))
        assertEquals(127, game.moves.size)
        assertEquals("miyado", game.senteName)
        assertEquals("匿名", game.goteName)
        assertEquals("初段", game.headers["先手段級"])
    }

    @Test
    fun `wars_game3 - 必至型mateの実局サンプルも完全一致する`() {
        // 69手後に後手のmate13（必至型・詰み筋でない）が存在した実局（mate定義検証用）。
        val game = parser.parse(resource("wars_game3.kif"))
        val expected = resource("wars_game3_expected_usi.txt").trim().split(" ")
        assertEquals(116, game.moves.size)
        assertEquals(expected, game.moves)
        assertEquals("miyado", game.goteName)
    }

    @Test
    fun `narigin_abbrev_game1 - 成銀の1文字略記「全」を含む実局も完全一致する`() {
        // 実局サンプル（2026-07-20 miyadoさん提供、対局者名は匿名化）。「５二全(42)」の
        // ように成銀・成桂・成香を「全」「圭」「杏」の1文字で略記するKIF出力への回帰用。
        val game = parser.parse(resource("narigin_abbrev_game1.kif"))
        val expected = resource("narigin_abbrev_game1_expected_usi.txt").trim().split(" ")
        assertEquals(81, game.moves.size)
        assertEquals(expected, game.moves)
        assertEquals("匿名", game.senteName)
        assertEquals("miyado", game.goteName)
    }

    @Test
    fun `narikei_abbrev_game1 - 成桂の1文字略記「圭」を含む実局も完全一致する`() {
        // 実局サンプル（2026-07-20 miyadoさん提供、対局者名は匿名化）。「４一圭(52)」の
        // ように成桂を「圭」で略記するKIF出力への回帰用。同一局に成銀の略記「全」も複数含む。
        val game = parser.parse(resource("narikei_abbrev_game1.kif"))
        val expected = resource("narikei_abbrev_game1_expected_usi.txt").trim().split(" ")
        assertEquals(118, game.moves.size)
        assertEquals(expected, game.moves)
        assertEquals("匿名", game.senteName)
        assertEquals("miyado", game.goteName)
    }

    // ---- 個別仕様 ----

    @Test
    fun `打はUSIのdrop形式になる`() {
        val kif = """
            手合割：平手
            手数----指手---------消費時間--
               1 ７六歩(77)
               2 ３四歩(33)
               3 ４五角打
        """.trimIndent()
        // 3手目に手駒の角は無いが、パーサは盤面検証をしない（変換のみ）
        val game = parser.parse(kif)
        assertEquals(listOf("7g7f", "3c3d", "B*4e"), game.moves)
    }

    @Test
    fun `同は直前の移動先で解決される`() {
        val kif = """
            手数----指手---------消費時間--
               1 ２六歩(27)
               2 ３四歩(33)
               3 ２五歩(26)
               4 ２四歩(23)
               5 同　歩(25)
        """.trimIndent()
        val game = parser.parse(kif)
        assertEquals("2e2d", game.moves.last())
    }

    @Test
    fun `成はUSI末尾プラスになる`() {
        val kif = """
            手数----指手---------消費時間--
               1 ７六歩(77)
               2 ３四歩(33)
               3 ２二角成(88)
        """.trimIndent()
        val game = parser.parse(kif)
        assertEquals("8h2b+", game.moves.last())
    }

    @Test
    fun `成銀の移動は成りではない`() {
        val kif = """
            手数----指手---------消費時間--
               1 ５五成銀(54)
        """.trimIndent()
        val game = parser.parse(kif)
        assertEquals(listOf("5d5e"), game.moves)
    }

    @Test
    fun `不成は成りにならない`() {
        val kif = """
            手数----指手---------消費時間--
               1 ２二角不成(88)
        """.trimIndent()
        val game = parser.parse(kif)
        assertEquals(listOf("8h2b"), game.moves)
    }

    @Test
    fun `消費時間 mmss 形式を秒で保持する`() {
        val kif = """
            手数----指手---------消費時間--
               1 ７六歩(77)   ( 0:02/00:00:02)
               2 ３四歩(33)   ( 1:15/00:01:15)
        """.trimIndent()
        val game = parser.parse(kif)
        assertEquals(listOf<Int?>(2, 75), game.timesSeconds)
    }

    @Test
    fun `消費時間 hmmss 形式も読める`() {
        val kif = """
            手数----指手---------消費時間--
               1 ７六歩(77)   (1:02:03/01:02:03)
        """.trimIndent()
        val game = parser.parse(kif)
        assertEquals(listOf<Int?>(3723), game.timesSeconds)
    }

    @Test
    fun `消費時間が無ければnull`() {
        val kif = """
            手数----指手---------消費時間--
               1 ７六歩(77)
        """.trimIndent()
        val game = parser.parse(kif)
        assertNull(game.timesSeconds[0])
    }

    @Test
    fun `平手以外の手合割はエラー`() {
        val kif = """
            手合割：二枚落ち
            手数----指手---------消費時間--
               1 ７六歩(77)
        """.trimIndent()
        assertFailsWith<KifuParseException> { parser.parse(kif) }
    }

    @Test
    fun `投了以降は無視される`() {
        val kif = """
            手数----指手---------消費時間--
               1 ７六歩(77)
               2 投了
        """.trimIndent()
        val game = parser.parse(kif)
        assertEquals(listOf("7g7f"), game.moves)
    }

    @Test
    fun `中断でも手前までを返す`() {
        val kif = """
            手数----指手---------消費時間--
               1 ７六歩(77)
               2 ３四歩(33)
               3 中断
        """.trimIndent()
        val game = parser.parse(kif)
        assertEquals(listOf("7g7f", "3c3d"), game.moves)
    }

    // ---- endReason / winner ----

    @Test
    fun `wars_game1 - 投了で後手勝ち（90手・偶数）`() {
        val game = parser.parse(resource("wars_game1.kif"))
        assertEquals("投了", game.endReason)
        // 90手後（偶数）: 先手が次の番 → 先手が投了 → 後手勝ち
        assertEquals("gote", game.winner)
    }

    @Test
    fun `kiou_game1 - 投了で先手勝ち（103手・奇数）`() {
        val game = parser.parse(resource("kiou_game1.kif"))
        assertEquals("投了", game.endReason)
        // 103手後（奇数）: 後手が次の番 → 後手が投了 → 先手勝ち
        assertEquals("sente", game.winner)
    }

    @Test
    fun `kiou_game2 - 投了で先手勝ち（45手・奇数）`() {
        val game = parser.parse(resource("kiou_game2.kif"))
        assertEquals("投了", game.endReason)
        assertEquals("sente", game.winner)
    }

    @Test
    fun `切れ負けで後手勝ち（2手終局・偶数）`() {
        val kif = """
            手数----指手---------消費時間--
               1 ７六歩(77)
               2 ３四歩(33)
               3 切れ負け
        """.trimIndent()
        // 2手後（偶数）: 先手が次の番 → 先手が切れ負け → 後手勝ち
        val game = parser.parse(kif)
        assertEquals("切れ負け", game.endReason)
        assertEquals("gote", game.winner)
    }

    @Test
    fun `切れ負けで先手勝ち（3手終局・奇数）`() {
        val kif = """
            手数----指手---------消費時間--
               1 ７六歩(77)
               2 ３四歩(33)
               3 ２六歩(27)
               4 切れ負け
        """.trimIndent()
        // 3手後（奇数）: 後手が次の番 → 後手が切れ負け → 先手勝ち
        val game = parser.parse(kif)
        assertEquals("切れ負け", game.endReason)
        assertEquals("sente", game.winner)
    }

    @Test
    fun `終局語なしなら endReason と winner は null`() {
        val kif = """
            手数----指手---------消費時間--
               1 ７六歩(77)
               2 ３四歩(33)
        """.trimIndent()
        val game = parser.parse(kif)
        assertNull(game.endReason)
        assertNull(game.winner)
    }

    @Test
    fun `千日手は winner が null`() {
        val kif = """
            手数----指手---------消費時間--
               1 ７六歩(77)
               2 ３四歩(33)
               3 千日手
        """.trimIndent()
        val game = parser.parse(kif)
        assertEquals("千日手", game.endReason)
        // 千日手は引き分け → winner は null
        assertNull(game.winner)
    }

    @Test
    fun `漢数字と半角数字の座標も読める`() {
        val kif = """
            手数----指手---------消費時間--
               1 7六歩(77)
               2 三四歩(33)
        """.trimIndent()
        val game = parser.parse(kif)
        assertEquals(listOf("7g7f", "3c3d"), game.moves)
    }
}
