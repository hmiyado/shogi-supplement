package dev.miyado.shogisupplement.ui.drill

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.common.NavIcons
import dev.miyado.shogisupplement.ui.common.PvExtState
import dev.miyado.shogisupplement.ui.common.SfenPosition
import dev.miyado.shogisupplement.ui.common.ShogiBoardView
import dev.miyado.shogisupplement.ui.theme.IbmPlexMonoFamily
import dev.miyado.shogisupplement.ui.theme.shogiColors

/**
 * 名前付き指し手ライン（タブ + 手列）。
 *
 * @param name  タブラベル
 * @param moves USI 手列（スペース区切りではなく List<String>）
 */
data class KifuLine(val name: String, val moves: List<String>)

/**
 * 棋譜ビューアの共通コンポーネント。
 * 持駒行 + 盤 + タブ + ナビ手送りを表示する。
 * 状態（アクティブタブ・手数）はホイストして呼び出し元が管理する。
 *
 * @param startSfen       開始 SFEN（null = 平手）
 * @param lines           名前付きライン（タブ名 + 手列）。1 本のとき タブ行は非表示。
 * @param activeLineIdx   現在アクティブなタブインデックス
 * @param plyIndex        現在の手数（0 = 開始局面）
 * @param flip            後手視点で反転表示するか
 * @param boardMaxHeight  盤の最大高さ制約
 * @param onLineSelected  タブ選択コールバック（新インデックスを渡す）
 * @param onNavigate      ナビゲーション コールバック（新手数を渡す）
 * @param lineEnabled     各タブの有効/無効（省略時はすべて有効）
 * @param currentMoveLabel ナビ行ラベル（null = 簡易ラベルを内部計算）
 * @param currentMoveLabelIsBlunder ラベルを朱色で表示するか
 * @param onMoveListRequested 棋譜リスト Sheet 開くコールバック（null = ラベルをタップ不可）
 * @param evalSuffixText ナビラベル末尾に連結する形勢サフィックス（例:「（−350）」。null = 非表示）。
 *   ドリル結果画面向け。別行スロットではなくラベルに直接連結し、No-jitter（行高不変）
 *   を保つ（DESIGN.md Layout節）。Mono フォント・符号色で他の形勢表示と同じ規約にする。
 * @param evalSuffixSign evalSuffixText の優劣（正 = 優勢[紺青]、負 = 劣勢[朱]、0 = 中立）。
 * @param extendableLineIdx 読み筋のオンデマンド延長に対応するライン（呼び出し側が指定。
 *   ドリル結果画面では「最善」タブのみ）のインデックス。null = 延長非対応（既定。
 *   既存の呼び出し側の挙動を変えない）。
 * @param extendState 延長の状態（Idle=延長可能・Loading=解析中・Error=直近失敗）。
 *   [extendableLineIdx] 指定時のみ意味を持つ。
 * @param onExtendRequested 延長トリガー（[extendableLineIdx] のラインでライン末尾に到達した
 *   状態で▶+タップ時、ライン末尾局面の SFEN を渡す）。null = 延長UIを出さない
 *   （ReportScreen 本譜ビューア等・既定の呼び出しでは常に null のまま）。
 */
