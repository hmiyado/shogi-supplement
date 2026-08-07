package dev.miyado.shogisupplement.pipeline

import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.engine.PvInfo

/**
 * 1局面分のエンジン解析結果（multipv=1 の最善手情報＋multipv=2 の次善手情報）。
 *
 * report_kifu.py の evals リストの各要素に対応:
 *   `{"score": pv1.get("score"), "pv": pv1.get("pv") or []}`
 *
 * @property score 手番側視点のスコア（null = エンジン出力なし）
 * @property pv    最善手の読み筋（USI 手列）。なければ空リスト
 * @property pv2Score  次善手（multipv=2）の手番側視点スコア（null = MultiPV=2 出力なし）
 * @property pv2MoveUsi 次善手の指し手（USI）。ドリルの一次判定（pv1/pv2 圏内判定）にのみ使う。
 *   次善手の継続読み筋全体は持たない（一次判定に必要なのは先頭手とスコアのみのため）。
 */
data class PositionEval(
    val score: Score?,
    val pv: List<String>,
    val pv2Score: Score? = null,
    val pv2MoveUsi: String? = null,
)

/**
 * 1局面ぶんの MultiPV 結果（[PvInfo] のリスト）を [PositionEval] に変換する。
 *
 * [dev.miyado.shogisupplement.engine.AnalysisOrchestrator] と
 * [ProgressiveReportState] の両方が使う共通の変換（multipv=1/2 を取り出すだけの
 * 決め打ちロジックを2箇所に重複させない）。
 */
fun List<PvInfo>.toPositionEval(): PositionEval {
    val pv1 = firstOrNull { it.multipv == 1 }
    val pv2 = firstOrNull { it.multipv == 2 }
    return PositionEval(
        score = pv1?.score,
        pv = pv1?.pv ?: emptyList(),
        pv2Score = pv2?.score,
        pv2MoveUsi = pv2?.pv?.firstOrNull(),
    )
}
