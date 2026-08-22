package dev.miyado.shogisupplement.ui.report

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.notation.JapaneseNotation
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.common.ShogiSecondaryButton
import dev.miyado.shogisupplement.ui.theme.IbmPlexMonoFamily
import dev.miyado.shogisupplement.ui.theme.LightInk
import dev.miyado.shogisupplement.ui.theme.ShipporiMinchoFamily
import dev.miyado.shogisupplement.ui.theme.TextStyleDataMove
import dev.miyado.shogisupplement.ui.theme.shogiColors

/**
 * 検討モードのパネル。評価スロットと手順チップ列を固定の外形に収める。
 * チップ列はFlowRowで折り返し、余剰分だけ内部スクロールする。
 * 終了操作はナビ行に集約し、評価スロットは状態に応じて排他表示する。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StudyPanel(
    studyState: StudyState,
    onChipTapped: (Int) -> Unit,
    onBranchChipTapped: (Int) -> Unit,
    onBranchPopupDismiss: () -> Unit,
    onBranchOptionSelected: (depth: Int, moveUsi: String) -> Unit,
    onAnalyze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shogiColors = MaterialTheme.shogiColors
    val notations = remember(studyState.baseSfen, studyState.displayLine) {
        buildMoveNotations(studyState.baseSfen, studyState.displayLine)
    }

    Card(
        modifier = modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            // ── 見出し行: 「検討中」＋分岐元行（「42手目 ▲３四飛（−320）から分岐」）。
            // タイトルは幅固定で省略しない、分岐元行が残り幅を使って maxLines=1 + ellipsis になる。
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = AppStrings.STUDY_PANEL_TITLE,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = ShipporiMinchoFamily,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    text = AppStrings.studyOriginLine(studyState.origin.label),
                    style = MaterialTheme.typography.bodySmall,
                    color = shogiColors.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).alignByBaseline(),
                )
            }

            Spacer(Modifier.height(8.dp))

            // 評価スロットは固定高さとし、手動再試行はError状態だけに限定する。
            // Preparingは自動回復待ちのためLoadingと同じ表示にする。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                when (val es = studyState.evalState) {
                    StudyEvalState.None -> Unit
                    StudyEvalState.Preparing -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                AppStrings.STUDY_EVAL_PREPARING,
                                style = MaterialTheme.typography.bodySmall,
                                color = shogiColors.ink2,
                            )
                        }
                    }
                    StudyEvalState.Error -> {
                        AnalyzeButton(onClick = onAnalyze)
                    }
                    StudyEvalState.Loading -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                AppStrings.STUDY_EVAL_ANALYZING,
                                style = MaterialTheme.typography.bodySmall,
                                color = shogiColors.ink2,
                            )
                        }
                    }
                    is StudyEvalState.Value -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                es.label.text,
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = IbmPlexMonoFamily),
                                color = when {
                                    es.label.sign > 0 -> MaterialTheme.colorScheme.primary
                                    es.label.sign < 0 -> shogiColors.loss
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                            if (es.bestMoveText != null) {
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    AppStrings.studyBestMoveLabel(es.bestMoveText),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = shogiColors.ink2,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = shogiColors.line)

            Spacer(Modifier.height(8.dp))

            // displayLine全体を淡色を含めて描画し、余剰分だけこの領域内で縦スクロールする。
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                studyState.displayLine.forEachIndexed { depth, moveUsi ->
                    val isCurrent = depth == studyState.moves.size - 1
                    val isFuture = depth >= studyState.moves.size
                    val hasBranch = studyState.branchFlags.getOrNull(depth) == true
                    val evalSuffix = (studyState.chipEvalStates.getOrNull(depth) as? StudyEvalState.Value)
                        ?.let { AppStrings.studyChipEvalSuffix(it.label.text) }
                    Box {
                        StudyMoveChip(
                            label = notations.getOrElse(depth) { moveUsi },
                            evalSuffix = evalSuffix,
                            isCurrent = isCurrent,
                            isFuture = isFuture,
                            hasBranch = hasBranch,
                            onClick = {
                                if (hasBranch) onBranchChipTapped(depth) else onChipTapped(depth + 1)
                            },
                        )
                        if (studyState.openBranchPopupDepth == depth) {
                            // ポップの兄弟変化も棋譜表記で出す（このdepthに至る直前の局面は
                            // 全兄弟で共通なので、盤面を1回だけ組み立てて使い回す）。
                            val popupBoard = remember(studyState.baseSfen, studyState.displayLine, depth) {
                                runCatching {
                                    ShogiBoard.fromSfen(sfenAfterMoves(studyState.baseSfen, studyState.displayLine, depth))
                                }.getOrNull()
                            }
                            DropdownMenu(
                                expanded = true,
                                onDismissRequest = onBranchPopupDismiss,
                            ) {
                                studyState.branchPopupOptions.forEach { option ->
                                    val optionNotation = popupBoard?.let { board ->
                                        runCatching { JapaneseNotation.format(option.moveUsi, board) }.getOrNull()
                                    } ?: option.moveUsi
                                    val optionEvalText = (option.evalState as? StudyEvalState.Value)
                                        ?.label?.text ?: AppStrings.STUDY_BRANCH_EVAL_UNKNOWN
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "$optionNotation  $optionEvalText" +
                                                    if (option.isCurrent) AppStrings.STUDY_BRANCH_CURRENT_SUFFIX else "",
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        },
                                        onClick = {
                                            if (!option.isCurrent) onBranchOptionSelected(depth, option.moveUsi)
                                            onBranchPopupDismiss()
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 検討パネルの評価スロット手動リトライボタン（Error状態のみに出す）。
 */
