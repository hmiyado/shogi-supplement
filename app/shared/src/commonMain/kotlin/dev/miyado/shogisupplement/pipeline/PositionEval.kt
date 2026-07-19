package dev.miyado.shogisupplement.pipeline

import dev.miyado.shogisupplement.blunder.Score

/**
 * 1局面分のエンジン解析結果（multipv=1 の最善手情報）。
 *
 * report_kifu.py の evals リストの各要素に対応:
 *   `{"score": pv1.get("score"), "pv": pv1.get("pv") or []}`
 *
 * @property score 手番側視点のスコア（null = エンジン出力なし）
 * @property pv    最善手の読み筋（USI 手列）。なければ空リスト
 */
data class PositionEval(
    val score: Score?,
    val pv: List<String>,
)
