package dev.miyado.shogisupplement.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ShogiBoard.legalMoves() の単体テスト。
 *
 * 検証範囲:
 * - 駒の動き（各駒種の移動先）
 * - 自玉が取られる手を除外（自玉が取られる手の禁止）
 * - 打ち手の二歩
 * - 打ち手の行き所のない駒（歩・香の1段目禁止、桂の1-2段目禁止）
 */
class LegalMoveTest {

    // ─── fromSfen 基本テスト ──────────────────────────────────────────────────

    @Test
    fun `fromSfen - 平手初期局面と一致する`() {
        val board = ShogiBoard.fromSfen(
            "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1",
        )
        assertEquals(
            "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1",
            board.toSfen(),
        )
    }

    @Test
    fun `fromSfen - 手数が保持される`() {
        val board = ShogiBoard.fromSfen(
            "ln2g3l/2ks1s3/1pppppnr1/p7p/5gpp1/P1P4RP/1PSPPSP2/1KGG5/LN5NL b BPbp 41",
        )
        assertEquals(
            "ln2g3l/2ks1s3/1pppppnr1/p7p/5gpp1/P1P4RP/1PSPPSP2/1KGG5/LN5NL b BPbp 41",
            board.toSfen(),
        )
    }

    @Test
    fun `fromSfen - push後にSFENが正しく更新される`() {
        val board = ShogiBoard.fromSfen(
            "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1",
        )
        board.push(ShogiMove.fromUsi("7g7f"))
        // 手数が2に増える
        assertTrue(board.toSfen().endsWith(" 2"), "move number should be 2: ${board.toSfen()}")
    }

    // ─── 合法手数テスト ───────────────────────────────────────────────────────

    @Test
    fun `初期局面の合法手は30手`() {
        val board = ShogiBoard()
        // 将棋初期局面の合法手は30手（各歩7枚×1+飛1+角1+桂2=30）
        val moves = board.legalMoves()
        assertEquals(30, moves.size, "Initial position should have 30 legal moves, got: ${moves.size}")
    }

    @Test
    fun `合法手は自駒を取る手を含まない`() {
        val board = ShogiBoard()
        val moves = board.legalMoves()
        for (m in moves) {
            if (m.from != null) {
                val dest = board.pieceAt(m.to)
                if (dest != null) {
                    assertFalse(
                        dest.side == board.turn,
                        "Illegal move captures own piece: $m",
                    )
                }
            }
        }
    }

    // ─── 自玉チェックの禁止 ──────────────────────────────────────────────────

    @Test
    fun `自玉が取られる手は合法手に含まれない`() {
        // 飛車直射: 先手玉9i、後手飛車9a（9筋を縦に利かせる）、後手玉1a
        // 先手玉は 8i, 8h には動けるが 9h は飛車の利き上なので不可
        val board = ShogiBoard.fromSfen(
            "r7k/9/9/9/9/9/9/9/K8 b - 1",
        )
        val moves = board.legalMoves()
        // 先手玉 9i は 8i, 8h には動けるが 9h は後手飛の利きで不可
        // 9h は飛車の利き上にある
        val kingSq = ShogiSquare(9, 9)
        val illegalSq = ShogiSquare(9, 8) // 飛車の利き上（file=9, rank=8 = 9h）
        val moveToIllegal = moves.any { it.from == kingSq && it.to == illegalSq }
        assertFalse(moveToIllegal, "King should not move to sq attacked by rook: moves=$moves")
    }

    // ─── 二歩テスト ──────────────────────────────────────────────────────────

    @Test
    fun `二歩は打てない`() {
        // 先手が7g歩を持つ局面で7筋に歩がある → 7筋に打つ手が生成されない
        // 平手からスタートし、9g歩を手駒にする局面を設定
        val board = ShogiBoard.fromSfen(
            "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPP1/1B5R1/LNSGKGSNL b P 1",
        )
        // file=1-9のrank=1-6 各マスへの歩打ちを確認（ただし9筋=ファイル9に元の歩あり）
        val moves = board.legalMoves()
        val pawnDrops = moves.filter { it.dropType == PieceType.PAWN }
        // file 1-8（歩がない筋）には打てる。file 9（歩がある筋）には打てない
        val illegalDrop = pawnDrops.any { it.to.file == 9 }
        assertFalse(illegalDrop, "Pawn drop in file 9 (already has pawn) should be illegal")
    }

    @Test
    fun `歩のない筋には打てる`() {
        val board = ShogiBoard.fromSfen(
            "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPP1/1B5R1/LNSGKGSNL b P 1",
        )
        val moves = board.legalMoves()
        val pawnDrops = moves.filter { it.dropType == PieceType.PAWN }
        // file 1-8 (rank 2-7 範囲内のマス) には打てる
        val file1Drops = pawnDrops.filter { it.to.file == 1 }
        assertTrue(file1Drops.isNotEmpty(), "Pawn drop in file 1 (no pawn) should be possible")
    }

    // ─── 行き所のない駒 ──────────────────────────────────────────────────────

    @Test
    fun `歩は先手の1段目に打てない`() {
        val board = ShogiBoard.fromSfen(
            "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPP2/1B5R1/LNSGKGSNL b PP 1",
        )
        val moves = board.legalMoves()
        val pawnDropsRank1 = moves.filter { it.dropType == PieceType.PAWN && it.to.rank == 1 }
        assertTrue(pawnDropsRank1.isEmpty(), "Pawn should not be dropped on rank 1 for Black")
    }

