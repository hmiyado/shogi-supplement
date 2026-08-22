package dev.miyado.shogisupplement.drill

import dev.miyado.shogisupplement.blunder.BlunderJudge
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.engine.PvInfo

/**
 * ドリルの正誤を判定する。最善手や実戦悪手との一致は即時判定する。
 * 保存済みMultiPV=2の境界で確定できない場合だけ、注入されたエンジンで二次判定する。
 */
object DrillJudge {

    /**
     * 最善手との勝率差がこれ以下なら正解とする閾値。
     * 定数化してあるので後から調整可能。
     */
    const val CORRECT_LOSS_WP_THRESHOLD = 0.05

    /** 判定結果の理由区分。 */
    enum class Reason {
        MATCH_BEST,

        MATCH_ACTUAL_BLUNDER,

        PRIMARY_MATCH_SECOND,

        /** top-2圏外でpv2の下界が閾値を超えたため、不正解を確定した。 */
        PRIMARY_OUT_OF_TOP2,

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
     * 一次判定を行う。userMoveUsiはbest_usiとの不一致を確認済みとする。
     * @param blunder 出題元の悪手レコード。
     * @param userMoveUsi ユーザーのUSI手。
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

    /** ドリルの正誤を判定する。 @param blunder 出題元の悪手。 @param userMoveUsi ユーザーのUSI手。 @param engineAnalyze 二次判定用関数。 @return DrillResult。 */
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
                reason = if (userMoveUsi == blunder.secondUsi) Reason.PRIMARY_MATCH_SECOND else Reason.PRIMARY_OUT_OF_TOP2,
            )
            PrimaryVerdict.Ambiguous, PrimaryVerdict.Unavailable -> Unit // 4. 二次判定へ
        }

        // ── 4. 二次判定（エンジン評価） ───────────────────────────────────
        if (engineAnalyze != null) {
            return judgeByEngine(blunder, userMoveUsi, engineAnalyze)
        }

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

        val pvBefore = engineAnalyze(sfenBefore)
        val bestScore = pvBefore.firstOrNull()?.score
            ?: return DrillResult(
                isCorrect = false,
                lossWp = Double.NaN,
                userMoveUsi = userMoveUsi,
                bestMoveUsi = blunder.bestUsi,
                reason = Reason.ENGINE_EVAL,
            )

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

        val pvAfterUser = engineAnalyze(sfenAfterUser)
        val afterScore = pvAfterUser.firstOrNull()?.score
            ?: return DrillResult(
                isCorrect = false,
                lossWp = Double.NaN,
                userMoveUsi = userMoveUsi,
                bestMoveUsi = blunder.bestUsi,
                reason = Reason.ENGINE_EVAL,
            )

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
