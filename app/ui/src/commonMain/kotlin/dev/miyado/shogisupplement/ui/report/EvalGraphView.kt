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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.db.PositionEvalRow
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.theme.shogiColors
import kotlin.math.roundToInt

/**
 * 評価値グラフ用に正規化した1点。自分視点・クランプ済みの値のみ持つ
 * （表示側は符号や勝率換算を一切行わない。正 = 自分優勢で統一）。
 */
data class EvalGraphPoint(val ply: Int, val clampedCp: Int)

/** グラフの縦軸クランプ幅（cp）。悪手判定閾値の4倍程度を確保し、
 * 通常の優劣の推移が視認できる範囲に収める。詰みはこの値に張り付ける。 */
const val EVAL_GRAPH_CLAMP_CP = 2000

/**
 * [PositionEvalRow] を評価値グラフ用の点列に変換する（ply昇順ソート・クランプ適用・自分視点への正規化）。
 * mateIn は符号のみ使い、クランプ上限/下限に張り付ける（実際の詰み手数は表示に使わない。
 * グラフは形勢の推移の概観が目的で、詰み手数の精緻な表現は他の表示（ナビ行等）が担うため）。
 *
 * @param userIsGote ユーザーが後手なら true（符号反転）。position_eval は先手視点保存のため、
 *   この画面の他の表示と同じ規約で自分視点に揃える
 *   （上=自分有利で統一。実機確認で先手視点固定は違和感があるとの指摘）。
 */
fun buildEvalGraphPoints(positionEvals: List<PositionEvalRow>, userIsGote: Boolean = false): List<EvalGraphPoint> =
    positionEvals
        .sortedBy { it.ply }
        .mapNotNull { row ->
            val mateIn = row.mateIn
            val scoreCp = row.scoreCp
            val senteCp = when {
                mateIn != null -> if (mateIn > 0) EVAL_GRAPH_CLAMP_CP else -EVAL_GRAPH_CLAMP_CP
                scoreCp != null -> scoreCp.coerceIn(-EVAL_GRAPH_CLAMP_CP, EVAL_GRAPH_CLAMP_CP)
                else -> return@mapNotNull null
            }
            val cp = if (userIsGote) -senteCp else senteCp
            EvalGraphPoint(ply = row.ply, clampedCp = cp)
        }

/**
 * グラフ上のx座標（Canvas内のローカル座標・px）を ply に変換する。
 * タップ・ドラッグ双方のポインタ処理で共有する。
 * Composeに依存しない純粋関数のためユニットテスト可能。
 *
 * @param x タップ/ドラッグ位置のx座標（px）
 * @param widthPx Canvasの幅（px）。0以下なら常に0を返す
 * @param effectiveMaxPly 横軸の最大ply（呼び出し側のグラフ描画で使っている値と同じものを渡すこと）
 */
fun plyFromX(x: Float, widthPx: Int, effectiveMaxPly: Int): Int {
    if (widthPx <= 0) return 0
    val ratio = (x / widthPx).coerceIn(0f, 1f)
    return (ratio * effectiveMaxPly).roundToInt().coerceIn(0, effectiveMaxPly)
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
 * @param currentPly ビューア（ナビ行）が現在表示している ply。null なら現在手ラインを描かない
 *   （検討モード等、本譜の ply と対応が取れない状態を表す）
 * @param onPlyTapped タップした位置に最も近い ply。呼び出し側でナビゲーション（該当手へジャンプ）・
 *   悪手マーカーなら一覧への切替に使う
 * @param onPlyDragged 横方向ドラッグ中（指を離すまでの全サンプル）に呼ばれる、現在の指位置に
 *   対応する ply（スクラバー操作）。呼び出し側はタップと違い一覧への切替を発火させないこと
 *   （このカード自身はタップ/ドラッグの種別を渡すだけで、悪手一覧への切替可否の判断は
 *   呼び出し側の責務）。
 */
@Composable
fun EvalGraphCard(
    points: List<EvalGraphPoint>,
    maxPly: Int,
    blunderPlies: Set<Int>,
    currentPly: Int? = null,
    modifier: Modifier = Modifier,
    onPlyTapped: (Int) -> Unit = {},
    onPlyDragged: (Int) -> Unit = {},
) {
    if (points.isEmpty()) return
    val shogiColors = MaterialTheme.shogiColors
    val lineColor = MaterialTheme.colorScheme.onSurface
    val zeroLineColor = shogiColors.line
    val markerColor = shogiColors.loss
    val markerHaloColor = MaterialTheme.colorScheme.surface
    val currentPlyLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
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
                    .testTag("eval_graph_canvas")
                    // タップとドラッグを2つの pointerInput に分けて検出する（Compose の定石。
                    // detectDragGestures はタッチスロップを越えて初めて onDragStart を発火し
                    // その後の change を consume するため、スロップ未満で指を離す＝タップは
                    // こちらに一切反応せず、隣の detectTapGestures 側にそのまま拾われる。
                    // Why not 1つの pointerInput にまとめる: detectDragGestures と
                    // detectTapGestures は排他のジェスチャー検出器で片方しか awaitPointerEventScope
                    // を占有できないため、タップ専用/ドラッグ専用で分離するのが標準的な組み方）。
                    .pointerInput(effectiveMaxPly) {
                        detectTapGestures { offset: Offset ->
                            onPlyTapped(plyFromX(offset.x, size.width, effectiveMaxPly))
                        }
                    }
                    .pointerInput(effectiveMaxPly) {
                        // 連続更新の間引き: 生のポインタサンプル毎ではなく、算出した ply が
                        // 前回から実際に変わったときだけ onPlyDragged を呼ぶ（ply の解像度は
                        // 通常ワイド全体で高々数百程度なので、指の微小な揺れでの無駄な
                        // 再コンポーズ・SFEN再計算を避けられる。数百手規模の対局でも
                        // 1回あたりの計算量は軽いため、これに加えた時間ベースの間引き
                        // （フレーム制限等）までは導入していない）。
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

                // 悪手マーカー（朱）。実機確認で視認しづらいとの指摘のため、線より
                // ひとまわり大きい半径にし、周囲に面色のハロー（縁取り）を敷いて
                // 線・ゼロ基準線との重なりでも輪郭が埋もれないようにする
                // （ハロー自体は surface 色＝無彩色なので朱=損失専用ルールに抵触しない）。
                val byPly = points.associateBy { it.ply }
                for (ply in blunderPlies) {
                    val p = byPly[ply] ?: continue
                    val center = Offset(xOf(p.ply), yOf(p.clampedCp))
                    drawCircle(color = markerHaloColor, radius = 6.dp.toPx(), center = center)
                    drawCircle(color = markerColor, radius = 4.dp.toPx(), center = center)
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
