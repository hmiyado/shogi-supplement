package dev.miyado.shogisupplement.opening

import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.board.ShogiPiece
import dev.miyado.shogisupplement.board.Side
import dev.miyado.shogisupplement.opening.OpeningClassifier.bfFile
import dev.miyado.shogisupplement.opening.OpeningClassifier.ROOK_HOME_RANK
import dev.miyado.shogisupplement.opening.OpeningClassifier.bfRank

/** 角が最初に取られた手。誰が・相手の角の初期位置で取ったかまで見る。 */
data class BishopCapture(val ply: Int, val by: Side, val onOpponentHome: Boolean)

/**
 * 序盤に何が起きたかの記録。戦型の判定はこの記録だけを見る。
 *
 * 記録と判定を分けているのは、戦型ごとの条件（[OpeningCondition]）を宣言として書くため。
 * 何を見るかがここに集まっていれば、戦型が増えても条件の組み合わせだけで済む。
 */
class OpeningEvents private constructor(
    /** 飛車先の歩を5段目まで伸ばした手数。 */
    val rookPawnPushPly: Map<Side, Int>,
    /** 飛車先の歩を交換した手数（自分の飛車が4段目で歩を取った手）。 */
    val pawnTradePly: Int?,
    /** 飛車が横歩を取った手数。 */
    val yokofuPly: Int?,
    /** 角交換が成立した手数（2枚目が取られた手）。 */
    val bishopExchangePly: Int?,
    /** 窓を掛けない最初の角の捕獲手数。 */
    val anyBishopCapturePly: Int?,
    val firstBishopCapture: BishopCapture?,
    /** 角交換のあと最初に角を打った手数。 */
    val bishopDropPly: Int?,
    /** 飛車を振り飛車の筋へ振った手数。 */
    val furibishaPly: Map<Side, Int>,
    /** 角交換のあと、自分の角を筋違いのマスへ打った手数。 */
    val sujichigaiDropPly: Map<Side, Int>,
    /** 7五の歩を突いた手数。 */
    val sideRookPawnPly: Map<Side, Int>,
    /** 飛車を7筋へ振った手数。 */
    val sangenbishaPly: Map<Side, Int>,
    /** 角道を止める歩を突いた手数。 */
    val bishopPathClosedPly: Map<Side, Int>,
    /** 飛車先の歩を交換したあと、その飛車を左翼へ寄せた手数。 */
    val hineriPly: Map<Side, Int>,
    /** 自陣の最下段を通して飛車を端筋へ回した手数。 */
    val chikatetsuPly: Map<Side, Int>,
    private val rooksHomeAtPly: Set<Int>,
) {

    /** 双方が飛車先を伸ばし切った手数（遅い方）。 */
    val rookPawnsPushedPly: Int? =
        if (rookPawnPushPly.size == 2) rookPawnPushPly.values.max() else null

    /** 双方の伸ばし切りの手数差。 */
    val rookPawnPushGap: Int? =
        if (rookPawnPushPly.size == 2) rookPawnPushPly.values.max() - rookPawnPushPly.values.min() else null

    /** その手を指す前の時点で、双方の飛車がまだ初期の筋にいたか。 */
    fun bothRooksHomeAt(ply: Int?): Boolean = ply != null && ply in rooksHomeAtPly

    /** 成立からこの手数以内に、どちらかが振り飛車へ振ったか。 */
    fun swungWithin(basePly: Int, plies: Int): Boolean =
        furibishaPly.values.any { it - basePly <= plies }

    companion object {

        fun record(moves: List<ShogiMove>, sides: List<Side>): OpeningEvents {
            val builder = Builder()
            var board = dev.miyado.shogisupplement.board.ShogiBoard()
            moves.forEachIndexed { index, move ->
                val mover = board.turn
                val moving = move.from?.let { board.pieceAt(it) }
                val captured = board.pieceAt(move.to)
                builder.observe(index + 1, move, mover, moving, captured)
                board.push(move)
            }
            return builder.build()
        }

        internal const val ROOK_FILE = 2
        internal const val ROOK_PAWN_RANK = 5
        internal const val PAWN_TRADE_RANK = 4
        internal const val YOKOFU_FILE = 3
        internal const val YOKOFU_RANK = 4

        /** 筋違い角を打つマス（先手4五）。 */
        internal val SUJICHIGAI_SQUARE = BfSquare(4, 5)

        /** 早石田で突く歩（先手7五）。 */
        internal val SIDE_ROOK_PAWN = BfSquare(7, 5)

        /** 角道を止める歩（先手6六）。 */
        internal val BISHOP_PATH_PAWN = BfSquare(6, 6)

        /** ひねり飛車で飛車を寄せるマス（先手3六）。 */
        internal val HINERI_SQUARE = BfSquare(3, 6)

        /** 飛車が三間へ振られる筋。 */
        internal const val SANGEN_FILE = 7

        /** 地下鉄飛車で飛車を通す段（自陣の最下段）と、その行き先の端筋。 */
        internal const val LOWEST_RANK = 9
        internal const val EDGE_FILE = 9

        private val ROOK_TYPES = setOf(PieceType.ROOK, PieceType.PROM_ROOK)
        private val BISHOP_TYPES = setOf(PieceType.BISHOP, PieceType.PROM_BISHOP)
        private val BISHOP_HOME = BfSquare(8, 8)
        private val FURIBISHA_FILES = setOf(5, 6, 7, 8)
    }

    /** 1パスで出来事を拾う。判定はここでは行わない。 */
    private class Builder {
        private val rookFile = mutableMapOf(Side.BLACK to ROOK_FILE, Side.WHITE to ROOK_FILE)
        private val furibishaPly = mutableMapOf<Side, Int>()
        private val rookPawnPushPly = mutableMapOf<Side, Int>()
        private val rooksHomeAtPly = mutableSetOf<Int>()
        private var bishopCaptures = 0
        private var anyBishopCapturePly: Int? = null
        private var firstBishopCapture: BishopCapture? = null
        private var bishopExchangePly: Int? = null
        private var bishopDropPly: Int? = null
        private var pawnTradePly: Int? = null
        private var yokofuPly: Int? = null
        private val sujichigaiDropPly = mutableMapOf<Side, Int>()
        private val sideRookPawnPly = mutableMapOf<Side, Int>()
        private val sangenbishaPly = mutableMapOf<Side, Int>()
        private val bishopPathClosedPly = mutableMapOf<Side, Int>()
        private val hineriPly = mutableMapOf<Side, Int>()
        private val rookLowestRankPly = mutableMapOf<Side, Int>()
        private val chikatetsuPly = mutableMapOf<Side, Int>()

        fun observe(ply: Int, move: ShogiMove, mover: Side, moving: ShogiPiece?, captured: ShogiPiece?) {
            val opponent = if (mover == Side.BLACK) Side.WHITE else Side.BLACK
            // 出来事はこの手を指す前の陣形で見る（横歩を取る手は飛車自身が筋を離れるため）。
            if (rookFile.values.all { it == ROOK_FILE }) rooksHomeAtPly += ply
            val isRookMove = moving != null && moving.type in ROOK_TYPES
            val toFile = bfFile(move.to.file, mover)
            val toRank = bfRank(move.to.rank, mover)

            if (captured != null && captured.type in BISHOP_TYPES) {
                if (anyBishopCapturePly == null) anyBishopCapturePly = ply
                if (firstBishopCapture == null) {
                    firstBishopCapture = BishopCapture(
                        ply = ply,
                        by = mover,
                        onOpponentHome = move.to == BISHOP_HOME.toSquare(opponent),
                    )
                }
                bishopCaptures++
                if (bishopCaptures >= 2 && bishopExchangePly == null) bishopExchangePly = ply
            }

            if (moving == null && move.dropType == PieceType.BISHOP &&
                bishopExchangePly != null && bishopDropPly == null
            ) {
                bishopDropPly = ply
            }

            if (moving?.type == PieceType.PAWN && mover !in rookPawnPushPly &&
                toFile == ROOK_FILE && toRank == ROOK_PAWN_RANK
            ) {
                rookPawnPushPly[mover] = ply
            }

            if (isRookMove && captured?.type == PieceType.PAWN && toFile == ROOK_FILE &&
                toRank == PAWN_TRADE_RANK && pawnTradePly == null
            ) {
                pawnTradePly = ply
            }
            if (isRookMove && captured?.type == PieceType.PAWN && toFile == YOKOFU_FILE &&
                toRank == YOKOFU_RANK && yokofuPly == null
            ) {
                yokofuPly = ply
            }

            if (moving == null && move.dropType == PieceType.BISHOP &&
                bishopExchangePly != null && mover !in sujichigaiDropPly &&
                toFile == SUJICHIGAI_SQUARE.file && toRank == SUJICHIGAI_SQUARE.rank
            ) {
                sujichigaiDropPly[mover] = ply
            }

            if (moving?.type == PieceType.PAWN) {
                if (mover !in sideRookPawnPly &&
                    toFile == SIDE_ROOK_PAWN.file && toRank == SIDE_ROOK_PAWN.rank
                ) {
                    sideRookPawnPly[mover] = ply
                }
                if (mover !in bishopPathClosedPly &&
                    toFile == BISHOP_PATH_PAWN.file && toRank == BISHOP_PATH_PAWN.rank
                ) {
                    bishopPathClosedPly[mover] = ply
                }
            }

            if (isRookMove) {
                if (mover !in sangenbishaPly && toFile == SANGEN_FILE && toRank == ROOK_HOME_RANK) {
                    sangenbishaPly[mover] = ply
                }
                val traded = pawnTradePly
                if (mover !in hineriPly && traded != null && ply > traded &&
                    toFile == HINERI_SQUARE.file && toRank == HINERI_SQUARE.rank
                ) {
                    hineriPly[mover] = ply
                }
                // 引く手と回る手の順序が定義そのものなので、最下段に居た記録を持ってから見る。
                if (toRank == LOWEST_RANK) {
                    val since = rookLowestRankPly[mover]
                    if (since == null) {
                        rookLowestRankPly[mover] = ply
                    } else if (mover !in chikatetsuPly && toFile == EDGE_FILE) {
                        chikatetsuPly[mover] = ply
                    }
                }
                rookFile[mover] = toFile
                // 振ったとみなすのは飛車の初期段へ動かす手だけ（浮き飛車の横移動を数えない）。
                if (mover !in furibishaPly && toFile in FURIBISHA_FILES && toRank == ROOK_HOME_RANK) {
                    furibishaPly[mover] = ply
                }
            }
        }

        fun build() = OpeningEvents(
            rookPawnPushPly = rookPawnPushPly.toMap(),
            pawnTradePly = pawnTradePly,
            yokofuPly = yokofuPly,
            bishopExchangePly = bishopExchangePly,
            anyBishopCapturePly = anyBishopCapturePly,
            firstBishopCapture = firstBishopCapture,
            bishopDropPly = bishopDropPly,
            furibishaPly = furibishaPly.toMap(),
            sujichigaiDropPly = sujichigaiDropPly.toMap(),
            sideRookPawnPly = sideRookPawnPly.toMap(),
            sangenbishaPly = sangenbishaPly.toMap(),
            bishopPathClosedPly = bishopPathClosedPly.toMap(),
            hineriPly = hineriPly.toMap(),
            chikatetsuPly = chikatetsuPly.toMap(),
            rooksHomeAtPly = rooksHomeAtPly.toSet(),
        )
    }
}
