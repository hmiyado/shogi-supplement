package dev.miyado.shogisupplement.board

/**
 * USI 1マス: file(1-9, 右=9・左=1) × rank(1-9, 上=1・下=9)。
 * USI 文字列 "7g" → file=7, rank=7 (a=1, b=2, ..., g=7, ..., i=9)。
 */
data class ShogiSquare(val file: Int, val rank: Int) {
    companion object {
        fun fromUsi(s: String): ShogiSquare {
            require(s.length >= 2) { "Invalid USI square: $s" }
            val file = s[0].digitToIntOrNull()
                ?: error("Invalid file char: ${s[0]}")
            val rank = s[1] - 'a' + 1
            require(file in 1..9 && rank in 1..9) { "Square out of range: $s (file=$file, rank=$rank)" }
            return ShogiSquare(file, rank)
        }
    }

    override fun toString(): String = "$file${'a' + rank - 1}"
}

/** 手番: BLACK = 先手, WHITE = 後手。 */
enum class Side { BLACK, WHITE }

val Side.opposite: Side get() = if (this == Side.BLACK) Side.WHITE else Side.BLACK

/**
 * 駒の種類。materialValue は classify_blunders.py の PIECE_VALUES と同一。
 */
enum class PieceType(val materialValue: Int, val promotable: Boolean = false) {
    PAWN(1, true),
    LANCE(3, true),
    KNIGHT(4, true),
    SILVER(5, true),
    GOLD(6),
    BISHOP(8, true),
    ROOK(10, true),
    KING(0),
    PROM_PAWN(6),
    PROM_LANCE(6),
    PROM_KNIGHT(6),
    PROM_SILVER(6),
    PROM_BISHOP(10),
    PROM_ROOK(12);

    fun promoted(): PieceType = when (this) {
        PAWN -> PROM_PAWN
        LANCE -> PROM_LANCE
        KNIGHT -> PROM_KNIGHT
        SILVER -> PROM_SILVER
        BISHOP -> PROM_BISHOP
        ROOK -> PROM_ROOK
        else -> this
    }

    fun unpromoted(): PieceType = when (this) {
        PROM_PAWN -> PAWN
        PROM_LANCE -> LANCE
        PROM_KNIGHT -> KNIGHT
        PROM_SILVER -> SILVER
        PROM_BISHOP -> BISHOP
        PROM_ROOK -> ROOK
        else -> this
    }
}

/** 盤面上の駒（種類＋手番）。 */
data class ShogiPiece(val type: PieceType, val side: Side)

// ─── SFEN ヘルパー ─────────────────────────────────────────────────────────

/** SFEN 1文字（またはプレフィックス付き2文字）の駒表現を返す。Black=大文字、White=小文字。 */
internal fun ShogiPiece.toSfenString(): String {
    val base = type.sfenChar()
    val c = if (side == Side.BLACK) base.uppercaseChar() else base.lowercaseChar()
    return if (type.isPromoted()) "+$c" else c.toString()
}

/**
 * 駒の種類に対応する SFEN 基底文字（大文字）を返す。
 * 成駒は不成の文字を返す（SFEN では '+' をプレフィックスするため）。
 */
internal fun PieceType.sfenChar(): Char = when (this.unpromoted()) {
    PieceType.PAWN -> 'P'
    PieceType.LANCE -> 'L'
    PieceType.KNIGHT -> 'N'
    PieceType.SILVER -> 'S'
    PieceType.GOLD -> 'G'
    PieceType.BISHOP -> 'B'
    PieceType.ROOK -> 'R'
    PieceType.KING -> 'K'
    else -> error("Unexpected PieceType for sfenChar: $this")
}

/** 成駒かどうかを返す。 */
internal fun PieceType.isPromoted(): Boolean = when (this) {
    PieceType.PROM_PAWN, PieceType.PROM_LANCE, PieceType.PROM_KNIGHT,
    PieceType.PROM_SILVER, PieceType.PROM_BISHOP, PieceType.PROM_ROOK -> true
    else -> false
}

/** SFEN 文字 → PieceType 変換（大文字のみ）。 */
private fun sfenCharToPieceType(c: Char): PieceType? = when (c) {
    'P' -> PieceType.PAWN
    'L' -> PieceType.LANCE
    'N' -> PieceType.KNIGHT
    'S' -> PieceType.SILVER
    'G' -> PieceType.GOLD
    'B' -> PieceType.BISHOP
    'R' -> PieceType.ROOK
    'K' -> PieceType.KING
    else -> null
}

