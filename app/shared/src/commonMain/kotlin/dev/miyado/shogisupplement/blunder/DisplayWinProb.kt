package dev.miyado.shogisupplement.blunder

import kotlin.math.exp

/**
 * 表示用勝率換算（実測較正版）。
 *
 * 悪手判定系（BlunderJudge / 悪手抽出 / 係数表）が使う s=600 とは別物。
 * こちらは実対局データへのフィット値 s=1254 を使い、UI への損失表示に専用する。
 * 出典: research/docs/winrate-calibration.md
 *
 * ⚠️ 判定系（BlunderJudge.SIGMOID_SCALE = 600）は係数表 v3 まで変更禁止。
 *    表示（このオブジェクト）と判定を混在させないこと。
 */
object DisplayWinProb {

    /**
     * 表示用シグモイドスケール（実測較正値）。
     * 1/(1+exp(-cp / DISPLAY_SIGMOID_SCALE)) で勝率に換算する。
     * 出典: research/docs/winrate-calibration.md
     */
    const val DISPLAY_SIGMOID_SCALE = 1254.0

    /**
     * cp → 表示用勝率 [0,1]（s=1254 の較正版シグモイド）。
     *
     * 例:
     *   cp=0    → 0.500（互角）
     *   cp=600  → ≈ 0.618
     *   cp=1254 → ≈ 0.731
     */
    fun winProb(cp: Int): Double = 1.0 / (1.0 + exp(-cp / DISPLAY_SIGMOID_SCALE))

    /**
     * 悪手の表示用勝率損失を返す（s=1254 較正版）。
     *
     * @param cpBefore 悪手前局面の評価値（手番側視点 cp。BlunderJudge.toCp 準拠）
     * @param cpAfter  悪手後局面の評価値（次手番側視点 cp。BlunderJudge.toCp 準拠）
     * @return 勝率損失 [0, 1]（= winProb(cpBefore) - winProb(-cpAfter)）
     *
     * BlunderJudge のロス計算 winProb(cpBefore) - winProb(-cpAfter) と同形だが
     * シグモイドスケールが異なるため値が変わる（s=1254 では同じ cp 損失でも小さく見える）。
     */
    fun lossWp(cpBefore: Int, cpAfter: Int): Double =
        winProb(cpBefore) - winProb(-cpAfter)
}
