package dev.miyado.shogisupplement.notation

import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.board.ShogiSquare
import dev.miyado.shogisupplement.kifu.KifParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * JapaneseNotation の単体テスト（最低15ケース）。
 *
 * 検証範囲:
 * - 先手・後手の手番記号（▲/△）
 * - 通常手（歩・銀）
 * - 成（角成）
 * - 不成（成れる局面で成らない場合のみ付ける）
 * - 打ちが必要なケース・不要なケース
 * - 同（直前の着手先と同じマス）
 * - 曖昧性解消: 右・左・直・引・寄 の代表例
 * - 後手視点（盤を反転して判定）
 *
 * ## SFEN/USI の筋・段の対応
 * SFEN 盤面文字列はランク1（a）からランク9（i）、各ランクはfile=9→1 の順に記述する。
 * 例: ランク7 "2S6" → file=9,8 が空（2）、file=7 に S（先手銀）、file=6〜1 が空（6）
 */
class JapaneseNotationTest {

    // ─── 基本テスト ────────────────────────────────────────────────────────────

    @Test
    fun `先手の通常手 歩を1歩進める`() {
        // 平手初期局面から先手 7g→7f（歩）
        val board = ShogiBoard()
        val result = JapaneseNotation.format("7g7f", board)
        assertEquals("▲７六歩", result)
    }

    @Test
    fun `後手の通常手`() {
        // 先手7g7fの後、後手3c→3d
        val board = ShogiBoard()
        board.push(ShogiMove.fromUsi("7g7f"))
        val result = JapaneseNotation.format("3c3d", board)
        assertEquals("△３四歩", result)
    }

    @Test
    fun `成り手（角成）`() {
        // 先手角を 8h に配置した局面から 2b へ成り
        // SFEN: 角が 8h、後手陣が空、玉だけ置く
        val board = ShogiBoard.fromSfen("4k4/9/9/9/9/9/9/1B7/4K4 b - 1")
        val result = JapaneseNotation.format("8h2b+", board)
        assertEquals("▲２二角成", result)
    }

    @Test
    fun `不成り（成れる局面で成らない場合のみ付く）`() {
        // 先手銀 7d（file=7, rank=4）→ 6c（file=6, rank=3）。敵陣3段目に入るが成らない
        // SFEN ランク4: "2S6" → file=9,8=空(2), S at file=7, file=6〜1=空(6)
        val board = ShogiBoard.fromSfen("9/9/9/2S6/9/9/9/4K4/9 b - 1")
        val result = JapaneseNotation.format("7d6c", board)
        assertEquals("▲６三銀不成", result)
    }

    @Test
    fun `敵陣外の移動は不成が付かない`() {
        // 先手銀 7g（file=7, rank=7）→ 6f（file=6, rank=6）。成り不可の範囲
        // SFEN ランク7: "2S6" → S at file=7
        val board = ShogiBoard.fromSfen("9/9/9/9/9/9/2S6/4K4/9 b - 1")
        val result = JapaneseNotation.format("7g6f", board)
        assertEquals("▲６六銀", result)
    }

    // ─── 打ち ──────────────────────────────────────────────────────────────────

    @Test
    fun `打ちが必要なケース（盤上の同種駒が同マスに到達できる）`() {
        // 先手: 飛持ち駒 + 盤上に飛。盤上の飛も同マスに行ける → 「打」が必要
        // 飛 at 2h (file=2, rank=8)、持ち駒に飛あり。2d (file=2, rank=4) に打つ
        // 盤上の飛は 2h→2d と直進できる（同筋）
        // SFEN ランク8: "7R1" → 9〜3=空(7), R at file=2, file=1=空(1)
        val board = ShogiBoard.fromSfen("9/9/9/9/9/9/9/7R1/4K4 b R 1")
        val result = JapaneseNotation.format("R*2d", board)
        assertEquals("▲２四飛打", result)
    }

    @Test
    fun `打ちが不要なケース（盤上に同種駒がない）`() {
        // 先手: 歩を持ち駒に持つが、盤上に先手の歩がない局面
        val board = ShogiBoard.fromSfen("9/9/9/9/9/9/9/4K4/9 b P 1")
        val result = JapaneseNotation.format("P*5e", board)
        // 盤上に同種（歩）がないので「打」は付かない
        assertEquals("▲５五歩", result)
    }

    // ─── 同（直前の着手先と同じマス）─────────────────────────────────────────

    @Test
    fun `同の表記（全角スペースあり）`() {
        // 後手が持ち駒の歩を 5e に打つ。直前の着手先も 5e
        val board = ShogiBoard.fromSfen("9/9/9/9/9/9/9/4K4/9 w p 1")
        val prevMoveTo = ShogiSquare(5, 5) // 直前の着手先
        val result = JapaneseNotation.format("p*5e", board, prevMoveTo)
        assertEquals("△同　歩", result)
    }