/**
 * USI 指し手。
 * - 通常手: from="7g", to="7f", promote=false
 * - 成り: from="8h", to="2b", promote=true
 * - 打ち: from=null, to="5e", dropType=PAWN
 */
data class ShogiMove(
    val from: ShogiSquare?,
    val to: ShogiSquare,
    val promote: Boolean = false,
    val dropType: PieceType? = null,
) {
    companion object {
        private fun charToPieceType(c: Char): PieceType? = when (c.uppercaseChar()) {
            'P' -> PieceType.PAWN
            'L' -> PieceType.LANCE
            'N' -> PieceType.KNIGHT
            'S' -> PieceType.SILVER
            'G' -> PieceType.GOLD
            'B' -> PieceType.BISHOP
            'R' -> PieceType.ROOK
            else -> null
        }

        /** USI 文字列 ("7g7f", "P*2e", "8h2b+") をパース。 */
        fun fromUsi(s: String): ShogiMove {
            // Drop: "P*2e"
            if (s.length >= 4 && s[1] == '*') {
                val pt = charToPieceType(s[0]) ?: error("Unknown drop piece type: ${s[0]}")
                val to = ShogiSquare.fromUsi(s.substring(2, 4))
                return ShogiMove(null, to, dropType = pt)
            }
            require(s.length >= 4) { "USI move too short: $s" }
            val from = ShogiSquare.fromUsi(s.substring(0, 2))
            val to = ShogiSquare.fromUsi(s.substring(2, 4))
            val promote = s.length > 4 && s[4] == '+'
            return ShogiMove(from, to, promote)
        }
    }

    /** USI 指し手文字列を返す（"7g7f", "8h2b+", "P*5e"）。 */
    fun toUsiString(): String = when {
        dropType != null -> "${dropType.sfenChar().uppercaseChar()}*$to"
        else -> "${from}${to}${if (promote) "+" else ""}"
    }
}

/**
 * 将棋盤（平手初期局面スタート）。
 *
 * classify_blunders.py の shogi.Board インタフェース（pieceAt / push / pop / isCheck / turn）
 * を Kotlin 移植したもの。pv_stats で使う。
 *
 * 平手初期 SFEN: `lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1`
 */
class ShogiBoard {

    // boardMap[sq] = piece（空マスは含まない）
    private val boardMap: HashMap<ShogiSquare, ShogiPiece> = HashMap(81)

    // hands[side.ordinal][pieceType] = 枚数
    private val hands: Array<HashMap<PieceType, Int>> = Array(2) { HashMap() }

    /** 現在の手番。 */
    var turn: Side = Side.BLACK
        private set

    /**
     * 手数ベース。fromSfen() で指定された局面の手数を保持し、
     * toSfen() の手数表示に使う。デフォルト 1（平手初期局面）。
     */
    private var moveNumberBase: Int = 1

    private data class MoveRecord(
        val from: ShogiSquare?,
        val to: ShogiSquare,
        val movedPiece: ShogiPiece,
        val capturedPiece: ShogiPiece?,
        val promote: Boolean,
        val dropType: PieceType?,
    )

    private val history: ArrayDeque<MoveRecord> = ArrayDeque()

    init {
        resetToInitialPosition()
    }

    companion object {
        /**
         * SFEN 文字列から局面を再現した ShogiBoard を返す。
         * 例: "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1"
         */
        fun fromSfen(sfen: String): ShogiBoard {
            val board = ShogiBoard()
            board.initFromSfen(sfen)
            return board
        }
    }

