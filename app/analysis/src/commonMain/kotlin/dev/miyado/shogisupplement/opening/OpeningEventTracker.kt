package dev.miyado.shogisupplement.opening

import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.board.ShogiPiece
import dev.miyado.shogisupplement.board.Side
import dev.miyado.shogisupplement.opening.OpeningClassifier.bfFile
import dev.miyado.shogisupplement.opening.OpeningClassifier.bfRank

/**
 * 序盤に何が起きたかを1パスで記録し、駒の配置では表せない戦型を判定する。
 *
 * 相居飛車かどうかは、出来事が起きた時点で双方の飛車が初期の筋にいることと、
 * その後しばらく振り飛車へ振らないことの両方で見る。
 * Why not 飛車の定着で見る: 定着は10手の据え置きを要求するため、11手目に飛車が動く
 * 横歩取りのような早い出来事に間に合わない。
 */
internal class OpeningEventTracker {

    private val rookFile = mutableMapOf(Side.BLACK to ROOK_FILE, Side.WHITE to ROOK_FILE)
    private val rookChangePly = mutableMapOf(Side.BLACK to 0, Side.WHITE to 0)
    private val furibishaPly = mutableMapOf<Side, Int>()
    private val rookPawnPly = mutableMapOf<Side, Int>()
    private var bishopCaptures = 0
    private var anyBishopCapturePly: Int? = null
    private var firstCapturePly: Int? = null
    private var firstCaptureBy: Side? = null
    private var firstCaptureOnHome = false
    private var exchangePly: Int? = null
    private var bishopDropPly: Int? = null
    private var aigakariPly: Int? = null
    private var pawnTradePly: Int? = null
    private var yokofuPly: Int? = null
    private var ibishaAtExchange = false
    private var ibishaAtAigakari = false

    fun observe(ply: Int, move: ShogiMove, mover: Side, moving: ShogiPiece?, captured: ShogiPiece?) {
        val opponent = if (mover == Side.BLACK) Side.WHITE else Side.BLACK
        // 出来事はこの手を指す前の陣形で見る（横歩を取る手は飛車自身が筋を離れるため）。
        val bothRooksHome = rookFile.values.all { it == ROOK_FILE }
        val isRookMove = moving != null && moving.type in ROOK_TYPES
        val toFile = bfFile(move.to.file, mover)
        val toRank = bfRank(move.to.rank, mover)

        if (captured != null && captured.type in BISHOP_TYPES) {
            if (anyBishopCapturePly == null) anyBishopCapturePly = ply
            if (firstCapturePly == null && ply <= KAKUGAWARI_PLY_CAP) {
                firstCapturePly = ply
                firstCaptureBy = mover
                firstCaptureOnHome = move.to == BISHOP_HOME.toSquare(opponent)
            }
            if (firstCapturePly != null) {
                bishopCaptures++
                if (bishopCaptures >= 2 && exchangePly == null && ply <= EXCHANGE_FINISH_CAP) {
                    exchangePly = ply
                    ibishaAtExchange = bothRooksHome
                }
            }
        }

        if (moving == null && move.dropType == PieceType.BISHOP && exchangePly != null && bishopDropPly == null) {
            bishopDropPly = ply
        }

        if (moving != null && moving.type == PieceType.PAWN && ply <= AIGAKARI_PLY_CAP &&
            mover !in rookPawnPly && toFile == ROOK_FILE && toRank == ROOK_PAWN_RANK
        ) {
            rookPawnPly[mover] = ply
            if (rookPawnPly.size == 2) {
                aigakariPly = ply
                ibishaAtAigakari = bothRooksHome
            }
        }

        if (isRookMove && captured?.type == PieceType.PAWN && toFile == ROOK_FILE) {
            if (pawnTradePly == null && ply <= PAWN_TRADE_PLY_CAP && toRank == PAWN_TRADE_RANK) {
                pawnTradePly = ply
            }
        }
        if (isRookMove && captured?.type == PieceType.PAWN && yokofuPly == null &&
            ply <= YOKOFUDORI_PLY_CAP && toFile == YOKOFU_FILE && toRank == YOKOFU_RANK
        ) {
            yokofuPly = ply
        }

        if (isRookMove && toFile != rookFile.getValue(mover)) {
            rookFile[mover] = toFile
            rookChangePly[mover] = ply
        }
        Side.entries.forEach { side ->
            if (side !in furibishaPly && rookFile.getValue(side) in FURIBISHA_FILES &&
                ply - rookChangePly.getValue(side) >= SETTLE_PLY_THRESHOLD
            ) {
                furibishaPly[side] = ply
            }
        }
    }

