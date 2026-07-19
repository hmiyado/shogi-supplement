package dev.miyado.shogisupplement.blunder

import dev.miyado.shogisupplement.text.AppStrings
import kotlin.math.roundToInt

/**
 * レポート手送り時の局面評価値表示。
 *
 * position_eval は先手視点（正 = 先手優勢）で保存されているため、
 * ユーザーが後手のときは符号を反転してユーザー視点に揃える。
 *
 * 表示規約（DESIGN.md）:
 * - 符号は必ず付ける（+/−/±0）。数値は Mono フォント（表示側で指定）
 * - 正（ユーザー優勢）= 紺青、負（劣勢）= 朱、0 = 中立
 */
object PositionEvalDisplay {

    /** 表示状態: テキストと優劣（色分け用）。 */
    data class EvalLabel(
        val text: String,
        /** 正 = ユーザー優勢（紺青）、負 = 劣勢（朱）、0 = 互角（中立）。 */
        val sign: Int,
    )

    /**
     * 局面評価値の表示ラベルを返す。scoreCp と mateIn が両方 null なら null（非表示）。
     *
     * @param scoreCp 先手視点 cp（正 = 先手優勢）。詰み局面は null
     * @param mateIn 詰みまでの手数（先手視点: 正 = 先手が詰ます）。非詰みは null
     * @param userIsGote ユーザーが後手なら true（符号反転する）
     * @param evalDisplay 'cp' = 評価値表示 / 'wp' = 勝率表示
     * @param ply 局面の手数（0 = 初期局面）。mate_in=0 の正確な勝敗判定に必要。null の場合は負けとして扱う
     */
    fun format(
        scoreCp: Int?,
        mateIn: Int?,
        userIsGote: Boolean,
        evalDisplay: String,
        ply: Int? = null,
    ): EvalLabel? {
        // 詰み局面: 手数つきで「N手詰」。ユーザー視点の勝ち負けで符号を決める
        if (mateIn != null) {
            // mate_in=0: その局面の手番側が詰まされている。ply の奇偶から手番を特定する
            if (mateIn == 0) {
                val userWins = if (ply != null) {
                    val isSenteToMove = (ply % 2 == 0)
                    userIsGote == isSenteToMove
                } else {
                    false // ply 不明時は負けとして安全側に倒す
                }
                val text = if (userWins) AppStrings.POSITION_EVAL_MATE_ZERO_WIN
                           else AppStrings.POSITION_EVAL_MATE_ZERO_LOSS
                return EvalLabel(text, sign = if (userWins) 1 else -1)
            }
            val userMate = if (userIsGote) -mateIn else mateIn
            val text = AppStrings.positionEvalMate(userMate)
            return EvalLabel(text, sign = if (userMate > 0) 1 else -1)
        }
        val cp = scoreCp ?: return null
        val userCp = if (userIsGote) -cp else cp
        return when (evalDisplay) {
            "wp" -> {
                val pct = (DisplayWinProb.winProb(userCp) * 100).roundToInt()
                EvalLabel(
                    AppStrings.positionEvalWp(pct),
                    sign = userCp.compareTo(0),
                )
            }
            else -> EvalLabel(
                AppStrings.cpSignedLabel(userCp),
                sign = userCp.compareTo(0),
            )
        }
    }
}
