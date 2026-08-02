package dev.miyado.shogisupplement.classify

import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.board.Side
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.pipeline.PositionEval

/** 悪手の6カテゴリ分類結果（classify_blunders.py classify_move() の移植）。 */
data class ClassificationResult(
    /** カテゴリ名（6種類）。 */
    val category: String,

    /** 相手の最善応手列での駒割変化 − 最善を指した場合の駒割変化（< MATERIAL_THRESHOLD で駒損判定）。 */
    val diffMaterial: Int,

    /** 相手の最善応手列で mover が王手された回数。 */
    val punishChecks: Int,

    /** 相手の最善応手の初手が、直前に指した駒を取ったか（タダ取られ判定用）。 */
    val tookMovedPiece: Boolean,

    /** 詰み見逃しの場合の見逃した詰み手数（詰み見逃し以外は null）。 */
    val missedMateIn: Int?,
)

/**
 * 悪手の6カテゴリ分類（scripts/classify_blunders.py classify_move() の Kotlin 移植）。
 *
 * 分類優先順位:
 * 1. 詰み見逃し: ply t で自分に詰みがあり、指した後消えた
 * 2. 頓死: t で自玉に詰みがなく、指した後詰まされる
 * 3. 駒損（即取り）: 差分駒損 ≤ -5 かつ相手の最善応手の初手が駒取り
 * 4. 駒損（タクティクス）: 差分駒損 ≤ -5 かつ初手は駒取りでない（両取り・素抜き等）
 * 5. 玉の危険（寄せ）: 相手の最善応手列で2回以上王手される
 * 6. 位置的・その他: 上記いずれでもない
 */
object BlunderClassifier {

    /** 銀級以上の差分駒損で「駒損」扱い（classify_blunders.py MATERIAL_THRESHOLD と同値）。 */
    const val MATERIAL_THRESHOLD = -5

    /** 読み筋追跡の上限ply（classify_blunders.py PV_HORIZON と同値）。 */
    const val PV_HORIZON = 10

    /**
     * 悪手を分類する。
     *
     * 副作用: board に move を push して返す（呼び出し側はその後の局面から続けられる）。
     * これは classify_blunders.py classify_move() と同一の挙動。
     *
     * @param board push 前の盤面（mover のターン）
     * @param move  悪手（USI 文字列をパース済み）
     * @param cur   ply t（push 前）のエンジン評価（score + pv1 = 最善手の読み筋）
     * @param nxt   ply t+1（push 後）のエンジン評価（score + pv1 = 相手の最善応手）
     */
    fun classify(
        board: ShogiBoard,
        move: ShogiMove,
        cur: PositionEval,
        nxt: PositionEval,
    ): ClassificationResult {
        val curMate = (cur.score as? Score.Mate)?.plies
        val nxtMate = (nxt.score as? Score.Mate)?.plies

        val hadMate = curMate != null && curMate > 0
        val kept = nxtMate != null && nxtMate <= 0
        val getsMated = nxtMate != null && nxtMate > 0
        val alreadyLost = curMate != null && curMate < 0

        val moverColor = board.turn

        // 最善手の読み筋での駒割変化（push 前の盤面から）
        val (bestMaterial, _) = pvStats(board, cur.pv, moverColor)

        // 悪手を指す
        board.push(move)

        // 相手の最善応手列での駒割変化・王手回数（push 後の盤面から）
        val punishPv = nxt.pv
        val (punishMaterial, punishChecks) = pvStats(board, punishPv, moverColor)

        // 相手の最善応手の初手が駒取りかどうか、取った駒が直前に指したコマかどうか
        var firstPunishIsCapture = false
        var tookMovedPiece = false
        if (punishPv.isNotEmpty()) {
            try {
                val xm = ShogiMove.fromUsi(punishPv[0])
                if (xm.dropType == null && board.pieceAt(xm.to) != null) {
                    firstPunishIsCapture = true
                    tookMovedPiece = xm.to == move.to
                }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // ignore
            }
        }

        val diffMaterial = punishMaterial - bestMaterial

        val category = when {
            hadMate && !kept -> "詰み見逃し"
            getsMated && !alreadyLost -> "頓死"
            diffMaterial <= MATERIAL_THRESHOLD && firstPunishIsCapture -> "駒損（即取り）"
            diffMaterial <= MATERIAL_THRESHOLD -> "駒損（タクティクス）"
            punishChecks >= 2 -> "玉の危険（寄せ）"
            else -> "位置的・その他"
        }

        return ClassificationResult(
            category = category,
            diffMaterial = diffMaterial,
            punishChecks = punishChecks,
            tookMovedPiece = tookMovedPiece,
            missedMateIn = if (hadMate) curMate else null,
        )
    }

    /**
     * 読み筋を辿り、(perspective 視点の駒割変化, perspective への王手回数) を返す。
     * classify_blunders.py pv_stats() の Kotlin 移植。
     *
     * 途中で例外が起きたら打ち切り（Python の except Exception: pass と同等）。
     * push した分だけ必ず pop して盤面を元に戻す。
     */
    internal fun pvStats(
        board: ShogiBoard,
        pv: List<String>,
        perspective: Side,
    ): Pair<Int, Int> {
        var material = 0
        var checks = 0
        var nPushed = 0

        try {
            for (usiStr in pv.take(PV_HORIZON)) {
                val mv = ShogiMove.fromUsi(usiStr)
                val side = board.turn

                // 取る手の場合は駒割変化を追跡（打ち駒は取れない）
                if (mv.dropType == null) {
                    val captured = board.pieceAt(mv.to)
                    if (captured != null) {
                        val v = captured.type.materialValue
                        material += if (side == perspective) v else -v
                    }
                }

                board.push(mv)
                nPushed++

                // perspective が王手されているか
                if (board.turn == perspective && board.isCheck()) {
                    checks++
                }
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Python の `except Exception: pass` と同等
        }

        repeat(nPushed) { board.pop() }
        return material to checks
    }
}
