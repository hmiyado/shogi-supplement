package dev.miyado.shogisupplement.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.blunder.BlunderJudge
import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.db.PositionEvalRow
import dev.miyado.shogisupplement.pipeline.PositionEval
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.theme.shogiColors
import kotlin.math.roundToInt

/** 自分視点・クランプ済みの評価値。 */
data class EvalGraphPoint(val ply: Int, val clampedCp: Int)

/** 詰み評価もこの上下限へ丸める。 */
const val EVAL_GRAPH_CLAMP_CP = 2000

/** mateIn=0だけは符号がないため、手番から勝敗を復元する。 */
fun buildEvalGraphPoints(positionEvals: List<PositionEvalRow>, userIsGote: Boolean = false): List<EvalGraphPoint> =
    positionEvals
        .sortedBy { it.ply }
        .mapNotNull { row ->
            val mateIn = row.mateIn
            val scoreCp = row.scoreCp
            val senteCp = when {
                mateIn == 0 -> {
                    val isSenteToMove = row.ply % 2 == 0
                    if (isSenteToMove) -EVAL_GRAPH_CLAMP_CP else EVAL_GRAPH_CLAMP_CP
                }
                mateIn != null -> if (mateIn > 0) EVAL_GRAPH_CLAMP_CP else -EVAL_GRAPH_CLAMP_CP
                scoreCp != null -> scoreCp.coerceIn(-EVAL_GRAPH_CLAMP_CP, EVAL_GRAPH_CLAMP_CP)
                else -> return@mapNotNull null
            }
            val cp = if (userIsGote) -senteCp else senteCp
            EvalGraphPoint(ply = row.ply, clampedCp = cp)
        }

/** 手番側視点の進捗評価を先手視点、自分視点の順に変換する。 */
fun buildProgressiveEvalGraphPoints(evals: List<PositionEval>, userIsGote: Boolean = false): List<EvalGraphPoint> =
    evals.mapIndexedNotNull { ply, eval ->
        val score = eval.score ?: return@mapIndexedNotNull null
        val moverCp = when (score) {
            is Score.Mate -> if (score.plies > 0) EVAL_GRAPH_CLAMP_CP else -EVAL_GRAPH_CLAMP_CP
            is Score.Cp -> BlunderJudge.toCp(score).coerceIn(-EVAL_GRAPH_CLAMP_CP, EVAL_GRAPH_CLAMP_CP)
        }
        val moverIsGote = ply % 2 == 1
        val senteCp = if (moverIsGote) -moverCp else moverCp
        val userCp = if (userIsGote) -senteCp else senteCp
        EvalGraphPoint(ply = ply, clampedCp = userCp)
    }

/** 幅が0以下なら0を返す。 */
fun plyFromX(x: Float, widthPx: Int, effectiveMaxPly: Int): Int {
    if (widthPx <= 0) return 0
    val ratio = (x / widthPx).coerceIn(0f, 1f)
    return (ratio * effectiveMaxPly).roundToInt().coerceIn(0, effectiveMaxPly)
}

/** loss色は悪手マーカーだけに使い、形勢領域は着色しない。 */
@Composable
fun EvalGraphCard(
    points: List<EvalGraphPoint>,
    maxPly: Int,
    blunderPlies: Set<Int>,
    currentPly: Int? = null,
    modifier: Modifier = Modifier,
    onPlyTapped: (Int) -> Unit = {},
    onPlyDragged: (Int) -> Unit = {},
    analyzingThroughPly: Int? = null,
    interactive: Boolean = true,
    enabled: Boolean = true,
) {
    // 解析中と無効状態は、点がなくてもカードの領域を保つ。
    if (points.isEmpty() && analyzingThroughPly == null && enabled) return
    val shogiColors = MaterialTheme.shogiColors
    val lineColor = MaterialTheme.colorScheme.onSurface
    val zeroLineColor = shogiColors.line
    val markerColor = shogiColors.loss
    val markerHaloColor = MaterialTheme.colorScheme.surface
    val currentPlyLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    val hatchColor = shogiColors.ink3
    val frontierColor = shogiColors.highlight
    val effectiveMaxPly = maxOf(maxPly, points.maxOfOrNull { it.ply } ?: 0, 1)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.38f)
            .testTag("eval_graph_card")
            .semantics { if (!enabled) disabled() },
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
                    .testTag("eval_graph_canvas")
                    // 排他的なジェスチャー検出器なのでタップとドラッグを分ける。
                    .then(
                        if (interactive && enabled) {
                            Modifier
                                .pointerInput(effectiveMaxPly) {
                                    detectTapGestures { offset: Offset ->
                                        onPlyTapped(plyFromX(offset.x, size.width, effectiveMaxPly))
                                    }
                                }
                                .pointerInput(effectiveMaxPly) {
                                    // plyが変わらないポインタ更新では再描画しない。
                                    var lastReportedPly: Int? = null
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            val ply = plyFromX(offset.x, size.width, effectiveMaxPly)
                                            lastReportedPly = ply
                                            onPlyDragged(ply)
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val ply = plyFromX(change.position.x, size.width, effectiveMaxPly)
                                            if (ply != lastReportedPly) {
                                                lastReportedPly = ply
                                                onPlyDragged(ply)
                                            }
                                        },
                                    )
                                }
                        } else {
                            Modifier
                        },
                    ),
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

                // 未反映区間のハッチング（斜線）。反映済み区間の実線と対比させて
                // 「まだ解析結果が無い」ことを一目で示す。
                if (analyzingThroughPly != null && analyzingThroughPly < effectiveMaxPly) {
                    val hatchStartX = xOf(analyzingThroughPly.coerceAtLeast(0))
                    clipRect(left = hatchStartX, top = 0f, right = w, bottom = h) {
                        val step = 10.dp.toPx()
                        var x = hatchStartX - h
                        while (x < w) {
                            drawLine(
                                color = hatchColor,
                                start = Offset(x, h),
                                end = Offset(x + h, 0f),
                                strokeWidth = 1.dp.toPx(),
                            )
                            x += step
                        }
                    }
                }

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

                // ゼロ基準線と重なっても見えるよう、面色の縁取りを置く。
                val byPly = points.associateBy { it.ply }
                for (ply in blunderPlies) {
                    val p = byPly[ply] ?: continue
                    val center = Offset(xOf(p.ply), yOf(p.clampedCp))
                    drawCircle(color = markerHaloColor, radius = 6.dp.toPx(), center = center)
                    drawCircle(color = markerColor, radius = 4.dp.toPx(), center = center)
                }

                // 反映先端（watermark直前のply）の卵黄ドット——「いま注目」の位置を示す。
                if (analyzingThroughPly != null) {
                    byPly[analyzingThroughPly - 1]?.let { p ->
                        val center = Offset(xOf(p.ply), yOf(p.clampedCp))
                        drawCircle(color = markerHaloColor, radius = 5.dp.toPx(), center = center)
                        drawCircle(color = frontierColor, radius = 3.5.dp.toPx(), center = center)
                    }
                }

                // 現在手ライン（ビューアのナビ行と同期。中立色・縦線でゼロ基準線と区別する）。
                if (currentPly != null) {
                    val x = xOf(currentPly.coerceIn(0, effectiveMaxPly))
                    drawLine(
                        color = currentPlyLineColor,
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }
        }
    }
}