    /** 平手初期局面にリセット。 */
    fun resetToInitialPosition() {
        boardMap.clear()
        hands[0].clear()
        hands[1].clear()
        history.clear()
        turn = Side.BLACK
        moveNumberBase = 1

        val backRank = listOf(
            PieceType.LANCE, PieceType.KNIGHT, PieceType.SILVER, PieceType.GOLD,
            PieceType.KING, PieceType.GOLD, PieceType.SILVER, PieceType.KNIGHT, PieceType.LANCE,
        )
        // BLACK (先手): rank 9 (i) / 8 (h) / 7 (g)
        for ((i, pt) in backRank.withIndex()) {
            boardMap[ShogiSquare(9 - i, 9)] = ShogiPiece(pt, Side.BLACK)
        }
        boardMap[ShogiSquare(2, 8)] = ShogiPiece(PieceType.ROOK, Side.BLACK)   // 2h
        boardMap[ShogiSquare(8, 8)] = ShogiPiece(PieceType.BISHOP, Side.BLACK) // 8h
        for (f in 1..9) {
            boardMap[ShogiSquare(f, 7)] = ShogiPiece(PieceType.PAWN, Side.BLACK)
        }

        // WHITE (後手): rank 1 (a) / 2 (b) / 3 (c)
        for ((i, pt) in backRank.withIndex()) {
            boardMap[ShogiSquare(9 - i, 1)] = ShogiPiece(pt, Side.WHITE)
        }
        boardMap[ShogiSquare(8, 2)] = ShogiPiece(PieceType.ROOK, Side.WHITE)   // 8b
        boardMap[ShogiSquare(2, 2)] = ShogiPiece(PieceType.BISHOP, Side.WHITE) // 2b
        for (f in 1..9) {
            boardMap[ShogiSquare(f, 3)] = ShogiPiece(PieceType.PAWN, Side.WHITE)
        }
    }

    /**
     * SFEN 文字列から局面を設定する内部メソッド。
     * fromSfen() から呼ばれる。
     */
    private fun initFromSfen(sfen: String) {
        boardMap.clear()
        hands[0].clear()
        hands[1].clear()
        history.clear()

        val parts = sfen.trim().split(" ")
        val boardStr = parts.getOrElse(0) { return }
        val turnStr = parts.getOrElse(1) { "b" }
        val handStr = parts.getOrElse(2) { "-" }
        val moveNum = parts.getOrElse(3) { "1" }.toIntOrNull() ?: 1

        // ── 盤面パース ───────────────────────────────────────────────────
        val ranks = boardStr.split("/")
        for ((rankIdx, rankStr) in ranks.withIndex()) {
            val rank = rankIdx + 1
            var file = 9
            var i = 0
            while (i < rankStr.length && file >= 1) {
                val c = rankStr[i]
                when {
                    c == '+' -> {
                        i++
                        if (i < rankStr.length) {
                            val nc = rankStr[i]
                            val pt = sfenCharToPieceType(nc.uppercaseChar())
                            if (pt != null) {
                                val side = if (nc.isUpperCase()) Side.BLACK else Side.WHITE
                                boardMap[ShogiSquare(file, rank)] = ShogiPiece(pt.promoted(), side)
                            }
                            file--
                        }
                    }
                    c.isDigit() -> file -= c.digitToInt()
                    else -> {
                        val pt = sfenCharToPieceType(c.uppercaseChar())
                        if (pt != null) {
                            val side = if (c.isUpperCase()) Side.BLACK else Side.WHITE
                            boardMap[ShogiSquare(file, rank)] = ShogiPiece(pt, side)
                        }
                        file--
                    }
                }
                i++
            }
        }

        // ── 手番 ────────────────────────────────────────────────────────
        turn = if (turnStr == "b") Side.BLACK else Side.WHITE

        // ── 持ち駒パース ───────────────────────────────────────────────
        if (handStr != "-") {
            var pendingCount = 0
            for (c in handStr) {
                when {
                    c.isDigit() -> pendingCount = pendingCount * 10 + c.digitToInt()
                    else -> {
                        val pt = sfenCharToPieceType(c.uppercaseChar())
                        if (pt != null && pt != PieceType.KING) {
                            val cnt = if (pendingCount > 0) pendingCount else 1
                            pendingCount = 0
                            val side = if (c.isUpperCase()) Side.BLACK else Side.WHITE
                            hands[side.ordinal][pt] = (hands[side.ordinal][pt] ?: 0) + cnt
                        } else {
                            pendingCount = 0
                        }
                    }
                }
            }
        }

        // ── 手数ベース ─────────────────────────────────────────────────
        moveNumberBase = moveNum
    }

    /** sq のコマを返す（空なら null）。 */
    fun pieceAt(sq: ShogiSquare): ShogiPiece? = boardMap[sq]

    /** side の持ち駒を返す（コピー）。 */
    fun getHand(side: Side): Map<PieceType, Int> = hands[side.ordinal].toMap()

