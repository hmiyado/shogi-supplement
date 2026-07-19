package dev.miyado.shogisupplement.pipeline

import dev.miyado.shogisupplement.classify.ClassificationResult
import dev.miyado.shogisupplement.judge.Judgement

/**
 * 1件の悪手レポート。report_kifu.py の analyze_file() が返すリストの要素に対応。
 *
 * @property ply           手数（1 始まり）
 * @property side          "sente" / "gote"
 * @property moveUsi       悪手 USI
 * @property bestUsi       最善手 USI（pv の先頭。なければ null）
 * @property lossWp        勝率損失 = winProb(cpBefore) - winProb(-cpAfter)
 * @property classification 6カテゴリ分類結果
 * @property judgement     相応判定結果
 * @property bestPv        悪手前局面の最善読み筋（スペース区切り USI。NULL可）
 * @property punishPv      悪手後局面からの相手の最善応手列（スペース区切り USI。NULL可）
 * @property cpBefore      悪手前局面の評価値（手番側視点 cp。BlunderJudge.toCp 準拠。NULL可）
 * @property cpAfter       悪手後局面の評価値（次手番側視点 cp。BlunderJudge.toCp 準拠。NULL可）
 *                         損失 cp = cpBefore + cpAfter（cpAfter は相手視点なので加算）
 */
data class BlunderReport(
    val ply: Int,
    val side: String,
    val moveUsi: String,
    val bestUsi: String?,
    val lossWp: Double,
    val classification: ClassificationResult,
    val judgement: Judgement,
    val bestPv: String? = null,
    val punishPv: String? = null,
    val cpBefore: Int? = null,
    val cpAfter: Int? = null,
)
