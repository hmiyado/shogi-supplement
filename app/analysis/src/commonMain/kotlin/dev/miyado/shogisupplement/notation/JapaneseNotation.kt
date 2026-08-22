package dev.miyado.shogisupplement.notation

import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.board.Side
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.board.ShogiSquare

/**
 * USI指し手と直前の盤面から日本将棋連盟式の棋譜表記を生成する。
 * 手番記号、筋段、成、不成、打を規則に従って付加し、後手は先手視点へ正規化する。
 * 曖昧性は動作、位置、位置と動作の順に解消する。
 */
object JapaneseNotation {


    private val FILE_CHARS = arrayOf("", "１", "２", "３", "４", "５", "６", "７", "８", "９")
    private val RANK_CHARS = arrayOf("", "一", "二", "三", "四", "五", "六", "七", "八", "九")

    private fun pieceChar(type: PieceType): String = when (type) {
        PieceType.PAWN -> "歩"
        PieceType.LANCE -> "香"
        PieceType.KNIGHT -> "桂"
        PieceType.SILVER -> "銀"
        PieceType.GOLD -> "金"
        PieceType.BISHOP -> "角"
        PieceType.ROOK -> "飛"
        PieceType.KING -> "玉"
        PieceType.PROM_PAWN -> "と"
        PieceType.PROM_LANCE -> "成香"
        PieceType.PROM_KNIGHT -> "成桂"
        PieceType.PROM_SILVER -> "成銀"
        PieceType.PROM_BISHOP -> "馬"
        PieceType.PROM_ROOK -> "龍"
    }


    /** USI指し手を棋譜表記へ変換する。 @param usiMove USI形式の指し手。 @param board 直前の盤面。 @param prevMoveTo 直前の着手先。 */
    fun format(usiMove: String, board: ShogiBoard, prevMoveTo: ShogiSquare? = null): String {
        val move = ShogiMove.fromUsi(usiMove)
        return format(move, board, prevMoveTo)
    }

    fun format(move: ShogiMove, board: ShogiBoard, prevMoveTo: ShogiSquare? = null): String {
        val sb = StringBuilder()

        sb.append(if (board.turn == Side.BLACK) "▲" else "△")

        val sameSq = prevMoveTo != null && prevMoveTo == move.to
        if (sameSq) {
            sb.append("同　") // 「同」+ 全角スペース
        } else {
            sb.append(FILE_CHARS[move.to.file])
            sb.append(RANK_CHARS[move.to.rank])
        }

        val pieceType: PieceType
        val isDrop: Boolean

        // move.dropTypeは別モジュール(:kifu)のvalプロパティのため、直接参照ではスマートキャストが
        // 効かない(Kotlinの仕様: 非localなvalのスマートキャストは同一モジュール宣言が条件)。
        // ローカルvalに一度受けてからnullチェックする。
        val dropType = move.dropType
        if (dropType != null) {
            pieceType = dropType
            isDrop = true
        } else {
            val fromSq = move.from ?: error("from is null for non-drop move")
            val piece = board.pieceAt(fromSq) ?: error("No piece at $fromSq")
            pieceType = piece.type
            isDrop = false
        }

        sb.append(pieceChar(pieceType))

        if (isDrop) {
            val dropSide = board.turn
            val needsDrop = board.legalMoves().any { m ->
                m.dropType == null && m.to == move.to &&
                    board.pieceAt(m.from!!)?.type == pieceType &&
                    board.pieceAt(m.from!!)?.side == dropSide
            }
            if (needsDrop) sb.append("打")
        } else {
            if (move.promote) {
                sb.append("成")
            } else {
                val promoZone = if (board.turn == Side.BLACK) 1..3 else 7..9
                val canPromote = pieceType.promotable &&
                    (move.from!!.rank in promoZone || move.to.rank in promoZone)
                if (canPromote) sb.append("不成")
            }

            val disambig = disambiguation(move, board, pieceType)
            if (disambig != null) sb.append(disambig)
        }

        return sb.toString()
    }


    private val NO_TATE_PIECES = setOf(
        PieceType.ROOK, PieceType.BISHOP, PieceType.PROM_ROOK, PieceType.PROM_BISHOP,
    )

    /** 同種駒の曖昧性解消文字列を返す。正規化した筋と段で先後を同じ規則にする。 */
    private fun disambiguation(move: ShogiMove, board: ShogiBoard, pieceType: PieceType): String? {
        val fromSq = move.from ?: return null
        val moverSide = board.pieceAt(fromSq)?.side ?: return null

        // 同種駒で同じ到達地を持つ合法手（from 以外）を列挙。
        // moverSide でフィルタすることで board.turn が狂っていても対辺駒の誤検出を防ぐ。
        val rivals = board.legalMoves().filter { m ->
            m.dropType == null &&
                m.from != fromSq &&
                m.to == move.to &&
                board.pieceAt(m.from!!)?.type == pieceType &&
                board.pieceAt(m.from!!)?.side == moverSide
        }.map { it.from!! }.distinct()

        if (rivals.isEmpty()) return null

        val side = board.turn
        fun nf(f: Int) = if (side == Side.BLACK) f else (10 - f)
        fun nr(r: Int) = if (side == Side.BLACK) r else (10 - r)

        val fromNf = nf(fromSq.file)
        val fromNr = nr(fromSq.rank)
        val toNr = nr(move.to.rank)
        val toNf = nf(move.to.file)
        val moverDir = moveDirectionSuffix(fromNr, toNr)

        val sameDirRivalNfs = rivals
            .filter { moveDirectionSuffix(nr(it.rank), toNr) == moverDir }
            .map { nf(it.file) }
        if (sameDirRivalNfs.isEmpty()) return moverDir

        val straight = moverDir == "上" && fromNf == toNf
        if (straight && pieceType !in NO_TATE_PIECES) return "直"

        return when {
            sameDirRivalNfs.all { it > fromNf } -> "右" // ライバルが全部左側（大nf）
            sameDirRivalNfs.all { it < fromNf } -> "左" // ライバルが全部右側（小nf）
            else -> {
                val pos = if (sameDirRivalNfs.count { it > fromNf } >=
                    sameDirRivalNfs.count { it < fromNf }
                ) "右" else "左"
                pos + moverDir
            }
        }
    }

    /** 着地方向から上、引、寄のいずれかを返す。nrは先手向きに正規化済みとする。 */
    private fun moveDirectionSuffix(fromNr: Int, toNr: Int): String = when {
        fromNr > toNr -> "上"
        fromNr < toNr -> "引"
        else -> "寄"
    }
}
