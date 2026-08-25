package dev.miyado.shogisupplement.opening

import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.board.Side

/** 片方の陣営の分類結果。 */
data class SideOpening(
    /** 飛車の筋から決まる大分類（居飛車・中飛車・四間飛車・三間飛車・向かい飛車ほか）。 */
    val style: String,
    /** 表示する囲い。成立していなければ最終局面から残余の形（舟囲い・居玉）を出す。 */
    val castle: String,
    /** 成立した囲い。発展の途中も残る（本美濃へ組んでから高美濃へ発展すれば両方入る）。 */
    val achievedCastles: Set<String>,
    /** 成立した戦法・戦型のタグ。 */
    val tags: Set<String>,
)

data class OpeningResult(val black: SideOpening, val white: SideOpening) {
    fun of(side: Side): SideOpening = if (side == Side.BLACK) black else white
}

/**
 * 棋譜から戦型と囲いを判定する。エンジンは使わず、指し手と盤面だけで決まる。
 *
 * 判定は「疑わしきは付けない」。同じ出だしから別の戦型へ分岐する将棋を拾わないよう、
 * 成立条件に手数の上限と、成立後の否定（振り飛車へ振ったら取り消す等）を置いている。
 */
object OpeningClassifier {

    /** 飛車が同じ筋に留まったら定着とみなす手数。一時的な浮き飛車を拾わないため。 */
    const val SETTLE_PLY_THRESHOLD = 10

    /** 中盤以降の飛車回りを新たな定着として拾わない上限。 */
    const val STYLE_PLY_CAP = 80

    private const val ROOK_FILE = 2
    private val ROOK_TYPES = setOf(PieceType.ROOK, PieceType.PROM_ROOK)
    private val BISHOP_TYPES = setOf(PieceType.BISHOP, PieceType.PROM_BISHOP)
    /** 定着した筋（自分視点）と表示名の対応。 */
    val FURIBISHA_LABELS_BY_FILE = mapOf(5 to "中飛車", 6 to "四間飛車", 7 to "三間飛車", 8 to "向かい飛車")

    fun classify(usiMoves: List<String>): OpeningResult {
        val moves = usiMoves.map { ShogiMove.fromUsi(it) }
        val events = OpeningEvents.record(moves, Side.entries)
        val trackers = Side.entries.associateWith { RookSettleTracker(it) }
        val achieved = Side.entries.associateWith { mutableMapOf<String, Int>() }
        val board = ShogiBoard()

        moves.forEachIndexed { index, move ->
            val ply = index + 1
            val mover = board.turn
            val moving = move.from?.let { board.pieceAt(it) }
            board.push(move)
            trackers.getValue(mover).observe(ply, move, moving)
            Side.entries.forEach { side ->
                PLACEMENT_DEFS.forEach { def ->
                    // 囲いと戦型に同じ名前（矢倉）があるため、slugで区別して記録する。
                    if (def.slug !in achieved.getValue(side) && ply <= def.plyCap && board.matches(side, def)) {
                        achieved.getValue(side)[def.slug] = ply
                    }
                }
            }
        }

        val styles = Side.entries.associateWith { trackers.getValue(it).style() }
        val tags = Side.entries.associateWith { mutableSetOf<String>() }

        PLACEMENT_STRATEGY_DEFS.forEach { def ->
            Side.entries.forEach { side ->
                val achievedPly = achieved.getValue(side)[def.slug] ?: return@forEach
                val context = OpeningContext(events, styles, tags, achievementPly = achievedPly)
                if (def.conditions.all { it.holds(context, side) }) tags.getValue(side) += def.name
            }
        }

        // 宣言の順に評価する。前提にするタグと排他にするタグが先に決まっている必要がある。
        EVENT_STRATEGY_DEFS.forEach { def ->
            val context = OpeningContext(events, styles, tags)
            val matched = Side.entries.filter { side -> def.conditions.all { it.holds(context, side) } }
            when (def.scope) {
                TagScope.BOTH_SIDES -> if (matched.size == Side.entries.size) {
                    tags.values.forEach { it += def.name }
                }
                TagScope.MATCHING_SIDE -> matched.forEach { tags.getValue(it) += def.name }
            }
        }

        return OpeningResult(
            black = sideResult(Side.BLACK, styles, achieved, tags, board),
            white = sideResult(Side.WHITE, styles, achieved, tags, board),
        )
    }

