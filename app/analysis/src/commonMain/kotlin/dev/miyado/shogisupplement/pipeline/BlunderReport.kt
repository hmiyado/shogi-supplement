package dev.miyado.shogisupplement.pipeline

import dev.miyado.shogisupplement.classify.ClassificationResult
import dev.miyado.shogisupplement.judge.Judgement

/** 悪手レポート。 @property ply 手数。 @property side 手番。 @property moveUsi 実戦手。 @property bestUsi 最善手。 @property lossWp 勝率損失。 @property classification 分類結果。 @property judgement 相応判定。 @property bestPv 悪手前のPV。 @property punishPv 悪手後のPV。 @property cpBefore 悪手前のcp。 @property cpAfter 悪手後のcp。 @property secondUsi 次善手。 @property secondCp 次善手のcp。 */
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
    val secondUsi: String? = null,
    val secondCp: Int? = null,
)