    @Test
    fun `桂馬は先手の1-2段目に打てない`() {
        val board = ShogiBoard.fromSfen(
            "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b N 1",
        )
        val moves = board.legalMoves()
        val knightDropsRank12 = moves.filter {
            it.dropType == PieceType.KNIGHT && it.to.rank in 1..2
        }
        assertTrue(knightDropsRank12.isEmpty(), "Knight should not be dropped on rank 1-2 for Black")
    }

    @Test
    fun `桂馬は先手の3段目以降に打てる`() {
        val board = ShogiBoard.fromSfen(
            "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b N 1",
        )
        val moves = board.legalMoves()
        val knightDropsRank3Plus = moves.filter {
            it.dropType == PieceType.KNIGHT && it.to.rank >= 3
        }
        assertTrue(knightDropsRank3Plus.isNotEmpty(), "Knight should be droppable on rank 3+ for Black")
    }

    // ─── 成りテスト ──────────────────────────────────────────────────────────

    @Test
    fun `歩が敵陣に入るとき成りの選択肢が生成される`() {
        // 先手歩 7d → 7c （敵陣3段目に入る）。SFEN行は9筋→1筋の順なので7筋は"2P6"
        val board = ShogiBoard.fromSfen(
            "9/9/9/2P6/9/9/9/4K4/9 b - 1",
        )
        val from = ShogiSquare(7, 4)
        val to = ShogiSquare(7, 3)
        val moves = board.legalMovesFrom(from).filter { it.to == to }
        // 成り・不成の2択が生成される
        assertEquals(2, moves.size, "Should have 2 moves (promote and non-promote) but got: $moves")
        assertTrue(moves.any { it.promote }, "Should include promoted move")
        assertTrue(moves.any { !it.promote }, "Should include non-promoted move")
    }

    @Test
    fun `歩が先手の1段目に移動するとき成りのみ（行き所のない駒の禁止）`() {
        // 先手歩 7b → 7a （成り必須）
        val board = ShogiBoard.fromSfen(
            "9/2P6/9/9/9/9/9/4K4/9 b - 1",
        )
        val from = ShogiSquare(7, 2)
        val to = ShogiSquare(7, 1)
        val moves = board.legalMovesFrom(from).filter { it.to == to }
        // 不成は行き所がなくなるので禁止 → 成りのみ
        assertEquals(1, moves.size, "Only promoted move should be available: $moves")
        assertTrue(moves.first().promote, "The only move should be promoted")
    }

    @Test
    fun `持ち駒が複数種類あっても合法手生成できる（実機クラッシュ回帰）`() {
        // 2026-07-11実機クラッシュ: 持ち駒2種類（角・歩）の局面で legalMovesFrom を呼ぶと
        // isLegal内のpush/popが持ち駒Mapを構造変更し ConcurrentModificationException。
        // 持ち駒1種類ではキー削除後のnext()が発生せず検出できなかった
        val board = ShogiBoard.fromSfen(
            "ln2g3l/2ks1s3/1pppppnr1/p7p/5gpp1/P1P4RP/1PSPPSP2/1KGG5/LN5NL b BPbp 41",
        )
        val moves = board.legalMoves() // 例外にならないこと
        assertTrue(moves.any { it.dropType == PieceType.BISHOP }, "bishop drops should exist")
        assertTrue(moves.any { it.dropType == PieceType.PAWN }, "pawn drops should exist")

        // クラッシュ時の操作: 2fの飛車を選択
        val rookMoves = board.legalMovesFrom(ShogiSquare(2, 6))
        assertTrue(rookMoves.isNotEmpty(), "rook at 2f should have legal moves")
        assertTrue(rookMoves.any { it.toUsiString() == "2f6f" }, "best move 2f6f should be legal")
    }

    // ─── legalDropSquares テスト ──────────────────────────────────────────────

    @Test
    fun `legalDropSquares - 歩を持つ局面で打てるマスが返る`() {
        val board = ShogiBoard.fromSfen(
            "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPP1/1B5R1/LNSGKGSNL b P 1",
        )
        val squares = board.legalDropSquares(PieceType.PAWN)
        assertTrue(squares.isNotEmpty(), "Should have drop squares for pawn")
        // file 9 には打てない（二歩）
        assertTrue(squares.none { it.file == 9 }, "File 9 should not be in drop squares due to double pawn")
        // rank 1 には打てない（行き所のない駒）
        assertTrue(squares.none { it.rank == 1 }, "Rank 1 should not be in drop squares")
    }

    // ─── toUsiString テスト ───────────────────────────────────────────────────

    @Test
    fun `ShogiMove toUsiString - 通常手`() {
        val move = ShogiMove(ShogiSquare(7, 7), ShogiSquare(7, 6), promote = false)
        assertEquals("7g7f", move.toUsiString())
    }

    @Test
    fun `ShogiMove toUsiString - 成り手`() {
        val move = ShogiMove(ShogiSquare(8, 8), ShogiSquare(2, 2), promote = true)
        assertEquals("8h2b+", move.toUsiString())
    }

    @Test
    fun `ShogiMove toUsiString - 打ち手`() {
        val move = ShogiMove(from = null, to = ShogiSquare(5, 5), dropType = PieceType.PAWN)
        assertEquals("P*5e", move.toUsiString())
    }
}