    /** 指し手を適用し手番を変える（副作用: boardMap / hands / turn / history を更新）。 */
    fun push(move: ShogiMove) {
        if (move.dropType != null) {
            // 打ち駒
            val hand = hands[turn.ordinal]
            val cnt = hand[move.dropType] ?: 0
            check(cnt > 0) { "No ${move.dropType} in ${turn}'s hand" }
            if (cnt == 1) hand.remove(move.dropType) else hand[move.dropType] = cnt - 1
            val piece = ShogiPiece(move.dropType, turn)
            boardMap[move.to] = piece
            history.addLast(MoveRecord(null, move.to, piece, null, false, move.dropType))
        } else {
            val from = move.from ?: error("from is null for non-drop move")
            val movedPiece = boardMap[from] ?: error("No piece at $from")
            val captured = boardMap[move.to]

            boardMap.remove(from)
            if (captured != null) {
                // 取った駒を手駒に追加（不成に戻す）
                val unpromoted = captured.type.unpromoted()
                val hand = hands[turn.ordinal]
                hand[unpromoted] = (hand[unpromoted] ?: 0) + 1
            }
            val newType = if (move.promote) movedPiece.type.promoted() else movedPiece.type
            boardMap[move.to] = ShogiPiece(newType, turn)
            history.addLast(MoveRecord(from, move.to, movedPiece, captured, move.promote, null))
        }
        turn = turn.opposite
    }

    /** 最後の指し手を巻き戻す。 */
    fun pop() {
        val rec = history.removeLast()
        turn = turn.opposite

        if (rec.dropType != null) {
            // 打ち駒を戻す
            boardMap.remove(rec.to)
            val hand = hands[turn.ordinal]
            hand[rec.dropType] = (hand[rec.dropType] ?: 0) + 1
        } else {
            val from = rec.from ?: error("from is null in history record")
            boardMap.remove(rec.to)
            boardMap[from] = rec.movedPiece
            if (rec.capturedPiece != null) {
                boardMap[rec.to] = rec.capturedPiece
                val unpromoted = rec.capturedPiece.type.unpromoted()
                val hand = hands[turn.ordinal]
                val cnt = hand[unpromoted] ?: 0
                if (cnt <= 1) hand.remove(unpromoted) else hand[unpromoted] = cnt - 1
            }
        }
    }

    /**
     * 現在の手番側の王が王手されているかを返す。
     * classify_blunders.py の board.is_check() 相当。
     */
    fun isCheck(): Boolean {
        val kingSq = boardMap.entries.find { (_, p) ->
            p.type == PieceType.KING && p.side == turn
        }?.key ?: return false

        val enemy = turn.opposite
        return boardMap.entries.any { (sq, piece) ->
            piece.side == enemy && kingSq in attackedSquares(sq, piece)
        }
    }

    // ─── 合法手生成 ───────────────────────────────────────────────────────────

    /**
     * 現在の手番の合法手をすべて返す。
     *
     * 実装範囲:
     * - 駒の動き（全駒種）
     * - 自玉が取られる手の禁止（isLegal チェック）
     * - 打ち手の二歩・行き所のない駒（打ち歩詰めは対象外）
     */
    fun legalMoves(): List<ShogiMove> {
        val result = mutableListOf<ShogiMove>()
        val promoZone = if (turn == Side.BLACK) 1..3 else 7..9
        val lastRank = if (turn == Side.BLACK) 1 else 9
        val lastTwoRanks = if (turn == Side.BLACK) 1..2 else 8..9

        // ── 盤上の駒 ────────────────────────────────────────────────────
        // Map.toList()（Pairへの即時コピー）であること。entries.toList() は Kotlin/Native では
        // 生きた EntryRef のリストになり、ループ内の isLegal() の push/pop 後に key/value を
        // 読むと ConcurrentModificationException になる（JVM では顕在化しない実装差）
        for ((sq, piece) in boardMap.toList()) {
            if (piece.side != turn) continue

            for (to in attackedSquares(sq, piece)) {
                val target = boardMap[to]
                if (target?.side == turn) continue  // 自駒は取れない

                // 行き所のない駒: 成りが強制される場合
                val mustPromote = piece.type.promotable && when (piece.type) {
                    PieceType.PAWN, PieceType.LANCE -> to.rank == lastRank
                    PieceType.KNIGHT -> to.rank in lastTwoRanks
                    else -> false
                }
                val canPromote = piece.type.promotable && (sq.rank in promoZone || to.rank in promoZone)

                if (mustPromote) {
                    val m = ShogiMove(sq, to, promote = true)
                    if (isLegal(m)) result.add(m)
                } else {
                    val m = ShogiMove(sq, to, promote = false)
                    if (isLegal(m)) result.add(m)
                    if (canPromote) {
                        val pm = ShogiMove(sq, to, promote = true)
                        if (isLegal(pm)) result.add(pm)
                    }
                }
            }
        }

        // ── 打ち駒 ──────────────────────────────────────────────────────
        // isLegal() 内の push/pop が持ち駒Mapを構造変更する（枚数0でキー削除・復元でキー追加）ため、
        // スナップショットに対してイテレートする（盤上駒ループの boardMap.toList() と同じ理由）
        val hand = hands[turn.ordinal]
        for ((pt, count) in hand.toList()) {
            if (count <= 0) continue
            for (rank in 1..9) {
                for (file in 1..9) {
                    val to = ShogiSquare(file, rank)
                    if (boardMap.containsKey(to)) continue  // 空マスのみ

                    // 行き所のない駒
                    if ((pt == PieceType.PAWN || pt == PieceType.LANCE) && rank == lastRank) continue
                    if (pt == PieceType.KNIGHT && rank in lastTwoRanks) continue

                    // 二歩
                    if (pt == PieceType.PAWN) {
                        val hasPawnInFile = boardMap.entries.any { (s, p) ->
                            s.file == file && p.side == turn && p.type == PieceType.PAWN
                        }
                        if (hasPawnInFile) continue
                    }

                    val m = ShogiMove(from = null, to = to, dropType = pt)
                    if (isLegal(m)) result.add(m)
                }
            }
        }

        return result
    }