@Composable
private fun AnalyzeButton(onClick: () -> Unit) {
    ShogiSecondaryButton(onClick = onClick) {
        Text(AppStrings.STUDY_ANALYZE_LABEL)
    }
}

/**
 * 手順チップ。分岐にはMaterialのKeyboardArrowDownを使い、記号の字体差を避ける。
 * 現在手はhighlight背景とLightInk文字で表示し、ダークテーマでもコントラストを保つ。
 */
@Composable
private fun StudyMoveChip(
    label: String,
    evalSuffix: String?,
    isCurrent: Boolean,
    isFuture: Boolean,
    hasBranch: Boolean,
    onClick: () -> Unit,
) {
    val shogiColors = MaterialTheme.shogiColors
    val textColor = when {
        isCurrent -> LightInk
        isFuture -> shogiColors.ink3
        else -> Color.Unspecified
    }
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (isCurrent) shogiColors.highlight else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(4.dp),
        border = if (hasBranch) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (evalSuffix != null) "$label$evalSuffix" else label,
                style = TextStyleDataMove,
                color = textColor,
                maxLines = 1,
            )
            if (hasBranch) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = AppStrings.STUDY_BRANCH_ICON_DESC,
                    tint = if (isCurrent) LightInk else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp).padding(start = 2.dp),
                )
            }
        }
    }
}

/**
 * baseSfenから各手の日本語表記を返す。
 * Why not 盤を使い回さない: 途中の失敗を後続の手へ波及させないため、手ごとに再構築する。
 */
private fun buildMoveNotations(baseSfen: String, moves: List<String>): List<String> =
    moves.indices.map { depth ->
        val prevSfen = sfenAfterMoves(baseSfen, moves, depth)
        runCatching { JapaneseNotation.format(moves[depth], ShogiBoard.fromSfen(prevSfen)) }
            .getOrElse { moves[depth] }
    }

/** baseSfen から moves を steps 手だけ進めた局面の SFEN を返す（1手でも失敗したらそこで打ち切る）。 */
private fun sfenAfterMoves(baseSfen: String, moves: List<String>, steps: Int): String {
    val board = runCatching { ShogiBoard.fromSfen(baseSfen) }.getOrElse { ShogiBoard() }
    for (i in 0 until steps) {
        runCatching { board.push(ShogiMove.fromUsi(moves[i])) }.onFailure { return board.toSfen() }
    }
    return board.toSfen()
}
