package dev.miyado.shogisupplement.drill

import dev.miyado.shogisupplement.blunder.BlunderJudge
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.engine.PvInfo

/**
 * ドリル正誤判定ロジック（shared に置き、エンジンは関数注入で差し替え可能）。
 *
 * 判定手順:
 * 1. ユーザーの手が best_usi と一致 → 即正解（エンジン不要）
 * 2. ユーザーの手が move_usi（実戦悪手）と一致 → 即不正解（エンジン不要）
 * 3. それ以外 → エンジン評価:
 *    - 出題局面を解析して最善評価値 score_best を取得
 *    - ユーザーの手を指した後の局面を解析して相手番評価値 score_after を取得
 *    - loss_wp = winProb(score_best) - winProb(-score_after)
 *    - loss_wp ≤ CORRECT_LOSS_WP_THRESHOLD なら正解
 */
object DrillJudge {

    /**
     * 最善手との勝率差がこれ以下なら正解とする閾値。
     * 定数化してあるので後から調整可能。
     */
    const val CORRECT_LOSS_WP_THRESHOLD = 0.05

    /** 判定結果の理由区分。 */
    enum class Reason {
        /** ユーザーの手が best_usi と完全一致した（エンジン不要）。 */
        MATCH_BEST,

        /** ユーザーの手が実戦の悪手 (move_usi) と同じだった（即不正解）。 */
        MATCH_ACTUAL_BLUNDER,

        /** エンジン評価による判定。 */
        ENGINE_EVAL,
    }

    /**
     * ドリル判定結果。
     *
     * @param isCorrect 正解なら true
     * @param lossWp    最善手とのloss_wp差（MATCH_BEST なら 0.0、エンジン判定失敗時は NaN の場合あり）
     * @param userMoveUsi  ユーザーが指した手の USI 文字列
     * @param bestMoveUsi  DB の best_usi（null の場合あり）
     * @param reason    判定に至った理由
     */
    data class DrillResult(
        val isCorrect: Boolean,
        val lossWp: Double,
        val userMoveUsi: String,
        val bestMoveUsi: String?,
        val reason: Reason,
        /** ENGINE_EVAL 判定時のユーザー手後の読み筋（スペース区切り USI 文字列。DB 保存不要）。 */
        val pv: String? = null,
    )

    /**
     * ドリルの正誤を判定する。
     *
     * @param blunder       出題元の悪手レコード（sfenBefore / bestUsi / moveUsi を使用）
     * @param userMoveUsi   ユーザーが指した手の USI 文字列
     * @param engineAnalyze エンジン解析関数 `(sfen: String) -> List<PvInfo>`。
     *                      null の場合はエンジン不要な即判定のみ行い、それ以外は失敗として不正解とする。
     * @return DrillResult
     */
    fun judge(
        blunder: BlunderRecord,
        userMoveUsi: String,
        engineAnalyze: ((sfen: String) -> List<PvInfo>)? = null,
    ): DrillResult {
        // ── 1. 最善手と一致 ─────────────────────────────────────────────
        if (blunder.bestUsi != null && userMoveUsi == blunder.bestUsi) {
            return DrillResult(
                isCorrect = true,
                lossWp = 0.0,
                userMoveUsi = userMoveUsi,
                bestMoveUsi = blunder.bestUsi,
                reason = Reason.MATCH_BEST,
            )
        }

        // ── 2. 実戦悪手と一致 ───────────────────────────────────────────
        if (userMoveUsi == blunder.moveUsi) {
            return DrillResult(
                isCorrect = false,
                lossWp = blunder.lossWp,
                userMoveUsi = userMoveUsi,
                bestMoveUsi = blunder.bestUsi,
                reason = Reason.MATCH_ACTUAL_BLUNDER,
            )
        }

        // ── 3. エンジン評価 ─────────────────────────────────────────────
        if (engineAnalyze != null) {
            return judgeByEngine(blunder, userMoveUsi, engineAnalyze)
        }

        // エンジン無し・即判定できない → 不正解
        return DrillResult(
            isCorrect = false,
            lossWp = Double.NaN,
            userMoveUsi = userMoveUsi,
            bestMoveUsi = blunder.bestUsi,
            reason = Reason.ENGINE_EVAL,
        )
    }

    private fun judgeByEngine(
        blunder: BlunderRecord,
        userMoveUsi: String,
        engineAnalyze: (sfen: String) -> List<PvInfo>,
    ): DrillResult {
        val sfenBefore = blunder.sfenBefore

        // 出題局面を解析（最善手の評価値取得: 手番側視点）
        val pvBefore = engineAnalyze(sfenBefore)
        val bestScore = pvBefore.firstOrNull()?.score
            ?: return DrillResult(
                isCorrect = false,
                lossWp = Double.NaN,
                userMoveUsi = userMoveUsi,
                bestMoveUsi = blunder.bestUsi,
                reason = Reason.ENGINE_EVAL,
            )

        // ユーザーの手を指した後の SFEN を計算
        val sfenAfterUser = try {
            val board = ShogiBoard.fromSfen(sfenBefore)
            board.push(ShogiMove.fromUsi(userMoveUsi))
            board.toSfen()
        } catch (e: Exception) {
            return DrillResult(
                isCorrect = false,
                lossWp = Double.NaN,
                userMoveUsi = userMoveUsi,
                bestMoveUsi = blunder.bestUsi,
                reason = Reason.ENGINE_EVAL,
            )
        }

        // ユーザー手後の局面を解析（相手番視点）
        val pvAfterUser = engineAnalyze(sfenAfterUser)
        val afterScore = pvAfterUser.firstOrNull()?.score
            ?: return DrillResult(
                isCorrect = false,
                lossWp = Double.NaN,
                userMoveUsi = userMoveUsi,
                bestMoveUsi = blunder.bestUsi,
                reason = Reason.ENGINE_EVAL,
            )

        // loss_wp 計算: 最善と比べて何ポイント勝率を損したか
        val wpBest = BlunderJudge.winProb(BlunderJudge.toCp(bestScore))
        val wpAfterUser = BlunderJudge.winProb(-BlunderJudge.toCp(afterScore))
        val lossWp = (wpBest - wpAfterUser).coerceAtLeast(0.0)

        val continuationPv = pvAfterUser.firstOrNull()?.pv
            ?.joinToString(" ")?.takeIf { it.isNotBlank() }

        return DrillResult(
            isCorrect = lossWp <= CORRECT_LOSS_WP_THRESHOLD,
            lossWp = lossWp,
            userMoveUsi = userMoveUsi,
            bestMoveUsi = blunder.bestUsi,
            reason = Reason.ENGINE_EVAL,
            pv = continuationPv,
        )
    }
}
