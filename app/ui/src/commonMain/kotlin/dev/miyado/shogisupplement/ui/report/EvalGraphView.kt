package dev.miyado.shogisupplement.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
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

/** 1手ごとの消費時間。nullはKIFに時間が記録されていない手を表す（0秒とは別）。 */
data class MoveTimeGraphPoint(val ply: Int, val seconds: Int?, val side: ClockSide? = null)

enum class ClockSide { SENTE, GOTE }

data class RemainingTimeGraphPoint(val ply: Int, val seconds: Int?, val side: ClockSide)

data class TimeGraphData(
    val remainingPoints: List<RemainingTimeGraphPoint>,
    val byoyomiPoints: List<MoveTimeGraphPoint>,
    val mainTimeMaxSeconds: Int,
    val byoyomiMaxSeconds: Int?,
)

private sealed interface TimeControlGraphSpec {
    val mainTimeSeconds: Int

    data class SuddenDeath(override val mainTimeSeconds: Int) : TimeControlGraphSpec
    data class Fischer(override val mainTimeSeconds: Int, val incrementSeconds: Int) : TimeControlGraphSpec
    data class Byoyomi(override val mainTimeSeconds: Int, val byoyomiSeconds: Int) : TimeControlGraphSpec
}

fun buildMoveTimeGraphPoints(moveTimesSeconds: List<Int?>, maxPly: Int): List<MoveTimeGraphPoint> =
    (1..maxPly.coerceAtLeast(0)).map { ply ->
        MoveTimeGraphPoint(ply, moveTimesSeconds.getOrNull(ply - 1))
    }

fun formatMoveTime(seconds: Int?): String = seconds?.let { "${it / 60}:${(it % 60).toString().padStart(2, '0')}" } ?: AppStrings.EVAL_GRAPH_TIME_UNKNOWN

private val MINUTES_ONLY = Regex("""^(\d+)分$""")
private val SUDDEN_DEATH = Regex("""^(\d+)分切れ負け$""")
private val MINUTES_PLUS_SECONDS = Regex("""^(\d+)分\+(\d+)秒(?:追加)?$""")
private val BYOYOMI_SECONDS = Regex("""^(\d+)秒$""")

