package dev.miyado.shogisupplement.opening

import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.board.Side

/** 片方の陣営の分類結果。 */
data class SideOpening(
    /** 画面に出す代表の戦型。成立したタグから[PRIMARY_STYLE_PRIORITY]の順で1つ選ぶ。 */
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

    /** どの形にも当てはまらなかったときの値。 */
    const val UNCLASSIFIED = "未分類"

    /** 飛車を振った手がこの手数までなら戦型として数える。中盤の攻めの飛車回りを拾わないため。 */
    const val ROOK_STYLE_PLY_CAP = 24

    /** 飛車を振る段（自分視点）。飛車の初期段で、ここへの横移動だけを「振った」とみなす。 */
    const val ROOK_HOME_RANK = 8

    private const val ROOK_FILE = 2
    private val ROOK_TYPES = setOf(PieceType.ROOK, PieceType.PROM_ROOK)
    private val BISHOP_TYPES = setOf(PieceType.BISHOP, PieceType.PROM_BISHOP)

    /**
     * 代表の戦型として出す順。先にあるものほど、その対局で何を指したかをよく表す。
     * 攻めの形（棒銀・腰掛け銀など）は代表にせず、タグとしてだけ持つ。
     */
    val PRIMARY_STYLE_PRIORITY = listOf(
        "横歩取り", "一手損角換わり", "角換わり", "相掛かり", "矢倉", "雁木",
        "石田流", "ツノ銀中飛車", "中飛車", "四間飛車", "三間飛車", "向かい飛車", "袖飛車",
        "右玉", "相振り飛車", "角交換振り飛車",
    )

    /** 飛車を振った筋（自分視点）と戦型名の対応。2筋（初期の筋）のままは判定しない。 */
    val ROOK_FILE_LABELS = mapOf(
        3 to "袖飛車",
        5 to "中飛車",
        6 to "四間飛車",
        7 to "三間飛車",
        8 to "向かい飛車",
    )

    fun classify(usiMoves: List<String>): OpeningResult {
        val moves = usiMoves.map { ShogiMove.fromUsi(it) }
        val events = OpeningEvents.record(moves, Side.entries)
        val trackers = Side.entries.associateWith { RookFileTracker(it) }
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

        val rookStyles = Side.entries.associateWith { trackers.getValue(it).style() }
        val tags = Side.entries.associateWith { mutableSetOf<String>() }

        PLACEMENT_STRATEGY_DEFS.forEach { def ->
            Side.entries.forEach { side ->
                val achievedPly = achieved.getValue(side)[def.slug] ?: return@forEach
                val context = OpeningContext(events, rookStyles, tags, achievementPly = achievedPly)
                if (def.conditions.all { it.holds(context, side) }) tags.getValue(side) += def.name
            }
        }

        // 宣言の順に評価する。前提にするタグと排他にするタグが先に決まっている必要がある。
        EVENT_STRATEGY_DEFS.forEach { def ->
            val context = OpeningContext(events, rookStyles, tags)
            val matched = Side.entries.filter { side -> def.conditions.all { it.holds(context, side) } }
            when (def.scope) {
                TagScope.BOTH_SIDES -> if (matched.size == Side.entries.size) {
                    tags.values.forEach { it += def.name }
                }
                TagScope.MATCHING_SIDE -> matched.forEach { tags.getValue(it) += def.name }
            }
        }

        Side.entries.forEach { side ->
            val rookStyle = rookStyles.getValue(side)
            if (rookStyle != UNCLASSIFIED) tags.getValue(side) += rookStyle
        }

        return OpeningResult(
            black = sideResult(Side.BLACK, achieved, tags, board),
            white = sideResult(Side.WHITE, achieved, tags, board),
        )
    }

    private fun sideResult(
        side: Side,
        achieved: Map<Side, Map<String, Int>>,
        tags: Map<Side, MutableSet<String>>,
        finalBoard: ShogiBoard,
    ): SideOpening {
        val achievedCastles = CASTLE_DEFS
            .filter { it.slug in achieved.getValue(side) }
            .map { it.name }
            .toSet()
        val sideTags = tags.getValue(side)
        return SideOpening(
            style = PRIMARY_STYLE_PRIORITY.firstOrNull { it in sideTags } ?: UNCLASSIFIED,
            castle = displayCastle(achievedCastles, side, finalBoard),
            achievedCastles = achievedCastles,
            tags = sideTags.toSet(),
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
        return if (isFunagakoi) "舟囲い" else UNCLASSIFIED
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
     * 飛車を振った筋から戦型を決める。最初に振った筋で確定し、その後どこへ回しても変えない。
     * 序盤に決めた戦型を、中盤に攻めで飛車を回しただけで塗り替えないため。
     */
    private class RookFileTracker(private val side: Side) {
        private var style: String? = null

        fun observe(ply: Int, move: ShogiMove, moving: dev.miyado.shogisupplement.board.ShogiPiece?) {
            if (style != null || ply > ROOK_STYLE_PLY_CAP) return
            if (moving == null || moving.side != side || moving.type !in ROOK_TYPES) return
            // 自陣の段へ横に動かす手だけを「振った」とみなす。浮き飛車が敵陣寄りの段を
            // 横へ動く手（横歩取りの2四飛→3四飛など）は戦型を決める手ではない。
            if (bfRank(move.to.rank, side) != ROOK_HOME_RANK) return
            style = ROOK_FILE_LABELS[bfFile(move.to.file, side)]
        }

        fun style(): String = style ?: UNCLASSIFIED
    }

    internal fun bfFile(file: Int, side: Side): Int = if (side == Side.BLACK) file else 10 - file

    internal fun bfRank(rank: Int, side: Side): Int = if (side == Side.BLACK) rank else 10 - rank
}
