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
 * 悪手定義v1.1。最善手一致を除外し、詰み見逃し、頓死、スイングを順に判定する。
 * 勝率はcpからシグモイドで換算し、mateは±(30000-|n|)へ変換する。
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

    /** 悪手を判定する。 @param before 指す前の手番側スコア。 @param after 指した後の相手側スコア。 @param moveUsi 実際のUSI手。 @param bestUsi 最善手のUSI。 */
    fun judge(before: Score, after: Score, moveUsi: String? = null, bestUsi: String? = null): BlunderVerdict {
        val cpBefore = toCp(before)
        val cpAfter = toCp(after)
        val lossCp = cpBefore + cpAfter
        val wpBefore = winProb(cpBefore)
        val wpAfter = winProb(-cpAfter)

        if (moveUsi != null && bestUsi != null && moveUsi == bestUsi) {
            // 指す前と指した後はルートの違う別々の探索で、固定ノードのため到達深さも揃わない。
            // 同じ手を辿っても評価値は一致せず、閾値を超えて振れることがある。
            return BlunderVerdict(false, null, lossCp, wpBefore, wpAfter)
        }

        val mateBefore = (before as? Score.Mate)?.plies
        val mateAfter = (after as? Score.Mate)?.plies

        if (mateBefore != null && mateBefore > 0 && !(mateAfter != null && mateAfter <= 0)) {
            return BlunderVerdict(true, BlunderType.MATE_MISS, lossCp, wpBefore, wpAfter)
        }
        if (mateAfter != null && mateAfter > 0 &&
            !(mateBefore != null && mateBefore < 0) &&
            cpBefore > SUDDEN_DEATH_FLOOR_CP
        ) {
            return BlunderVerdict(true, BlunderType.SUDDEN_DEATH, lossCp, wpBefore, wpAfter)
        }
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