private fun parseSeconds(raw: String?, pattern: Regex): Int? =
    raw?.trim()?.let { pattern.matchEntire(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }

private fun parseTimeControlGraphSpec(
    sourcePlace: String?,
    timeControlRaw: String?,
    timeControlByoyomiRaw: String?,
): TimeControlGraphSpec? {
    val main = timeControlRaw?.trim() ?: return null
    val byoyomiSeconds = parseSeconds(timeControlByoyomiRaw, BYOYOMI_SECONDS)
    val suddenDeathMinutes = parseSeconds(main, SUDDEN_DEATH)
    if (suddenDeathMinutes != null) return TimeControlGraphSpec.SuddenDeath(suddenDeathMinutes * 60)

    val minutes = parseSeconds(main, MINUTES_ONLY)
    if (minutes != null && byoyomiSeconds != null) {
        return TimeControlGraphSpec.Byoyomi(minutes * 60, byoyomiSeconds)
    }

    val plus = MINUTES_PLUS_SECONDS.matchEntire(main)
    if (plus != null) {
        val mainSeconds = plus.groupValues[1].toIntOrNull()?.times(60) ?: return null
        val extraSeconds = plus.groupValues[2].toIntOrNull() ?: return null
        return when {
            sourcePlace == "kiou" && main == "5分+5秒追加" ->
                TimeControlGraphSpec.Fischer(mainSeconds, extraSeconds)
            sourcePlace == "lishogi" ->
                TimeControlGraphSpec.Byoyomi(mainSeconds, extraSeconds)
            // 棋桜の10分+30秒はカジュアル/真剣の区別がKIFだけではつかない。
            // 秒読みと断定せず、主時間の推移だけを表示する。
            sourcePlace == "kiou" && main == "10分+30秒" ->
                TimeControlGraphSpec.SuddenDeath(mainSeconds)
            else -> null
        }
    }

    return minutes?.let { TimeControlGraphSpec.SuddenDeath(it * 60) }
}

fun buildTimeGraphData(
    sourcePlace: String?,
    timeControlRaw: String?,
    timeControlByoyomiRaw: String?,
    moveTimesSeconds: List<Int?>,
    userSide: String? = null,
): TimeGraphData? {
    val spec = parseTimeControlGraphSpec(sourcePlace, timeControlRaw, timeControlByoyomiRaw) ?: return null
    if (moveTimesSeconds.isEmpty()) return null

    val mainRemaining = intArrayOf(spec.mainTimeSeconds, spec.mainTimeSeconds)
    val inByoyomi = booleanArrayOf(
        spec is TimeControlGraphSpec.Byoyomi && spec.mainTimeSeconds == 0,
        spec is TimeControlGraphSpec.Byoyomi && spec.mainTimeSeconds == 0,
    )
    val remaining = mutableListOf<RemainingTimeGraphPoint>()
    val byoyomi = mutableListOf<MoveTimeGraphPoint>()

    moveTimesSeconds.forEachIndexed { index, consumed ->
        val ply = index + 1
        val side = if (index % 2 == 0) ClockSide.SENTE else ClockSide.GOTE
        val sideIndex = if (side == ClockSide.SENTE) 0 else 1
        when (spec) {
            is TimeControlGraphSpec.Byoyomi -> {
                if (inByoyomi[sideIndex]) {
                    byoyomi += MoveTimeGraphPoint(ply, consumed, side)
                } else if (consumed == null) {
                    remaining += RemainingTimeGraphPoint(ply, null, side)
                } else {
                    val before = mainRemaining[sideIndex]
                    val after = (before - consumed).coerceAtLeast(0)
                    mainRemaining[sideIndex] = after
                    remaining += RemainingTimeGraphPoint(ply, after, side)
                    if (after == 0) {
                        inByoyomi[sideIndex] = true
                        // 主時間を使い切った手だけ、秒読みへはみ出した分を表示する。
                        val byoyomiUsed = (consumed - before).coerceAtLeast(0)
                        if (byoyomiUsed > 0) byoyomi += MoveTimeGraphPoint(ply, byoyomiUsed, side)
                    }
                }
            }
            is TimeControlGraphSpec.Fischer -> {
                if (consumed == null) {
                    remaining += RemainingTimeGraphPoint(ply, null, side)
                } else {
                    mainRemaining[sideIndex] = (mainRemaining[sideIndex] - consumed + spec.incrementSeconds).coerceAtLeast(0)
                    remaining += RemainingTimeGraphPoint(ply, mainRemaining[sideIndex], side)
                }
            }
            is TimeControlGraphSpec.SuddenDeath -> {
                if (consumed == null) {
                    remaining += RemainingTimeGraphPoint(ply, null, side)
                } else {
                    mainRemaining[sideIndex] = (mainRemaining[sideIndex] - consumed).coerceAtLeast(0)
                    remaining += RemainingTimeGraphPoint(ply, mainRemaining[sideIndex], side)
                }
            }
        }
    }

    val selectedSide = when (userSide) {
        "sente" -> ClockSide.SENTE
        "gote" -> ClockSide.GOTE
        else -> null
    }
    val selectedRemaining = selectedSide?.let { side -> remaining.filter { it.side == side } } ?: remaining
    val selectedByoyomi = selectedSide?.let { side -> byoyomi.filter { it.side == side } } ?: byoyomi
    return TimeGraphData(
        remainingPoints = selectedRemaining,
        byoyomiPoints = selectedByoyomi,
        mainTimeMaxSeconds = maxOf(spec.mainTimeSeconds, selectedRemaining.mapNotNull { it.seconds }.maxOrNull() ?: 0, 1),
        byoyomiMaxSeconds = if (spec is TimeControlGraphSpec.Byoyomi) {
            maxOf(spec.byoyomiSeconds, selectedByoyomi.mapNotNull { it.seconds }.maxOrNull() ?: 0, 1)
        } else {
            null
        },
    )
}

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

/** 評価値・時間グラフで共有する、手数位置のタップ／ドラッグ入力。 */
private fun Modifier.graphGestures(
    effectiveMaxPly: Int,
    interactive: Boolean,
    enabled: Boolean,
    onPlyTapped: (Int) -> Unit,
    onPlyDragged: (Int) -> Unit,
): Modifier = then(
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
)

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
    timeGraphData: TimeGraphData? = null,
) {
    // 解析中と無効状態は、点がなくてもカードの領域を保つ。
    if (points.isEmpty() && timeGraphData == null && analyzingThroughPly == null && enabled) return
    val shogiColors = MaterialTheme.shogiColors
    val lineColor = MaterialTheme.colorScheme.onSurface
    val zeroLineColor = shogiColors.line
    val markerColor = shogiColors.loss
    val markerHaloColor = MaterialTheme.colorScheme.surface
    val currentPlyLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    val secondaryTimeColor = MaterialTheme.colorScheme.primary
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
                    .graphGestures(
                        effectiveMaxPly = effectiveMaxPly,
                        interactive = interactive,
                        enabled = enabled,
                        onPlyTapped = onPlyTapped,
                        onPlyDragged = onPlyDragged,
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
            if (timeGraphData != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        AppStrings.EVAL_GRAPH_REMAINING_TIME_LABEL,
                        style = MaterialTheme.typography.labelMedium,
                        color = shogiColors.ink2,
                    )
                    if (timeGraphData.byoyomiPoints.isNotEmpty()) {
                        Text(
                            AppStrings.EVAL_GRAPH_BYOYOMI_TIME_LABEL,
                            style = MaterialTheme.typography.labelMedium,
                            color = shogiColors.ink2,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        formatMoveTime(timeGraphData.mainTimeMaxSeconds),
                        style = MaterialTheme.typography.bodySmall,
                        color = shogiColors.ink3,
                    )
                    timeGraphData.byoyomiMaxSeconds?.let { maxSeconds ->
                        Text(
                            "${maxSeconds}秒",
                            style = MaterialTheme.typography.bodySmall,
                            color = shogiColors.ink3,
                        )
                    }
                }
                if (timeGraphData.remainingPoints.any { it.seconds != null } || timeGraphData.byoyomiPoints.isNotEmpty()) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(top = 4.dp)
                            .testTag("remaining_time_graph_canvas")
                            .graphGestures(
                                effectiveMaxPly = effectiveMaxPly,
                                interactive = interactive,
                                enabled = enabled,
                                onPlyTapped = onPlyTapped,
                                onPlyDragged = onPlyDragged,
                            ),
                    ) {
                        val w = size.width
                        val h = size.height
                        val baseline = h - 8.dp.toPx()
                        val mainMaxSeconds = timeGraphData.mainTimeMaxSeconds
                        val byoyomiMaxSeconds = timeGraphData.byoyomiMaxSeconds ?: 1
                        val byoyomiStartPly = timeGraphData.byoyomiPoints.minOfOrNull { it.ply }
                        fun xOf(ply: Int): Float = w * ply / effectiveMaxPly
                        fun remainingY(seconds: Int): Float = (h - 8.dp.toPx()) *
                            (1f - seconds.toFloat() / mainMaxSeconds)

                        // 秒読みへ移行した手数から同じレーンの背景を切り替える。
                        byoyomiStartPly?.let { startPly ->
                            val startX = xOf(startPly)
                            drawRect(
                                color = shogiColors.highlightSoft,
                                topLeft = Offset(startX, 0f),
                                size = size.copy(width = w - startX),
                            )
                        }
                        drawLine(zeroLineColor, Offset(0f, baseline), Offset(w, baseline), 1.dp.toPx())

                        listOf(ClockSide.SENTE, ClockSide.GOTE).forEach { side ->
                            val sidePoints = timeGraphData.remainingPoints.filter { it.side == side }
                            val color = if (side == ClockSide.SENTE) lineColor else secondaryTimeColor
                            sidePoints.zipWithNext().forEach { (p0, p1) ->
                                if (p0.seconds != null && p1.seconds != null && p1.ply - p0.ply == 2) {
                                    drawLine(color, Offset(xOf(p0.ply), remainingY(p0.seconds)), Offset(xOf(p1.ply), remainingY(p1.seconds)), 2.dp.toPx())
                                }
                            }
                            sidePoints.forEach { point ->
                                point.seconds?.let { seconds ->
                                    drawCircle(color, radius = 2.5.dp.toPx(), center = Offset(xOf(point.ply), remainingY(seconds)))
                                }
                            }
                        }

                        for (point in timeGraphData.byoyomiPoints) {
                            val x = xOf(point.ply)
                            val seconds = point.seconds
                            val color = if (point.side == ClockSide.SENTE) lineColor else secondaryTimeColor
                            if (seconds == null) {
                                drawLine(hatchColor, Offset(x - 3.dp.toPx(), baseline - 8.dp.toPx()), Offset(x + 3.dp.toPx(), baseline - 2.dp.toPx()), 1.dp.toPx())
                                drawLine(hatchColor, Offset(x - 3.dp.toPx(), baseline - 2.dp.toPx()), Offset(x + 3.dp.toPx(), baseline - 8.dp.toPx()), 1.dp.toPx())
                            } else if (seconds == 0) {
                                drawCircle(color, radius = 2.5.dp.toPx(), center = Offset(x, baseline))
                            } else {
                                val barHeight = (h - 16.dp.toPx()) * seconds / byoyomiMaxSeconds
                                drawLine(color, Offset(x, baseline), Offset(x, baseline - barHeight), 3.dp.toPx())
                            }
                            if (point.ply in blunderPlies) {
                                drawCircle(markerColor, radius = 3.dp.toPx(), center = Offset(x, baseline + 4.dp.toPx()))
                            }
                        }

                        currentPly?.let { ply ->
                            val x = xOf(ply.coerceIn(0, effectiveMaxPly))
                            drawLine(currentPlyLineColor, Offset(x, 0f), Offset(x, h), 1.dp.toPx())
                        }
                    }
                    Text(
                        if (timeGraphData.remainingPoints.map { it.side }.distinct().size == 1) {
                            AppStrings.EVAL_GRAPH_REMAINING_TIME_SELF_LEGEND
                        } else {
                            AppStrings.EVAL_GRAPH_REMAINING_TIME_LEGEND
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = shogiColors.ink3,
                    )
                    if (timeGraphData.byoyomiPoints.isNotEmpty()) {
                        Text(
                            AppStrings.EVAL_GRAPH_BYOYOMI_TIME_LEGEND,
                            style = MaterialTheme.typography.bodySmall,
                            color = shogiColors.ink3,
                        )
                    }
                } else {
                    Text(AppStrings.EVAL_GRAPH_TIME_NONE, style = MaterialTheme.typography.bodySmall, color = shogiColors.ink3)
                }
            }
        }
    }
}
