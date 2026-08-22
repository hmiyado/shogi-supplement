package dev.miyado.shogisupplement.blunder

import kotlin.math.exp

/** 表示用勝率換算。判定用のs=600とは分離し、表示には較正値s=1254を使う。 */
object DisplayWinProb {

    /**
     * 表示用シグモイドスケール（実測較正値）。
     * 1/(1+exp(-cp / DISPLAY_SIGMOID_SCALE)) で勝率に換算する。
     * lishogi較正データで較正した値。
     */
    const val DISPLAY_SIGMOID_SCALE = 1254.0

    /** cpをs=1254のシグモイドで表示用勝率へ変換する。 */
    fun winProb(cp: Int): Double = 1.0 / (1.0 + exp(-cp / DISPLAY_SIGMOID_SCALE))

    /** 悪手の表示用勝率損失を返す。 @param cpBefore 悪手前の手番側cp。 @param cpAfter 悪手後の相手側cp。 @return 勝率損失。 */
    fun lossWp(cpBefore: Int, cpAfter: Int): Double =
        winProb(cpBefore) - winProb(-cpAfter)
}