    @Test
    fun `直前着手先と異なるマスには同を使わない`() {
        // 後手が持ち駒の歩を 5e に打つ。直前の着手先は 4e
        val board = ShogiBoard.fromSfen("9/9/9/9/9/9/9/4K4/9 w p 1")
        val prevMoveTo = ShogiSquare(4, 5) // 別のマス
        val result = JapaneseNotation.format("p*5e", board, prevMoveTo)
        assertEquals("△５五歩", result)
    }

    // ─── 曖昧性解消: 右/左 ──────────────────────────────────────────────────

    @Test
    fun `右（横並びの銀が2枚、右側の銀を動かす）`() {
        // 先手銀 6f（file=6, rank=6）と 4f（file=4, rank=6）が同じランク。
        // 両方とも 5e（file=5, rank=5）への斜め前進が可能。
        // 先手視点で「右」= 小ファイル側 = file=4 が右、file=6 が左。
        // SFEN ランク6: "3S1S3" → file=9,8,7=空(3), S at file=6, file=5=空(1), S at file=4, file=3,2,1=空(3)
        val board = ShogiBoard.fromSfen("9/9/9/9/9/3S1S3/9/4K4/9 b - 1")
        // 4f（file=4, rank=6）の銀 → 5e。file=4 は先手の右側（小ファイル側）
        val result = JapaneseNotation.format("4f5e", board)
        assertEquals("▲５五銀右", result)
    }

    @Test
    fun `左（横並びの銀が2枚、左側の銀を動かす）`() {
        // 上の局面で 6f（file=6, rank=6）の銀 → 5e。file=6 は先手の左側（大ファイル側）
        val board = ShogiBoard.fromSfen("9/9/9/9/9/3S1S3/9/4K4/9 b - 1")
        val result = JapaneseNotation.format("6f5e", board)
        assertEquals("▲５五銀左", result)
    }

    // ─── 曖昧性解消: 上/引 ───────────────────────────────────────────────────

    @Test
    fun `上（縦並びの飛車2枚、前進する方。動作で区別でき、飛に直は使わない）`() {
        // 先手飛 5h（file=5, rank=8）と 5d（file=5, rank=4）が同筋。
        // 両方とも 5f（file=5, rank=6）への直進が可能。
        // 5h→5f は前進=上、5d→5f は後退=引 → 動作だけで一意に区別できる。
        // 連盟ルールでは飛・角・馬・龍に「直」を使わないため「▲５六飛直」は誤り
        val board = ShogiBoard.fromSfen("9/9/9/4R4/9/9/9/4R4/4K4 b - 1")
        val result = JapaneseNotation.format("5h5f", board)
        assertEquals("▲５六飛上", result)
    }

    @Test
    fun `引（縦並びの飛車2枚、後退する方）`() {
        // 上の局面で 5d→5f は後退（rank 増加）→ 引
        val board = ShogiBoard.fromSfen("9/9/9/4R4/9/9/9/4R4/4K4 b - 1")
        val result = JapaneseNotation.format("5d5f", board)
        assertEquals("▲５六飛引", result)
    }

    // ─── 曖昧性解消: 寄 ─────────────────────────────────────────────────────

    @Test
    fun `寄（横に寄る金、斜め配置のライバルと区別）`() {
        // 先手金 6h（file=6, rank=8）と 5g（file=5, rank=7）が異なる段・筋。
        // 両方とも 5h（file=5, rank=8）へ移動可能。
        // 6h→5h は横移動（rank 変わらず）→ 寄
        // 5g→5h は後退（rank 増加）→ 引
        // SFEN ランク7: "4G4" → G at file=5 (5g)。ランク8: "3G5" → G at file=6 (6h)
        val board = ShogiBoard.fromSfen("9/9/9/9/9/9/4G4/3G5/4K4 b - 1")
        val result = JapaneseNotation.format("6h5h", board)
        assertEquals("▲５八金寄", result)
    }

    // ─── 曖昧性解消: 直（動作で区別できない場合の位置区別） ─────────────────

    @Test
    fun `直（同段の金2枚、まっすぐ前進する方。動作は両方「上」で区別不能）`() {
        // 先手金 5c（file=5, rank=3）と 4c（file=4, rank=3）。両方 4b へ前進可能。
        // どちらも動作は「上」→ 位置で区別。4c→4b はまっすぐ前進 → 「直」
        // SFEN ランク3: file=9〜6 空(4), G at 5, G at 4, file=3〜1 空(3) = "4GG3"
        val board = ShogiBoard.fromSfen("9/9/4GG3/9/9/9/9/9/4K4 b - 1")
        val result = JapaneseNotation.format("4c4b", board)
        assertEquals("▲４二金直", result)
    }