    private fun sideResult(
        side: Side,
        styles: Map<Side, String>,
        achieved: Map<Side, Map<String, Int>>,
        tags: Map<Side, MutableSet<String>>,
        finalBoard: ShogiBoard,
    ): SideOpening {
        val achievedCastles = CASTLE_DEFS
            .filter { it.slug in achieved.getValue(side) }
            .map { it.name }
            .toSet()
        return SideOpening(
            style = styles.getValue(side),
            castle = displayCastle(achievedCastles, side, finalBoard),
            achievedCastles = achievedCastles,
            tags = tags.getValue(side).toSet(),
        )
    }

    /** 発展した形を優先して1つ選ぶ。成立が無ければ最終局面の残余の形を返す。 */
    private fun displayCastle(achieved: Set<String>, side: Side, finalBoard: ShogiBoard): String {
        val castles = CASTLE_DEFS.filter { it.name in achieved }
        if (castles.isEmpty()) return residualCastle(side, finalBoard)
        val developed = castles.filterNot { def -> castles.any { it.developsFrom == def.name } }
        return developed.lastOrNull()?.name ?: castles.last().name
    }

    /**
     * 達成型の囲いが1つも無いときだけ見る、未発達のままの形。
     * 矢倉を指した対局は必ず舟囲いを経由するため、達成型と同列には扱わない。
     */
    private fun residualCastle(side: Side, board: ShogiBoard): String {
        val king = BfSquare(5, 9).toSquare(side)
        if (board.pieceAt(king)?.let { it.type == PieceType.KING && it.side == side } == true) return "居玉"
        val funa = listOf(
            PiecePlacement(PieceType.KING, BfSquare(7, 8)),
            PiecePlacement(PieceType.SILVER, BfSquare(7, 9)),
            PiecePlacement(PieceType.GOLD, BfSquare(6, 9)),
        )
        val isFunagakoi = funa.all { placement ->
            board.pieceAt(placement.square.toSquare(side))
                ?.let { it.type == placement.type && it.side == side } == true
        }
        return if (isFunagakoi) "舟囲い" else "未分類"
    }

    private fun ShogiBoard.matches(side: Side, def: PlacementDef): Boolean {
        def.required.forEach { placement ->
            val piece = pieceAt(placement.square.toSquare(side))
            if (piece == null || piece.side != side || piece.type != placement.type) return false
        }
        def.empty.forEach { square ->
            if (pieceAt(square.toSquare(side)) != null) return false
        }
        def.forbidden.forEach { placement ->
            val piece = pieceAt(placement.square.toSquare(side))
            if (piece != null && piece.side == side && piece.type == placement.type) return false
        }
        return true
    }

    /**
     * 飛車が同じ筋に留まった時点で「その筋へ定着した」とみなす達成型のトラッカー。
     * 一時的に浮いただけの飛車を戦法として拾わないための据え置きを持つ。
     */
    private class RookSettleTracker(private val side: Side) {
        private var currentFile = ROOK_FILE
        private var lastChangePly = 0
        private val achievedFiles = mutableListOf<Int>()

        fun observe(ply: Int, move: ShogiMove, moving: dev.miyado.shogisupplement.board.ShogiPiece?) {
            if (ply > STYLE_PLY_CAP) return
            if (moving != null && moving.side == side && moving.type in ROOK_TYPES) {
                val file = bfFile(move.to.file, side)
                if (file != currentFile) {
                    currentFile = file
                    lastChangePly = ply
                }
            }
            if (ply - lastChangePly >= SETTLE_PLY_THRESHOLD && achievedFiles.lastOrNull() != currentFile) {
                achievedFiles += currentFile
            }
        }

        fun style(): String {
            if (achievedFiles.isEmpty()) return "未分類"
            // 名前の付いた筋への最後の定着を優先する。1・3・4・9筋への定着は
            // 本来の戦法へ移る途中の一時停止であることが多い。
            val named = achievedFiles.filter { it == ROOK_FILE || it in FURIBISHA_LABELS_BY_FILE }
            return label(named.lastOrNull() ?: achievedFiles.last())
        }

        private fun label(file: Int): String = when {
            file == ROOK_FILE -> "居飛車"
            file in FURIBISHA_LABELS_BY_FILE -> FURIBISHA_LABELS_BY_FILE.getValue(file)
            else -> "振り飛車（その他）"
        }
    }

    internal fun bfFile(file: Int, side: Side): Int = if (side == Side.BLACK) file else 10 - file

    internal fun bfRank(rank: Int, side: Side): Int = if (side == Side.BLACK) rank else 10 - rank
}
