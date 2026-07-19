package dev.miyado.shogisupplement.notation

import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.board.Side
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.board.ShogiSquare

/**
 * USI 指し手＋指す直前の盤面から日本将棋連盟式棋譜表記文字列を生成する。
 *
 * 例: "8h2b+" + 平手初期盤面 → "▲２二角成"
 *
 * 仕様（日本将棋連盟の棋譜表記ルール）:
 * - 手番記号: 先手=▲ / 後手=△
 * - 筋: 全角数字（１〜９）
 * - 段: 漢数字（一〜九）
 * - 同: 直前の着手先と同じマスへの着手は「同　銀」形式（同の後は全角スペース）
 * - 成 / 不成（不成は成れる局面で成らなかった場合のみ付ける）
 * - 打（盤上の同種駒が同じ到達地点に移動できる場合のみ付ける）
 * - 曖昧性解消: 同種駒が複数到達できる場合に「左」「右」「直」「上」「引」「寄」を付ける
 *   （後手は盤を反転して先手視点で判定）
 *
 * ## 曖昧性解消の優先順位（日本将棋連盟ルール）
 *
 * 1. **動作**（上/引/寄）が候補内で一意なら動作のみで区別する
 *    - 前進（敵陣方向）→ 上、後退 → 引、横 → 寄
 * 2. 動作で区別できない場合は**位置**（左/直/右）で区別する
 *    - まっすぐ前進（同筋前進）→ 直。ただし**飛・角・馬・龍には「直」を使わない**
 *    - 先手視点で小ファイル（1筋側）= 右、大ファイル（9筋側）= 左。
 *      後手は盤を反転して判定（10 - file が正規化ファイル）
 * 3. 3枚以上で位置だけでも一意にならない場合は位置＋動作の複合（右上/左引 等）
 */
object JapaneseNotation {

    // ─── 変換テーブル ─────────────────────────────────────────────────────────

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

    // ─── メイン変換 ──────────────────────────────────────────────────────────

    /**
     * USI 指し手と指す直前の盤面から棋譜表記文字列を返す。
     *
     * @param usiMove USI 形式の指し手文字列（例: "8h2b+", "B*3d"）
     * @param board 指す直前の盤面（副作用なし。内部では変更しない）
     * @param prevMoveTo 直前の相手の着手先（「同」表記の判定に使う。null の場合は「同」を使わない）
     */
    fun format(usiMove: String, board: ShogiBoard, prevMoveTo: ShogiSquare? = null): String {
        val move = ShogiMove.fromUsi(usiMove)
        return format(move, board, prevMoveTo)
    }

    /**
     * [ShogiMove] と指す直前の盤面から棋譜表記文字列を返す。
     */
    fun format(move: ShogiMove, board: ShogiBoard, prevMoveTo: ShogiSquare? = null): String {
        val sb = StringBuilder()

        // 手番記号
        sb.append(if (board.turn == Side.BLACK) "▲" else "△")

        // 着手先マス（または「同」）
        val sameSq = prevMoveTo != null && prevMoveTo == move.to
        if (sameSq) {
            sb.append("同　") // 「同」+ 全角スペース
        } else {
            sb.append(FILE_CHARS[move.to.file])
            sb.append(RANK_CHARS[move.to.rank])
        }

        // 動かす駒の種類（成る前の種類）
        val pieceType: PieceType
        val isDrop: Boolean

        if (move.dropType != null) {
            pieceType = move.dropType
            isDrop = true
        } else {
            val fromSq = move.from ?: error("from is null for non-drop move")
            val piece = board.pieceAt(fromSq) ?: error("No piece at $fromSq")
            pieceType = piece.type
            isDrop = false
        }

        sb.append(pieceChar(pieceType))

        if (isDrop) {
            // 「打」: 盤上の同種駒が同じマスに移動できる合法手が存在する場合のみ付ける
            val dropSide = board.turn
            val needsDrop = board.legalMoves().any { m ->
                m.dropType == null && m.to == move.to &&
                    board.pieceAt(m.from!!)?.type == pieceType &&
                    board.pieceAt(m.from!!)?.side == dropSide
            }
            if (needsDrop) sb.append("打")
        } else {
            // 「成」「不成」
            if (move.promote) {
                sb.append("成")
            } else {
                // 成れる局面で成らなかった場合のみ「不成」を付ける
                val promoZone = if (board.turn == Side.BLACK) 1..3 else 7..9
                val canPromote = pieceType.promotable &&
                    (move.from!!.rank in promoZone || move.to.rank in promoZone)
                if (canPromote) sb.append("不成")
            }

            // 曖昧性解消
            val disambig = disambiguation(move, board, pieceType)
            if (disambig != null) sb.append(disambig)
        }

        return sb.toString()
    }

    // ─── 曖昧性解消 ─────────────────────────────────────────────────────────

    /** 「直」を使わない駒（飛・角と成駒。連盟ルールでは左右で表す）。 */
    private val NO_TATE_PIECES = setOf(
        PieceType.ROOK, PieceType.BISHOP, PieceType.PROM_ROOK, PieceType.PROM_BISHOP,
    )

    /**
     * 同種駒が複数到達できる場合の曖昧性解消文字列を返す。ライバルがなければ null。
     *
     * 正規化ファイル（nf）: 先手は物理ファイル (1-9)、後手は 10-物理ファイル。
     * これにより「右（1筋側）= nf が小さい」がどちらの手番でも一致する。
     * 正規化 rank（nr）も同様で、先手の「前」= nr 減少方向。
     */
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

        // ── 1. 動作（上/引/寄）が候補内で一意なら動作のみ ──────────────────
        val sameDirRivalNfs = rivals
            .filter { moveDirectionSuffix(nr(it.rank), toNr) == moverDir }
            .map { nf(it.file) }
        if (sameDirRivalNfs.isEmpty()) return moverDir

        // ── 2. 位置で区別: まっすぐ前進は「直」（飛・角・馬・龍を除く） ─────
        val straight = moverDir == "上" && fromNf == toNf
        if (straight && pieceType !in NO_TATE_PIECES) return "直"

        // ── 3. 同動作グループ内の左右 ───────────────────────────────────────
        return when {
            sameDirRivalNfs.all { it > fromNf } -> "右" // ライバルが全部左側（大nf）
            sameDirRivalNfs.all { it < fromNf } -> "左" // ライバルが全部右側（小nf）
            // ── 4. 3枚以上で左右だけでは決まらない場合の位置＋動作の複合 ────
            else -> {
                val pos = if (sameDirRivalNfs.count { it > fromNf } >=
                    sameDirRivalNfs.count { it < fromNf }
                ) "右" else "左"
                pos + moverDir
            }
        }
    }

    /**
     * 着地方向から上/引/寄 のいずれかを返す。先手正規化済みの nr を使う。
     * - fromNr > toNr: 前進（敵陣方向）→ 上
     * - fromNr < toNr: 後退 → 引
     * - fromNr == toNr: 横移動 → 寄
     */
    private fun moveDirectionSuffix(fromNr: Int, toNr: Int): String = when {
        fromNr > toNr -> "上"
        fromNr < toNr -> "引"
        else -> "寄"
    }
}