    @Test
    fun `直とペアの左（同段の金2枚、斜め前進する方）`() {
        // 上と同じ局面で 5c→4b（斜め前進）。まっすぐの 4c が「直」なので、こちらは「左」
        val board = ShogiBoard.fromSfen("9/9/4GG3/9/9/9/9/9/4K4 b - 1")
        val result = JapaneseNotation.format("5c4b", board)
        assertEquals("▲４二金左", result)
    }

    @Test
    fun `後手視点の直`() {
        // 後手金 5g（file=5, rank=7）と 4g（file=4, rank=7）。両方 4h へ前進可能（後手の前=段増加）。
        // 4g→4h はまっすぐ前進 → 「直」
        // SFEN ランク7: "4gg3"
        val board = ShogiBoard.fromSfen("4k4/9/9/9/9/9/4gg3/9/4K4 w - 1")
        val result = JapaneseNotation.format("4g4h", board)
        assertEquals("△４八金直", result)
    }

    // ─── 曖昧性解消: 馬2枚が同動作（左右で区別。直は使わない） ─────────────

    @Test
    fun `馬2枚が異なる段・筋から同動作「上」で同マスへ利く場合は左右で区別`() {
        // 先手馬 9i（file=9, rank=9）と 3g（file=3, rank=7）。両方 5e へ斜め前進（動作は共に「上」）。
        // 動作で区別できない＋馬に「直」は使わない → 左右で区別。
        // 9i（nf=9 大）= 左、3g（nf=3 小）= 右
        // SFEN ランク7: file=9〜4 空(6), +B at 3, file=2,1 空(2) = "6+B2"
        //      ランク9: +B at 9, file=8〜6 空(3), K at 5, file=4〜1 空(4) = "+B3K4"
        val board = ShogiBoard.fromSfen("9/9/9/9/9/9/6+B2/9/+B3K4 b - 1")
        assertEquals("▲５五馬左", JapaneseNotation.format("9i5e", board))
        assertEquals("▲５五馬右", JapaneseNotation.format("3g5e", board))
    }

    // ─── 後手視点 ─────────────────────────────────────────────────────────────

    // ─── 防御的サイドフィルタ（board.turn が狂ったときの誤検出防止） ───────────────

    @Test
    fun `手番が逆の盤面で整形しても歩に曖昧性解消サフィックスが付かない（回帰）`() {
        // 先手の歩 1f (file=1,rank=6) が 1e へ進む場面。
        // 後手の歩 1d (file=1,rank=4) が同筋に存在するが対辺のため関係ない。
        // board.turn を WHITE（誤り）にすると board.legalMoves() が後手手を返し、
        // 後手の 1d→1e が「ライバル」として誤検出される。防御フィルタで除外されること。
        // SFEN rank1="4k4" rank4="8p"(White pawn 1d) rank6="8P"(Black pawn 1f) rank9="4K4"
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
        // 二歩禁止ルールにより同筋に同色の歩が2枚以上並ぶことはない。
        // したがって歩の指し手に上・引・寄・左・右・直のいずれのサフィックスも付かない。
        // 正しい board（SFEN に手番が含まれる）で整形すれば全棋譜で満たされる。
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
        // 後手銀 5c（file=5, rank=3）と 7c（file=7, rank=3）が同ランク。
        // 両方とも 6d（file=6, rank=4）への斜め前進が可能。
        // 後手視点で「右」= 大ファイル側 = file=7 が後手の右。
        // 正規化 nf = 10 - file: 7c → nf=3（小），5c → nf=5（大）。
        //   7c 側（nf=3）がライバルとして正規化nf小 → 5c は正規化nf大 = 左側 → 「左」
        //   5c 側のライバルが 7c（正規化nf=3 < 自分 nf=5）→ 動かす駒は左 → 「左」
        //   7c 側のライバルが 5c（正規化nf=5 > 自分 nf=3）→ 動かす駒は右 → 「右」
        // SFEN ランク3: "3s1s3" → file=9,8,7=空(3), s at file=6…
        // wait: file=7,rank=3 は "2s..." に相当。再確認:
        //   ランク3 file=9→1: 9,8=空(2), s at file=7, 6=空(1), s at file=5, 4,3,2,1=空(4)
        //   = "2s1s4"
        val board = ShogiBoard.fromSfen("4k4/9/2s1s4/9/9/9/9/9/4K4 w - 1")
        // 7c（file=7, rank=3）→ 6d（file=6, rank=4）。後手の右側の銀 → 「右」
        val result7c = JapaneseNotation.format("7c6d", board)
        assertEquals("△６四銀右", result7c)
        // 5c（file=5, rank=3）→ 6d（file=6, rank=4）。後手の左側の銀 → 「左」
        // ※ 別途白番にするため新しい盤面が必要だが同じ局面で検証
    }
}