@Composable
fun KifuLineViewer(
    startSfen: String?,
    lines: List<KifuLine>,
    activeLineIdx: Int,
    plyIndex: Int,
    flip: Boolean,
    boardMaxHeight: Dp,
    onLineSelected: (Int) -> Unit,
    onNavigate: (Int) -> Unit,
    modifier: Modifier = Modifier,
    lineEnabled: List<Boolean> = List(lines.size) { true },
    currentMoveLabel: String? = null,
    currentMoveLabelIsBlunder: Boolean = false,
    onMoveListRequested: (() -> Unit)? = null,
    evalSuffixText: String? = null,
    evalSuffixSign: Int = 0,
    extendableLineIdx: Int? = null,
    extendState: PvExtState = PvExtState.Idle,
    onExtendRequested: ((sfenAtLineEnd: String) -> Unit)? = null,
) {
    val activeLine = lines.getOrNull(activeLineIdx)
    val movesInLine = activeLine?.moves ?: emptyList()
    val maxPly = movesInLine.size
    val clampedPly = plyIndex.coerceIn(0, maxPly)

    val currentSfen = remember(startSfen, activeLineIdx, clampedPly, lines) {
        computeSfenAtStepKifuViewer(startSfen, movesInLine, clampedPly)
    }

    val lastMoveDest = remember(activeLineIdx, clampedPly, lines) {
        if (clampedPly <= 0) null
        else movesInLine.getOrNull(clampedPly - 1)?.let { usiStr ->
            runCatching {
                val move = ShogiMove.fromUsi(usiStr)
                move.to.file to move.to.rank
            }.getOrNull()
        }
    }

    val labelText = currentMoveLabel ?: when {
        clampedPly == 0 -> AppStrings.VIEWER_START_POSITION
        else -> AppStrings.viewerPlyLabel(clampedPly)
    }
    val shogiColors = MaterialTheme.shogiColors

    // ▶延長の可視化: extendableLineIdx のラインでライン末尾に到達しているとき、
    // ▶ボタンを「▶+」（primary色）にして延長トリガーであることを示す（ReportScreen の
    // 「最善の変化」タブと同じ規約）。Loading中も「▶+」のまま無効化。エラー時はナビラベルに
    // 「（—）」を出し、「▶+」は有効なまま（再試行可）。extendableLineIdx が非該当ライン・
    // onExtendRequested 未指定（既定）のときは常に false になり、既存の呼び出し側の挙動は
    // 完全に不変。
    val showExtendIndicator = extendableLineIdx != null &&
        extendableLineIdx == activeLineIdx &&
        onExtendRequested != null &&
        clampedPly >= maxPly
    val pvLoading = extendState is PvExtState.Loading
    val canTriggerExtend = showExtendIndicator && !pvLoading
    val effectiveSuffixText = if (showExtendIndicator && extendState is PvExtState.Error) {
        AppStrings.evalSuffix(AppStrings.EVAL_UNAVAILABLE)
    } else {
        evalSuffixText
    }
    val effectiveSuffixSign = if (showExtendIndicator && extendState is PvExtState.Error) 0 else evalSuffixSign

    Column(modifier = modifier) {
        // ── 盤 ─────────────────────────────────────────────────────────────
        // :ui の ShogiBoardView は onSquareTapped のみを公開しており、左右分割タップの
        // APIはない。KifuLineViewer は検討UXを持たない純閲覧ビューアなので、
        // タップされたマスの列位置（flip考慮）で左右半分を近似し、手送りナビに
        // 変換する（MainActivity.kt の「空マスタップ→ナビ」と同じ列しきい値パターン）。
        ShogiBoardView(
            sfen = currentSfen,
            flip = flip,
            lastMoveDest = lastMoveDest,
            onSquareTapped = { sq ->
                val files = if (flip) (1..9).toList() else (9 downTo 1).toList()
                val visualColIndex = files.indexOf(sq.file)
                if (visualColIndex <= 4) {
                    if (clampedPly > 0) onNavigate(clampedPly - 1)
                } else {
                    if (clampedPly < maxPly) onNavigate(clampedPly + 1)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = boardMaxHeight),
        )

        // ── タブ行（2本以上のとき表示）───────────────────────────────────
        if (lines.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                lines.forEachIndexed { idx, line ->
                    val isActive = activeLineIdx == idx
                    val enabled = lineEnabled.getOrElse(idx) { true }
                    val containerColor = if (isActive) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                    val contentColor = if (isActive) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                    OutlinedButton(
                        onClick = { onLineSelected(idx) },
                        enabled = enabled,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = containerColor,
                            contentColor = contentColor,
                        ),
                    ) {
                        Text(line.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }

        // ── ナビ行 ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { onNavigate(0) },
                enabled = clampedPly > 0,
                modifier = Modifier.height(36.dp).widthIn(min = 36.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) { Icon(NavIcons.FirstPage, contentDescription = "最初へ") }
            TextButton(
                onClick = { onNavigate(clampedPly - 1) },
                enabled = clampedPly > 0,
                modifier = Modifier.height(36.dp).widthIn(min = 36.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "1手戻る") }
            // 形勢サフィックス（例:「（−350）」）をラベル末尾に連結する（No-jitter・
            // DESIGN.md Layout節。別行のスロットは追加せず既存ラベルに統合する）。
            // 数値部は他の形勢表示（PositionEvalDisplay 系）と同じく Mono フォント・符号色。
            val labelWithEvalSuffix = if (effectiveSuffixText != null) {
                buildAnnotatedString {
                    append(labelText)
                    append(" ")
                    withStyle(
                        SpanStyle(
                            fontFamily = IbmPlexMonoFamily,
                            color = when {
                                effectiveSuffixSign > 0 -> MaterialTheme.colorScheme.primary
                                effectiveSuffixSign < 0 -> shogiColors.loss
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                        ),
                    ) {
                        append(effectiveSuffixText)
                    }
                }
            } else {
                AnnotatedString(labelText)
            }
            Text(
                text = labelWithEvalSuffix,
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (onMoveListRequested != null) {
                            Modifier.clickable { onMoveListRequested() }
                        } else {
                            Modifier
                        },
                    ),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = if (currentMoveLabelIsBlunder) shogiColors.loss
                        else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                // overflow=Clip では、長い手表記のとき中央寄せの結果、末尾の形勢サフィックス
                // （例:「（−350）」）が両端ごと見切れて消える。MiddleEllipsisで
                // 両端（手数プレフィックス側と形勢サフィックス側）を守る。
                overflow = TextOverflow.MiddleEllipsis,
            )
            TextButton(
                onClick = {
                    if (clampedPly < maxPly) {
                        onNavigate(clampedPly + 1)
                    } else if (canTriggerExtend) {
                        onExtendRequested?.invoke(currentSfen)
                    }
                },
                enabled = clampedPly < maxPly || canTriggerExtend,
                modifier = Modifier.height(36.dp).widthIn(min = 36.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) {
                if (showExtendIndicator) {
                    val extendColor = if (canTriggerExtend) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "1手進む",
                            tint = extendColor,
                        )
                        Text("+", color = extendColor)
                    }
                } else {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "1手進む")
                }
            }
            TextButton(
                onClick = { onNavigate(maxPly) },
                enabled = clampedPly < maxPly,
                modifier = Modifier.height(36.dp).widthIn(min = 36.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) { Icon(NavIcons.LastPage, contentDescription = "最後へ") }
        }
    }
}

/** 開始 SFEN から steps 手後の SFEN を計算する（KifuLineViewer 内部用）。 */
internal fun computeSfenAtStepKifuViewer(startSfen: String?, moves: List<String>, steps: Int): String {
    val board = if (startSfen != null) {
        runCatching { ShogiBoard.fromSfen(startSfen) }.getOrElse { ShogiBoard() }
    } else {
        ShogiBoard()
    }
    val limit = steps.coerceAtMost(moves.size)
    for (i in 0 until limit) {
        runCatching { board.push(ShogiMove.fromUsi(moves[i])) }.onFailure { break }
    }
    return board.toSfen()
}
