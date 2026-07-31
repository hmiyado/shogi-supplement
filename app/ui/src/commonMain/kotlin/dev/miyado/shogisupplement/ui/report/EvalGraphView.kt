package dev.miyado.shogisupplement.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.db.PositionEvalRow
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.theme.shogiColors
import kotlin.math.roundToInt

/**
 * 評価値グラフ用に正規化した1点。先手視点・クランプ済みの値のみ持つ
 * （表示側は符号や勝率換算を一切行わない。DESIGN.md「先手視点で統一」）。
 */
data class EvalGraphPoint(val ply: Int, val clampedCp: Int)

/** グラフの縦軸クランプ幅（cp）。悪手判定閾値の4倍程度を確保し、
 * 通常の優劣の推移が視認できる範囲に収める。詰みはこの値に張り付ける。 */
const val EVAL_GRAPH_CLAMP_CP = 2000

/**
 * [PositionEvalRow] を評価値グラフ用の点列に変換する（ply昇順ソート・クランプ適用）。
 * mateIn は符号のみ使い、クランプ上限/下限に張り付ける（実際の詰み手数は表示に使わない。
 * グラフは形勢の推移の概観が目的で、詰み手数の精緻な表現は他の表示（ナビ行等）が担うため）。
 */
fun buildEvalGraphPoints(positionEvals: List<PositionEvalRow>): List<EvalGraphPoint> =
    positionEvals
        .sortedBy { it.ply }
        .mapNotNull { row ->
            val mateIn = row.mateIn
            val scoreCp = row.scoreCp
            val cp = when {
                mateIn != null -> if (mateIn > 0) EVAL_GRAPH_CLAMP_CP else -EVAL_GRAPH_CLAMP_CP
                scoreCp != null -> scoreCp.coerceIn(-EVAL_GRAPH_CLAMP_CP, EVAL_GRAPH_CLAMP_CP)
                else -> return@mapNotNull null
            }
            EvalGraphPoint(ply = row.ply, clampedCp = cp)
        }

/**
 * 評価値グラフカード（手数×評価値の折れ線。悪手位置にマーカー表示）。
 *
 * 色は意味の三色体系を厳守する: loss（朱）は「悪手・損失専用」のため、
 * 形勢が悪い側の領域を朱で塗るような表現はしない（悪手マーカーの点にのみ使う）。
 * 線・ゼロ基準線はいずれも中立色（ink系/line）に統一する。
 *
 * @param points [buildEvalGraphPoints] 済みの点列（空なら何も描画しない＝呼び出し側で件数ガードする）
 * @param maxPly 横軸の最大値（対局の総手数）。points の最大ply未満にはしない
 * @param blunderPlies 悪手マーカーを打つ ply の集合（position_eval に対応データがない ply は無視）
 * @param onPlyTapped タップした位置に最も近い ply。呼び出し側でナビゲーション（該当手へジャンプ）に使う
 */
@Composable
fun EvalGraphCard(
    points: List<EvalGraphPoint>,
    maxPly: Int,
    blunderPlies: Set<Int>,
    modifier: Modifier = Modifier,
    onPlyTapped: (Int) -> Unit = {},
) {
    if (points.isEmpty()) return
    val shogiColors = MaterialTheme.shogiColors
    val lineColor = MaterialTheme.colorScheme.onSurface
    val zeroLineColor = shogiColors.line
    val markerColor = shogiColors.loss
    val effectiveMaxPly = maxOf(maxPly, points.maxOf { it.ply }, 1)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                AppStrings.EVAL_GRAPH_TITLE,
                style = MaterialTheme.typography.labelMedium,
                color = shogiColors.ink2,
            )
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .padding(top = 8.dp)
                    .pointerInput(effectiveMaxPly) {
                        detectTapGestures { offset: Offset ->
                            val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                            val ply = (ratio * effectiveMaxPly).roundToInt().coerceIn(0, effectiveMaxPly)
                            onPlyTapped(ply)
                        }
                    },
            ) {
                val w = size.width
                val h = size.height
                fun xOf(ply: Int): Float = if (effectiveMaxPly == 0) 0f else w * ply / effectiveMaxPly
                fun yOf(cp: Int): Float = h / 2f - (h / 2f) * (cp.toFloat() / EVAL_GRAPH_CLAMP_CP)

                drawLine(
                    color = zeroLineColor,
                    start = Offset(0f, h / 2f),
                    end = Offset(w, h / 2f),
                    strokeWidth = 1.dp.toPx(),
                )

                for (i in 0 until points.size - 1) {
                    val p0 = points[i]
                    val p1 = points[i + 1]
                    drawLine(
                        color = lineColor,
                        start = Offset(xOf(p0.ply), yOf(p0.clampedCp)),
                        end = Offset(xOf(p1.ply), yOf(p1.clampedCp)),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                }

                // 悪手マーカー（朱）。
                val byPly = points.associateBy { it.ply }
                for (ply in blunderPlies) {
                    val p = byPly[ply] ?: continue
                    drawCircle(
                        color = markerColor,
                        radius = 2.5.dp.toPx(),
                        center = Offset(xOf(p.ply), yOf(p.clampedCp)),
                    )
                }
            }
        }
    }
}
