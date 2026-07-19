package dev.miyado.shogisupplement.blunder

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * エンジンスコア（USI infoのcp/mate）。
 * 常に「その局面の手番側から見た値」。mate>0 は手番側が詰ます、mate<0 は詰まされる。
 */
sealed interface Score {
    data class Cp(val value: Int) : Score
    data class Mate(val plies: Int) : Score
}

/** 悪手の種別（悪手定義 v1.1）。 */
enum class BlunderType {
    /** 詰み見逃し: 詰みがあったのに逃した */
    MATE_MISS,

    /** 頓死: 詰みの無い局面から相手の詰み筋に入った（勝負が残る局面のみ） */
    SUDDEN_DEATH,

    /** スイング: 500cp以上損 ∧ 指す前勝率5-95% ∧ 指した後マイナス */
    EVAL_SWING,
}

/** 悪手判定の結果。isBlunder=false のとき type は null。 */
data class BlunderVerdict(
    val isBlunder: Boolean,
    val type: BlunderType?,
    val lossCp: Int,
    val winProbBefore: Double,
    val winProbAfter: Double,
)

/**
 * 悪手定義 v1.1（scripts/report_kifu.py is_blunder_v1() の移植）。
 *
 * 判定式:
 * - 詰み見逃し: 指す前に自分の詰みあり（mate>0）∧ 指した後に相手視点で mate<=0 でない
 * - 頓死: 指した後に相手の詰みあり ∧ 指す前は被詰みでない ∧ 指す前 cp > -500（勝負が残る）
 * - スイング: loss_cp >= 500 ∧ 指す前勝率 5〜95% ∧ 指した後自分視点マイナス
 *
 * 勝率変換: 1/(1+exp(-cp/600))、mate n → ±(30000-|n|)
 */
object BlunderJudge {

    const val MATE_CP = 30_000
    const val SIGMOID_SCALE = 600.0
    const val LOSS_THRESHOLD_CP = 500
    const val SUDDEN_DEATH_FLOOR_CP = -500

    /** cp → 勝率 [0,1]。 */
    fun winProb(cp: Int): Double = 1.0 / (1.0 + exp(-cp / SIGMOID_SCALE))

    /** Score → cp（mateは ±(30000-|n|)、mate 0 は -30000、cpは±30000にクランプ）。 */
    fun toCp(score: Score): Int = when (score) {
        is Score.Cp -> max(-MATE_CP, min(MATE_CP, score.value))
        is Score.Mate ->
            if (score.plies == 0) -MATE_CP
            else (if (score.plies > 0) 1 else -1) * (MATE_CP - abs(score.plies))
    }

    /**
     * @param before 指す前の局面のスコア（指し手側=手番側視点）
     * @param after  指した後の局面のスコア（相手側=次の手番側視点）
     */
    fun judge(before: Score, after: Score): BlunderVerdict {
        val cpBefore = toCp(before)
        val cpAfter = toCp(after)
        // 相手視点cpAfterを自分視点に反転して損失を計算
        val lossCp = cpBefore + cpAfter
        val wpBefore = winProb(cpBefore)
        val wpAfter = winProb(-cpAfter)

        val mateBefore = (before as? Score.Mate)?.plies
        val mateAfter = (after as? Score.Mate)?.plies

        // 詰み見逃し: 自分に詰みがあった ∧ 指した後も相手視点でmate<=0（=自分の詰み継続）でない
        if (mateBefore != null && mateBefore > 0 && !(mateAfter != null && mateAfter <= 0)) {
            return BlunderVerdict(true, BlunderType.MATE_MISS, lossCp, wpBefore, wpAfter)
        }
        // 頓死: 指した後に相手の詰み ∧ 指す前は被詰みでない ∧ 既に大差の負けではない
        if (mateAfter != null && mateAfter > 0 &&
            !(mateBefore != null && mateBefore < 0) &&
            cpBefore > SUDDEN_DEATH_FLOOR_CP
        ) {
            return BlunderVerdict(true, BlunderType.SUDDEN_DEATH, lossCp, wpBefore, wpAfter)
        }
        // スイング
        val isSwing = lossCp >= LOSS_THRESHOLD_CP &&
            wpBefore in 0.05..0.95 &&
            cpAfter > 0 // 指した後、自分視点でマイナス
        return if (isSwing) {
            BlunderVerdict(true, BlunderType.EVAL_SWING, lossCp, wpBefore, wpAfter)
        } else {
            BlunderVerdict(false, null, lossCp, wpBefore, wpAfter)
        }
    }
}