    /** 配置で成立した戦法のうち、相居飛車限定・角交換なしの条件を満たすか。 */
    fun allows(def: PlacementDef, achievedPly: Int): Boolean {
        if (def.aiIbishaOnly && swungAfter(achievedPly)) return false
        val exchangeStarted = anyBishopCapturePly
        if (def.noBishopExchange && exchangeStarted != null && exchangeStarted < achievedPly) return false
        return true
    }

    /** 対局単位で決まる戦型を両者へ、一手損角換わりは手損した後手にだけ付ける。 */
    fun applyDerivedTags(styles: Map<Side, String>, tags: Map<Side, MutableSet<String>>) {
        if (styles.values.all { it in FURIBISHA_LABELS }) {
            tags.values.forEach { it += "相振り飛車" }
        }
        val kakugawariBase = exchangePly
        val exchanged = kakugawariBase != null
        if (exchanged) {
            Side.entries.forEach { side ->
                if (styles.getValue(side) in FURIBISHA_LABELS) tags.getValue(side) += "角交換振り飛車"
            }
        }
        val kakugawari = kakugawariBase != null &&
            kakugawariBase <= KAKUGAWARI_PLY_CAP &&
            ibishaAtExchange &&
            !swungAfter(kakugawariBase) &&
            // 角交換の直後に角を打つ将棋は筋違い角であって角換わりではない。
            (bishopDropPly == null || bishopDropPly!! - kakugawariBase > BISHOP_DROP_CANCEL_PLIES) &&
            // 角交換して振った将棋は角交換振り飛車であって角換わりではない。
            tags.values.none { "角交換振り飛車" in it }

        // 角交換した将棋は相掛かりと呼ばない（角交換は行わないのが相掛かりの定義）。
        // 飛車先の歩交換を求めるのは、伸ばし合っただけの力戦形を除くため。
        val pawnPlies = rookPawnPly.values
        val pushedTogether = pawnPlies.size == 2 && (pawnPlies.max() - pawnPlies.min()) <= AIGAKARI_GAP_MAX
        val aigakariBase = aigakariPly
        val aigakari = aigakariBase != null &&
            ibishaAtAigakari &&
            pushedTogether &&
            exchangePly == null &&
            pawnTradePly != null &&
            !swungAfter(aigakariBase)

        if (aigakari) tags.values.forEach { it += "相掛かり" }
        if (kakugawari) tags.values.forEach { it += "角換わり" }
        if (aigakari && yokofuPly != null) tags.values.forEach { it += "横歩取り" }
        // 一手損角換わりは「飛車先の歩を伸ばす手を省いて先に角を交換する」後手の指し方。
        val whitePawnPly = rookPawnPly[Side.WHITE]
        if (kakugawari && firstCaptureBy == Side.WHITE && firstCaptureOnHome &&
            (whitePawnPly == null || whitePawnPly > exchangePly!!)
        ) {
            tags.getValue(Side.WHITE) += "一手損角換わり"
        }
    }

    /** 成立の直後に振り飛車へ振ったか（＝相居飛車の戦型ではなかったか）。 */
    private fun swungAfter(basePly: Int): Boolean =
        furibishaPly.values.any { it - basePly <= FURIBISHA_CANCEL_PLIES }

    private companion object {
        const val ROOK_FILE = 2
        const val ROOK_PAWN_RANK = 5
        const val PAWN_TRADE_RANK = 4
        const val YOKOFU_FILE = 3
        const val YOKOFU_RANK = 4
        const val SETTLE_PLY_THRESHOLD = 10

        val AIGAKARI_PLY_CAP = OpeningEventRules.AIGAKARI_PLY_CAP
        val AIGAKARI_GAP_MAX = OpeningEventRules.AIGAKARI_GAP_MAX
        val PAWN_TRADE_PLY_CAP = OpeningEventRules.PAWN_TRADE_PLY_CAP
        val YOKOFUDORI_PLY_CAP = OpeningEventRules.YOKOFUDORI_PLY_CAP
        val KAKUGAWARI_PLY_CAP = OpeningEventRules.KAKUGAWARI_PLY_CAP
        val EXCHANGE_FINISH_CAP = OpeningEventRules.EXCHANGE_FINISH_CAP
        val FURIBISHA_CANCEL_PLIES = OpeningEventRules.FURIBISHA_CANCEL_PLIES
        val BISHOP_DROP_CANCEL_PLIES = OpeningEventRules.BISHOP_DROP_CANCEL_PLIES

        val ROOK_TYPES = setOf(PieceType.ROOK, PieceType.PROM_ROOK)
        val BISHOP_TYPES = setOf(PieceType.BISHOP, PieceType.PROM_BISHOP)
        val BISHOP_HOME = BfSquare(8, 8)
        val FURIBISHA_FILES = setOf(5, 6, 7, 8)
        val FURIBISHA_LABELS = setOf("中飛車", "四間飛車", "三間飛車", "向かい飛車", "振り飛車（その他）")
    }
}