    /** from マスを起点とする合法手を返す（UI でのハイライト用）。 */
    fun legalMovesFrom(from: ShogiSquare): List<ShogiMove> =
        legalMoves().filter { it.from == from }

    /** dropType を打てる合法マスの一覧（UI でのハイライト用）。 */
    fun legalDropSquares(dropType: PieceType): List<ShogiSquare> =
        legalMoves().filter { it.dropType == dropType }.map { it.to }

    /**
     * 指し手を仮適用し、自玉が取られないか確認する。
     * push/pop は対にして使う。
     */
    private fun isLegal(move: ShogiMove): Boolean {
        push(move)
        // push 後: turn が相手番に変わる。ownSide = 指した側 = turn.opposite
        val ownSide = turn.opposite
        val kingSq = boardMap.entries.find { (_, p) -> p.type == PieceType.KING && p.side == ownSide }?.key
        val legal = kingSq == null || boardMap.entries.none { (sq, p) ->
            p.side == turn && kingSq in attackedSquares(sq, p)
        }
        pop()
        return legal
    }

    // ─── SFEN 生成 ───────────────────────────────────────────────────────────

    /**
     * 現在の局面を SFEN 文字列で返す。
     *
     * 形式: "[board] [turn] [hands] [move_number]"
     * - board: rank 1(a)→9(i)、各 rank は file 9→1 の順。Black=大文字、White=小文字、成駒は '+' 接頭辞。
     * - turn: 'b'(先手) / 'w'(後手)
     * - hands: R/B/G/S/N/L/P 順。大文字（Black）先、小文字（White）後。枚数 >1 は数字接頭辞。なければ '-'。
     * - move_number: moveNumberBase + 指した手数（fromSfen の手数から連続して増加）
     */
    fun toSfen(): String {
        val sb = StringBuilder()

        // ── board ──────────────────────────────────────────────────────────
        for (rank in 1..9) {
            var empty = 0
            for (file in 9 downTo 1) {
                val piece = boardMap[ShogiSquare(file, rank)]
                if (piece == null) {
                    empty++
                } else {
                    if (empty > 0) { sb.append(empty); empty = 0 }
                    sb.append(piece.toSfenString())
                }
            }
            if (empty > 0) sb.append(empty)
            if (rank < 9) sb.append('/')
        }

        // ── turn ───────────────────────────────────────────────────────────
        sb.append(' ')
        sb.append(if (turn == Side.BLACK) 'b' else 'w')

        // ── hands ──────────────────────────────────────────────────────────
        sb.append(' ')
        val handOrder = listOf(
            PieceType.ROOK, PieceType.BISHOP, PieceType.GOLD,
            PieceType.SILVER, PieceType.KNIGHT, PieceType.LANCE, PieceType.PAWN,
        )
        var hasAny = false
        for (side in listOf(Side.BLACK, Side.WHITE)) {
            val hand = hands[side.ordinal]
            for (pt in handOrder) {
                val cnt = hand[pt] ?: 0
                if (cnt > 0) {
                    hasAny = true
                    if (cnt > 1) sb.append(cnt)
                    val c = pt.sfenChar()
                    sb.append(if (side == Side.BLACK) c.uppercaseChar() else c.lowercaseChar())
                }
            }
        }
        if (!hasAny) sb.append('-')

        // ── move_number ────────────────────────────────────────────────────
        sb.append(' ')
        sb.append(history.size + moveNumberBase)

        return sb.toString()
    }

