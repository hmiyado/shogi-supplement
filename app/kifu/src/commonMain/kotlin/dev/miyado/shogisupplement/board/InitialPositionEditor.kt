package dev.miyado.shogisupplement.board

sealed interface EditorPieceLocation {
    data class Board(val square: ShogiSquare) : EditorPieceLocation
    data class Hand(val side: Side, val type: PieceType) : EditorPieceLocation
    data class Box(val type: PieceType) : EditorPieceLocation
}

data class InitialPositionEditor private constructor(
    val pieces: Map<ShogiSquare, ShogiPiece>,
    val blackHand: Map<PieceType, Int>,
    val whiteHand: Map<PieceType, Int>,
    val pieceBox: Map<PieceType, Int>,
    val turn: Side,
    val selected: EditorPieceLocation? = null,
) {
    val hasBothKings: Boolean
        get() = Side.entries.all { side -> pieces.values.count { it.type == PieceType.KING && it.side == side } == 1 }

    fun withTurn(side: Side): InitialPositionEditor = copy(turn = side)

    fun tapSquare(square: ShogiSquare): InitialPositionEditor {
        require(square.file in 1..9 && square.rank in 1..9)
        val piece = pieces[square]
        if (piece != null) {
            val next = when {
                piece.type.promotable -> piece.copy(type = piece.type.promoted())
                else -> ShogiPiece(piece.type.unpromoted(), piece.side.opposite)
            }
            return copy(pieces = pieces + (square to next), selected = EditorPieceLocation.Board(square))
        }
        val moving = selectedPiece() ?: return this
        val removed = removeSelected()
        return removed.copy(pieces = removed.pieces + (square to moving))
    }

    fun selectBoard(square: ShogiSquare): InitialPositionEditor =
        if (pieces[square] != null) copy(selected = EditorPieceLocation.Board(square)) else this

    fun moveToSquare(square: ShogiSquare): InitialPositionEditor =
        if (pieces[square] == null) tapSquare(square) else this

    fun selectHand(side: Side, type: PieceType): InitialPositionEditor =
        if ((hand(side)[type] ?: 0) > 0) copy(selected = EditorPieceLocation.Hand(side, type)) else this

    fun selectBox(type: PieceType): InitialPositionEditor =
        if ((pieceBox[type] ?: 0) > 0) copy(selected = EditorPieceLocation.Box(type)) else this

    fun moveToHand(side: Side): InitialPositionEditor {
        val piece = selectedPiece() ?: return this
        val type = piece.type.unpromoted()
        if (type == PieceType.KING) return this
        val removed = removeSelected()
        return removed.withHand(side, removed.hand(side).increment(type, 1))
    }

    fun moveToBox(): InitialPositionEditor {
        val piece = selectedPiece() ?: return this
        val removed = removeSelected()
        return removed.copy(pieceBox = removed.pieceBox.increment(piece.type.unpromoted(), 1))
    }

    fun hand(side: Side): Map<PieceType, Int> = if (side == Side.BLACK) blackHand else whiteHand

    fun toSfen(): String = buildString {
        append((1..9).joinToString("/") { rank ->
            buildString {
                var empty = 0
                for (file in 9 downTo 1) {
                    val piece = pieces[ShogiSquare(file, rank)]
                    if (piece == null) empty++ else {
                        if (empty > 0) append(empty)
                        empty = 0
                        append(piece.toSfenString())
                    }
                }
                if (empty > 0) append(empty)
            }
        })
        append(if (turn == Side.BLACK) " b " else " w ")
        val held = buildString {
            for (side in Side.entries) {
                for (type in handOrder) {
                    val count = hand(side)[type] ?: 0
                    if (count == 0) continue
                    if (count > 1) append(count)
                    append(if (side == Side.BLACK) type.sfenChar() else type.sfenChar().lowercaseChar())
                }
            }
        }
        append(held.ifEmpty { "-" })
        append(" 1")
    }

    private fun selectedPiece(): ShogiPiece? = when (val source = selected) {
        is EditorPieceLocation.Board -> pieces[source.square]
        is EditorPieceLocation.Hand -> if ((hand(source.side)[source.type] ?: 0) > 0) ShogiPiece(source.type, source.side) else null
        is EditorPieceLocation.Box -> if ((pieceBox[source.type] ?: 0) > 0) ShogiPiece(source.type, turn) else null
        null -> null
    }

    private fun removeSelected(): InitialPositionEditor = when (val source = selected) {
        is EditorPieceLocation.Board -> copy(pieces = pieces - source.square, selected = null)
        is EditorPieceLocation.Hand -> withHand(source.side, hand(source.side).increment(source.type, -1)).copy(selected = null)
        is EditorPieceLocation.Box -> copy(pieceBox = pieceBox.increment(source.type, -1), selected = null)
        null -> this
    }

    private fun withHand(side: Side, hand: Map<PieceType, Int>): InitialPositionEditor =
        if (side == Side.BLACK) copy(blackHand = hand) else copy(whiteHand = hand)

    companion object {
        private val handOrder = listOf(PieceType.ROOK, PieceType.BISHOP, PieceType.GOLD, PieceType.SILVER, PieceType.KNIGHT, PieceType.LANCE, PieceType.PAWN)
        private val stock = mapOf(PieceType.PAWN to 18, PieceType.LANCE to 4, PieceType.KNIGHT to 4, PieceType.SILVER to 4, PieceType.GOLD to 4, PieceType.BISHOP to 2, PieceType.ROOK to 2, PieceType.KING to 2)

        fun fromSfen(sfen: String = ShogiBoard().toSfen()): InitialPositionEditor {
            val board = ShogiBoard.fromSfen(sfen)
            val pieces = buildMap {
                for (rank in 1..9) for (file in 1..9) {
                    val square = ShogiSquare(file, rank)
                    board.pieceAt(square)?.let { put(square, it) }
                }
            }
            val hands = Side.entries.associateWith(board::getHand)
            val box = stock.mapValues { (type, total) ->
                val used = pieces.values.count { it.type.unpromoted() == type } + hands.values.sumOf { it[type] ?: 0 }
                require(used <= total) { "Too many pieces: $type" }
                total - used
            }.filterValues { it > 0 }
            require(hands.values.none { (it[PieceType.KING] ?: 0) > 0 })
            return InitialPositionEditor(pieces, hands.getValue(Side.BLACK), hands.getValue(Side.WHITE), box, board.turn)
        }
    }
}

private fun Map<PieceType, Int>.increment(type: PieceType, delta: Int): Map<PieceType, Int> {
    val count = (this[type] ?: 0) + delta
    require(count >= 0)
    return if (count == 0) this - type else this + (type to count)
}
