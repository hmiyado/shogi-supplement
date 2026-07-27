package dev.miyado.shogisupplement.drill

import dev.miyado.shogisupplement.blunder.BlunderJudge
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.engine.PvInfo

/**
 * ドリル正誤判定ロジック（shared に置き、エンジンは関数注入で差し替え可能）。
 *
 * ハイブリッド判定（一次＝端末内・保存済みデータのみ、二次＝エンジン）:
 * 1. ユーザーの手が best_usi と一致 → 即正解（エンジン不要）
 * 2. ユーザーの手が move_usi（実戦悪手）と一致 → 即不正解（エンジン不要）
 * 3. 一次判定（[judgePrimary]）: 出題局面は MultiPV=2 で解析済みなので、
 *    「top-2圏外の手の評価値は pv2 以下」という探索の境界保証が使える。
 *    - 指した手 = pv2 → 正確な loss_wp が分かるので確定判定
 *    - 指した手が top-2圏外 かつ pv2 の loss_wp が既に閾値超 → 確定不正解
 *      （実際の loss_wp はこれ以上悪いことはあっても良くなることはない）
 *    - それ以外（top-2圏外だが pv2 の loss_wp が閾値内）→ 曖昧領域。二次判定へ
 *    - pv2 データが無い旧解析 → 一次判定不能。二次判定へ
 * 4. 二次判定（エンジン評価、[engineAnalyze] が非 null のときのみ）:
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

        /** 一次判定: 指した手が pv2（次善手）と一致し、保存済みスコアから loss_wp が確定した。 */
        PRIMARY_MATCH_SECOND,

        /**
         * 一次判定: 指した手が top-2 圏外で、pv2 の loss_wp（下界）が既に閾値を超えていたため
         * 確定不正解（実際の loss_wp はこの下界以上）。
         */
        PRIMARY_OUT_OF_TOP2,

        /** 二次判定（エンジン評価、または一次判定不能でエンジン未注入だった場合の不正解フォールバック）。 */
        ENGINE_EVAL,
    }

    /** [judgePrimary] の判定結果。 */
    sealed interface PrimaryVerdict {
        /** 正解確定（保存済みデータのみで判定できた）。 */
        data class Correct(val lossWp: Double) : PrimaryVerdict

        /** 不正解確定（保存済みデータのみで判定できた）。 */
        data class Incorrect(val lossWp: Double) : PrimaryVerdict

        /** top-2圏外だが pv2 の loss_wp が閾値内 → 保存済みデータだけでは確定できない。二次判定が必要。 */
        data object Ambiguous : PrimaryVerdict

        /** pv2 データが無い（旧解析）ため一次判定そのものが行えない。二次判定が必要。 */
        data object Unavailable : PrimaryVerdict
    }

    /**
     * 一次判定（純関数・エンジン不要）。
     *
     * 呼び出し前提: [userMoveUsi] は既に best_usi（pv1）と不一致であることが確認済み
     * （[judge] のステップ1で処理済みのため、ここでは pv1 一致は扱わない）。
     *
     * @param blunder     出題元の悪手レコード（cpBefore/secondUsi/secondCp を使用）
     * @param userMoveUsi ユーザーが指した手の USI 文字列
     */
    fun judgePrimary(blunder: BlunderRecord, userMoveUsi: String): PrimaryVerdict {
        val cpBefore = blunder.cpBefore
        val secondUsi = blunder.secondUsi
        val secondCp = blunder.secondCp
        if (cpBefore == null || secondUsi == null || secondCp == null) {
            return PrimaryVerdict.Unavailable
        }

        // pv1・pv2 は同一局面（sfenBefore）・同一視点（手番側）のスコアなので、
        // afterUser 局面を解析せずに直接 winProb の差で loss_wp を求められる。
        val wpBest = BlunderJudge.winProb(cpBefore.toInt())
        val wpSecond = BlunderJudge.winProb(secondCp.toInt())
        val lossWpOfSecond = (wpBest - wpSecond).coerceAtLeast(0.0)

        if (userMoveUsi == secondUsi) {
            return if (lossWpOfSecond <= CORRECT_LOSS_WP_THRESHOLD) {
                PrimaryVerdict.Correct(lossWpOfSecond)
            } else {
                PrimaryVerdict.Incorrect(lossWpOfSecond)
            }
        }

        // top-2圏外: 実際の loss_wp は lossWpOfSecond 以上であることが保証されている
        // （MultiPV=2 の境界保証）。この下界だけで既に閾値超なら、実測しなくても確定不正解。
        return if (lossWpOfSecond > CORRECT_LOSS_WP_THRESHOLD) {
            PrimaryVerdict.Incorrect(lossWpOfSecond)
        } else {
            PrimaryVerdict.Ambiguous
        }
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

        // ── 3. 一次判定（端末内・保存済みデータのみ） ────────────────────────
        when (val primary = judgePrimary(blunder, userMoveUsi)) {
            is PrimaryVerdict.Correct -> return DrillResult(
                isCorrect = true,
                lossWp = primary.lossWp,
                userMoveUsi = userMoveUsi,
                bestMoveUsi = blunder.bestUsi,
                reason = Reason.PRIMARY_MATCH_SECOND,
            )
            is PrimaryVerdict.Incorrect -> return DrillResult(
                isCorrect = false,
                lossWp = primary.lossWp,
                userMoveUsi = userMoveUsi,
                bestMoveUsi = blunder.bestUsi,
                // Incorrect は「pv2と一致したが閾値超」と「top-2圏外で下界が既に閾値超」の
                // 2経路から来る（judgePrimary参照）。表示用の理由はどちらの経路かで分ける。
                reason = if (userMoveUsi == blunder.secondUsi) Reason.PRIMARY_MATCH_SECOND else Reason.PRIMARY_OUT_OF_TOP2,
            )
            PrimaryVerdict.Ambiguous, PrimaryVerdict.Unavailable -> Unit // 4. 二次判定へ
        }

        // ── 4. 二次判定（エンジン評価） ───────────────────────────────────
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