    // ─── 駒の利き計算 ────────────────────────────────────────────────────────

    /**
     * piece（位置 = from）が攻撃できるマスの集合。
     * 利き計算は、先手（BLACK）の前方は rank-1 方向（rank 9 → 1）。
     */
    private fun attackedSquares(from: ShogiSquare, piece: ShogiPiece): Set<ShogiSquare> {
        val fwd = if (piece.side == Side.BLACK) -1 else 1
        val f = from.file
        val r = from.rank

        /** 1マス移動（盤外は除外）。 */
        fun sq(df: Int, dr: Int): ShogiSquare? {
            val nf = f + df
            val nr = r + dr
            return if (nf in 1..9 && nr in 1..9) ShogiSquare(nf, nr) else null
        }

        /** 複数の1マス移動（可変長引数）をまとめて集合化。 */
        fun step(vararg deltas: Pair<Int, Int>): Set<ShogiSquare> =
            deltas.mapNotNull { (df, dr) -> sq(df, dr) }.toSet()

        /** 方向 dirs それぞれについて、駒に当たるまでスライド（当たりマスは含む）。 */
        fun slide(vararg dirs: Pair<Int, Int>): Set<ShogiSquare> {
            val result = mutableSetOf<ShogiSquare>()
            for ((df, dr) in dirs) {
                var cf = f + df
                var cr = r + dr
                while (cf in 1..9 && cr in 1..9) {
                    val s = ShogiSquare(cf, cr)
                    result.add(s)
                    if (boardMap.containsKey(s)) break  // 駒に当たったら停止
                    cf += df
                    cr += dr
                }
            }
            return result
        }

        return when (piece.type) {
            // 歩: 1マス前進
            PieceType.PAWN -> step(0 to fwd)

            // 香: 前方にスライド
            PieceType.LANCE -> slide(0 to fwd)

            // 桂: 前方2段 + 横1（跳ぶ）
            PieceType.KNIGHT -> setOfNotNull(sq(-1, 2 * fwd), sq(1, 2 * fwd))

            // 銀: 前方3 + 後方斜め2
            PieceType.SILVER -> step(0 to fwd, 1 to fwd, -1 to fwd, 1 to -fwd, -1 to -fwd)

            // 金・成り駒（と金・成香・成桂・成銀）: 前方3 + 後方1 + 横2
            PieceType.GOLD,
            PieceType.PROM_PAWN,
            PieceType.PROM_LANCE,
            PieceType.PROM_KNIGHT,
            PieceType.PROM_SILVER -> step(0 to fwd, 1 to fwd, -1 to fwd, 0 to -fwd, 1 to 0, -1 to 0)

            // 角: 斜め4方向スライド
            PieceType.BISHOP -> slide(1 to 1, 1 to -1, -1 to 1, -1 to -1)

            // 飛: 縦横4方向スライド
            PieceType.ROOK -> slide(0 to 1, 0 to -1, 1 to 0, -1 to 0)

            // 王: 8方向1マス
            PieceType.KING -> step(0 to 1, 0 to -1, 1 to 0, -1 to 0, 1 to 1, 1 to -1, -1 to 1, -1 to -1)

            // 馬（龍馬）: 角スライド + 縦横1マス
            PieceType.PROM_BISHOP ->
                slide(1 to 1, 1 to -1, -1 to 1, -1 to -1) +
                    step(0 to 1, 0 to -1, 1 to 0, -1 to 0)

            // 龍（龍王）: 飛スライド + 斜め1マス
            PieceType.PROM_ROOK ->
                slide(0 to 1, 0 to -1, 1 to 0, -1 to 0) +
                    step(1 to 1, 1 to -1, -1 to 1, -1 to -1)
        }
    }
}
