package dev.miyado.shogisupplement.notation

import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.board.ShogiSquare
import dev.miyado.shogisupplement.kifu.KifParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class JapaneseNotationTest {


    @Test
    fun `先手の通常手 歩を1歩進める`() {
        val board = ShogiBoard()
        val result = JapaneseNotation.format("7g7f", board)
        assertEquals("▲７六歩", result)
    }

    @Test
    fun `後手の通常手`() {
        val board = ShogiBoard()
        board.push(ShogiMove.fromUsi("7g7f"))
        val result = JapaneseNotation.format("3c3d", board)
        assertEquals("△３四歩", result)
    }

    @Test
    fun `成り手（角成）`() {
        val board = ShogiBoard.fromSfen("4k4/9/9/9/9/9/9/1B7/4K4 b - 1")
        val result = JapaneseNotation.format("8h2b+", board)
        assertEquals("▲２二角成", result)
    }

    @Test
    fun `不成り（成れる局面で成らない場合のみ付く）`() {
        val board = ShogiBoard.fromSfen("9/9/9/2S6/9/9/9/4K4/9 b - 1")
        val result = JapaneseNotation.format("7d6c", board)
        assertEquals("▲６三銀不成", result)
    }

    @Test
    fun `敵陣外の移動は不成が付かない`() {
        val board = ShogiBoard.fromSfen("9/9/9/9/9/9/2S6/4K4/9 b - 1")
        val result = JapaneseNotation.format("7g6f", board)
        assertEquals("▲６六銀", result)
    }


    @Test
    fun `打ちが必要なケース（盤上の同種駒が同マスに到達できる）`() {
        val board = ShogiBoard.fromSfen("9/9/9/9/9/9/9/7R1/4K4 b R 1")
        val result = JapaneseNotation.format("R*2d", board)
        assertEquals("▲２四飛打", result)
    }

    @Test
    fun `打ちが不要なケース（盤上に同種駒がない）`() {
        val board = ShogiBoard.fromSfen("9/9/9/9/9/9/9/4K4/9 b P 1")
        val result = JapaneseNotation.format("P*5e", board)
        assertEquals("▲５五歩", result)
    }


    @Test
    fun `同の表記（全角スペースあり）`() {
        val board = ShogiBoard.fromSfen("9/9/9/9/9/9/9/4K4/9 w p 1")
        val prevMoveTo = ShogiSquare(5, 5) // 直前の着手先
        val result = JapaneseNotation.format("p*5e", board, prevMoveTo)
        assertEquals("△同　歩", result)
    }

    @Test
    fun `直前着手先と異なるマスには同を使わない`() {
        val board = ShogiBoard.fromSfen("9/9/9/9/9/9/9/4K4/9 w p 1")
        val prevMoveTo = ShogiSquare(4, 5) // 別のマス
        val result = JapaneseNotation.format("p*5e", board, prevMoveTo)
        assertEquals("△５五歩", result)
    }


    @Test
    fun `右（横並びの銀が2枚、右側の銀を動かす）`() {
        val board = ShogiBoard.fromSfen("9/9/9/9/9/3S1S3/9/4K4/9 b - 1")
        val result = JapaneseNotation.format("4f5e", board)
        assertEquals("▲５五銀右", result)
    }

    @Test
    fun `左（横並びの銀が2枚、左側の銀を動かす）`() {
        val board = ShogiBoard.fromSfen("9/9/9/9/9/3S1S3/9/4K4/9 b - 1")
        val result = JapaneseNotation.format("6f5e", board)
        assertEquals("▲５五銀左", result)
    }


    @Test
    fun `上（縦並びの飛車2枚、前進する方。動作で区別でき、飛に直は使わない）`() {
        val board = ShogiBoard.fromSfen("9/9/9/4R4/9/9/9/4R4/4K4 b - 1")
        val result = JapaneseNotation.format("5h5f", board)
        assertEquals("▲５六飛上", result)
    }

    @Test
    fun `引（縦並びの飛車2枚、後退する方）`() {
        val board = ShogiBoard.fromSfen("9/9/9/4R4/9/9/9/4R4/4K4 b - 1")
        val result = JapaneseNotation.format("5d5f", board)
        assertEquals("▲５六飛引", result)
    }


    @Test
    fun `寄（横に寄る金、斜め配置のライバルと区別）`() {
        val board = ShogiBoard.fromSfen("9/9/9/9/9/9/4G4/3G5/4K4 b - 1")
        val result = JapaneseNotation.format("6h5h", board)
        assertEquals("▲５八金寄", result)
    }


    @Test
    fun `直（同段の金2枚、まっすぐ前進する方。動作は両方「上」で区別不能）`() {
        val board = ShogiBoard.fromSfen("9/9/4GG3/9/9/9/9/9/4K4 b - 1")
        val result = JapaneseNotation.format("4c4b", board)
        assertEquals("▲４二金直", result)
    }

    @Test
    fun `直とペアの左（同段の金2枚、斜め前進する方）`() {
        val board = ShogiBoard.fromSfen("9/9/4GG3/9/9/9/9/9/4K4 b - 1")
        val result = JapaneseNotation.format("5c4b", board)
        assertEquals("▲４二金左", result)
    }

    @Test
    fun `後手視点の直`() {
        val board = ShogiBoard.fromSfen("4k4/9/9/9/9/9/4gg3/9/4K4 w - 1")
        val result = JapaneseNotation.format("4g4h", board)
        assertEquals("△４八金直", result)
    }


    @Test
    fun `馬2枚が異なる段・筋から同動作「上」で同マスへ利く場合は左右で区別`() {
        val board = ShogiBoard.fromSfen("9/9/9/9/9/9/6+B2/9/+B3K4 b - 1")
        assertEquals("▲５五馬左", JapaneseNotation.format("9i5e", board))
        assertEquals("▲５五馬右", JapaneseNotation.format("3g5e", board))
    }



    @Test
    fun `手番が逆の盤面で整形しても歩に曖昧性解消サフィックスが付かない（回帰）`() {
        val wrongTurnBoard = ShogiBoard.fromSfen("4k4/9/9/8p/9/8P/9/9/4K4 w - 1")
        val result = JapaneseNotation.format("1f1e", wrongTurnBoard)
        assertFalse(
            result.endsWith("上") || result.endsWith("引") || result.endsWith("寄") ||
                result.endsWith("右") || result.endsWith("左") || result.endsWith("直"),
            "歩に曖昧性解消サフィックスが付いてはいけない: $result",
        )
    }

    @Test
    fun `歩には曖昧性解消サフィックスが付かない（全KIFゲーム一括検証）`() {
        val parser = KifParser()
        val kifFiles = listOf(
            "miyado_game1.kif", "miyado_game2.kif",
            "kiou_game1.kif", "kiou_game2.kif", "kiou_game3.kif",
            "wars_game1.kif", "wars_game2.kif",
        )
        val disambigSuffixes = listOf("上", "引", "寄", "左", "右", "直")
        for (kifFile in kifFiles) {
            val kif = checkNotNull(
                javaClass.classLoader.getResourceAsStream(kifFile),
            ) { "resource not found: $kifFile" }.readBytes().decodeToString()
            val game = parser.parse(kif)
            val board = ShogiBoard()
            for (usiMove in game.moves) {
                val prevSfen = board.toSfen()
                val prevBoard = runCatching { ShogiBoard.fromSfen(prevSfen) }.getOrElse { ShogiBoard() }
                val pieceAtFrom = runCatching {
                    ShogiMove.fromUsi(usiMove).from?.let { prevBoard.pieceAt(it) }
                }.getOrNull()
                val notation = runCatching { JapaneseNotation.format(usiMove, prevBoard) }.getOrNull()
                if (pieceAtFrom?.type == dev.miyado.shogisupplement.board.PieceType.PAWN && notation != null) {
                    val hasSuffix = disambigSuffixes.any { notation.endsWith(it) }
                    assertFalse(hasSuffix, "$kifFile [$usiMove]: 歩にサフィックスが付いた: $notation")
                }
                runCatching { board.push(ShogiMove.fromUsi(usiMove)) }
            }
        }
    }

    @Test
    fun `後手視点の曖昧性解消（後手の右は大ファイル側）`() {
        val board = ShogiBoard.fromSfen("4k4/9/2s1s4/9/9/9/9/9/4K4 w - 1")
        val result7c = JapaneseNotation.format("7c6d", board)
        assertEquals("△６四銀右", result7c)
    }
}
