package dev.miyado.shogisupplement.ui.report


import dev.miyado.shogisupplement.ui.text.text

import org.jetbrains.compose.resources.vectorResource

import dev.miyado.shogisupplement.ui.text.UiStrings

import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import dev.miyado.shogisupplement.ui.generated.resources.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.notation.JapaneseNotation
import dev.miyado.shogisupplement.ui.common.ShogiSecondaryButton
import dev.miyado.shogisupplement.ui.theme.IbmPlexMonoFamily
import dev.miyado.shogisupplement.ui.theme.LightInk
import dev.miyado.shogisupplement.ui.theme.TextStyleDataMove
import dev.miyado.shogisupplement.ui.theme.shogiColors

/**
 * 検討中のナビ行。表示中の本譜・分岐に沿って一手ずつ前後へ移動する。
 */
@Composable
internal fun StudyNavRow(
    studyState: StudyState,
    studySenteToMove: Boolean,
    onStudyStepBack: () -> Unit,
    onStudyStepForward: () -> Unit,
    onStudyExit: (() -> Unit)?,
) {
    val labelMovePrevious = stringResource(Res.string.move_previous)
    val labelMoveNext = stringResource(Res.string.move_next)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.shogiColors.primarySoft)
            .padding(horizontal = 8.dp)
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onStudyStepBack,
            enabled = studyState.moves.isNotEmpty(),
            modifier = Modifier.height(36.dp).widthIn(min = 36.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
        ) { Icon(vectorResource(Res.drawable.ic_auto_mirrored_filled_keyboard_arrow_left), contentDescription = labelMovePrevious) }
        Text(
            text = when {
                studyState.showTurnHint -> UiStrings.studyTurnHint(studySenteToMove)
                studyState.moves.isEmpty() -> stringResource(Res.string.study_start_position)
                else -> UiStrings.studyPlyLabel(studyState.originAbsolutePly + studyState.moves.size)
            },
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
        )
        TextButton(
            onClick = onStudyStepForward,
            enabled = studyState.moves.size < studyState.displayLine.size,
            modifier = Modifier.height(36.dp).widthIn(min = 36.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
        ) { Icon(vectorResource(Res.drawable.ic_auto_mirrored_filled_keyboard_arrow_right), contentDescription = labelMoveNext) }
        if (onStudyExit != null) {
            TextButton(
                onClick = onStudyExit,
                modifier = Modifier.height(36.dp),
            ) { Text(stringResource(Res.string.study_end)) }
        }
    }
}

/**
 * 評価の取得前後もパネル外形・行高・候補幅を変えない。
 * 終了操作はナビ行に集約し、評価スロットは状態に応じて排他表示する。
 */
@Composable
internal fun StudyPanel(
    studyState: StudyState,
    onChipTapped: (Int) -> Unit,
    onBranchChipTapped: (Int) -> Unit,
    onBranchPopupDismiss: () -> Unit,
    onBranchOptionSelected: (depth: Int, nodeId: Long) -> Unit,
    onAnalyze: () -> Unit,
    onCandidateTapped: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSave: (((Boolean) -> Unit) -> Unit)? = null,
    onDeleteBranch: ((String, List<String>, Long?) -> Boolean)? = null,
) {
    val shogiColors = MaterialTheme.shogiColors
    var actionsExpanded by remember(studyState.baseSfen, studyState.moves, studyState.nodeId) { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    var retryAttempt by remember { mutableStateOf(0) }
    var deletingBranch by remember(studyState.baseSfen, studyState.moves, studyState.nodeId) { mutableStateOf(false) }
    LaunchedEffect(studyState.saveFailed, retryAttempt) {
        if (studyState.saveFailed) {
            val result = snackbar.showSnackbar(
                getString(Res.string.study_save_failed),
                actionLabel = getString(Res.string.game_restore_retry_button),
                duration = androidx.compose.material3.SnackbarDuration.Indefinite,
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                onSave?.invoke { if (!it) retryAttempt++ }
            }
        }
    }
    if (deletingBranch && onDeleteBranch != null) {
        AlertDialog(
            onDismissRequest = { deletingBranch = false },
            title = { Text(stringResource(Res.string.study_delete_branch_title)) },
            text = { Text(stringResource(Res.string.study_delete_branch_body)) },
            confirmButton = {
                OutlinedButton(
                    onClick = { onDeleteBranch(studyState.baseSfen, studyState.moves, studyState.nodeId); deletingBranch = false },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(Res.string.study_delete_branch)) }
            },
            dismissButton = { TextButton(onClick = { deletingBranch = false }) { Text(stringResource(Res.string.cancel)) } },
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            // 評価スロットは固定高さとし、手動再試行はError状態だけに限定する。
            // Preparingは自動回復待ちのためLoadingと同じ表示にする。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(Modifier.fillMaxWidth().padding(end = if (onSave != null || onDeleteBranch != null) 56.dp else 0.dp)) {
                when (val es = studyState.evalState) {
                    StudyEvalState.None -> Unit
                    StudyEvalState.Preparing -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(Res.string.study_eval_preparing),
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
                                stringResource(Res.string.study_eval_analyzing),
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
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    UiStrings.studyEvalPerspective(studyState.flip),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = shogiColors.ink2,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    UiStrings.studyOriginLine(studyState.origin.label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = shogiColors.ink2,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                }
                if (onSave != null || onDeleteBranch != null) {
                    Box(Modifier.align(Alignment.CenterEnd)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { actionsExpanded = true }) {
                                Icon(vectorResource(Res.drawable.ic_filled_more_horiz), contentDescription = stringResource(Res.string.study_actions))
                            }
                        }
                        DropdownMenu(expanded = actionsExpanded, onDismissRequest = { actionsExpanded = false }) {
                            if (onDeleteBranch != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.study_delete_branch),
                                        color = if (studyState.canDeleteBranch) MaterialTheme.colorScheme.error else shogiColors.ink3) },
                                    enabled = studyState.canDeleteBranch,
                                    onClick = { actionsExpanded = false; deletingBranch = true },
                                )
                            }
                        }
                    }
                }
            }

            Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.CenterStart) {
                val candidates = (studyState.evalState as? StudyEvalState.Value)?.candidates.orEmpty()
                if (candidates.isNotEmpty()) StudyCandidateRow(candidates, onCandidateTapped)
            }

            HorizontalDivider(color = shogiColors.line)

            Spacer(Modifier.height(8.dp))

            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            ) {
                studyState.displayLine.forEachIndexed { depth, moveUsi ->
                    val options = studyState.moveOptions.getOrNull(depth).orEmpty().ifEmpty {
                        listOf(StudyBranchOption(moveUsi, studyState.chipEvalStates.getOrNull(depth)
                            ?: StudyEvalState.None, isCurrent = true))
                    }
                    key(studyState.baseSfen, studyState.displayLine.take(depth), options.map { it.nodeId }) {
                        val board = remember(studyState.baseSfen, studyState.displayLine.take(depth)) {
                            runCatching { ShogiBoard.fromSfen(sfenAfterMoves(studyState.baseSfen, studyState.displayLine, depth)) }.getOrNull()
                        }
                        StudyMoveRow(
                            ply = studyState.originAbsolutePly + depth + 1,
                            options = options,
                            notation = { option -> board?.let {
                                runCatching { JapaneseNotation.format(option.moveUsi, it) }.getOrNull()
                            } ?: option.moveUsi },
                            isCurrent = depth == studyState.moves.size - 1,
                            isFuture = depth >= studyState.moves.size,
                            scrollToRow = depth == studyState.moves.size - 1 || (studyState.moves.isEmpty() && depth == 0),
                            onSelected = { option ->
                                if (option.isCurrent) onChipTapped(depth + 1)
                                else onBranchOptionSelected(depth, option.nodeId)
                            },
                        )
                    }
                }
            }
        }
    }
    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * 候補手を評価の高い順に横1行で並べる。狭い画面では右端が切れるため、行内で横スクロールする。
 */
@Composable
private fun StudyCandidateRow(
    candidates: List<StudyCandidate>,
    onCandidateTapped: (String) -> Unit,
) {
    val shogiColors = MaterialTheme.shogiColors
    val evalStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = IbmPlexMonoFamily)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        candidates.forEachIndexed { index, candidate ->
            val isBest = index == 0
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .width(1.dp)
                        .height(14.dp)
                        .background(shogiColors.line),
                )
            }
            Surface(
                modifier = Modifier.clickable { onCandidateTapped(candidate.moveUsi) },
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(4.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        candidate.moveText,
                        // 最善手は紺青・太字・一回り大きく（DESIGN.mdの三色体系で最善手は紺青）。
                        style = if (isBest) {
                            TextStyleDataMove.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        } else {
                            TextStyleDataMove
                        },
                        color = if (isBest) MaterialTheme.colorScheme.primary else shogiColors.ink2,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        candidate.label.text,
                        style = if (isBest) evalStyle.copy(fontWeight = FontWeight.Bold) else evalStyle,
                        color = when {
                            candidate.label.sign > 0 -> MaterialTheme.colorScheme.primary
                            candidate.label.sign < 0 -> shogiColors.loss
                            else -> shogiColors.ink2
                        },
                        maxLines = 1,
                    )
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
        Text(stringResource(Res.string.study_analyze_label))
    }
}

@Composable
private fun StudyMoveRow(
    ply: Int,
    options: List<StudyBranchOption>,
    notation: (StudyBranchOption) -> String,
    isCurrent: Boolean,
    isFuture: Boolean,
    scrollToRow: Boolean,
    onSelected: (StudyBranchOption) -> Unit,
) {
    val colors = MaterialTheme.shogiColors
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val moveWidth = with(density) { (TextStyleDataMove.fontSize.toPx() * 8).toDp() }
    val winSample = stringResource(Res.string.position_eval_win_in, 999)
    val evalWidth = with(density) {
        listOf("+99999%", winSample).maxOf {
            measurer.measure(it, TextStyleDataMove.copy(fontFamily = IbmPlexMonoFamily)).size.width.toDp()
        }
    }
    val optionWidth = moveWidth + evalWidth + 12.dp + 32.dp
    val scroll = rememberScrollState()
    val bringIntoView = remember { BringIntoViewRequester() }
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(scrollToRow, placed) {
        if (scrollToRow && placed) bringIntoView.bringIntoView()
    }
    val selectedIndex = options.indexOfFirst { it.isCurrent }.coerceAtLeast(0)
    LaunchedEffect(selectedIndex, optionWidth, scroll.viewportSize) {
        if (options.size > 1) {
            val left = with(density) { optionWidth.roundToPx() } * selectedIndex
            val right = left + with(density) { optionWidth.roundToPx() }
            when {
                left < scroll.value -> scroll.scrollTo(left)
                right > scroll.value + scroll.viewportSize -> scroll.scrollTo(right - scroll.viewportSize)
            }
        }
    }
    Row(Modifier.fillMaxWidth().height(56.dp).bringIntoViewRequester(bringIntoView).onGloballyPositioned { placed = true }.testTag("study_move_row_$ply")) {
        Box(Modifier.width(42.dp).height(56.dp).background(colors.surface2), contentAlignment = Alignment.Center) {
            Text(ply.toString(), style = TextStyleDataMove.copy(fontFamily = IbmPlexMonoFamily), color = colors.ink3)
        }
        Row(if (options.size > 1) Modifier.weight(1f).horizontalScroll(scroll) else Modifier.weight(1f)) {
            options.forEach { option ->
                val active = isCurrent && option.isCurrent
                val textColor = when {
                    active -> LightInk
                    isFuture -> colors.ink3
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Surface(
                    modifier = (if (options.size > 1) Modifier.width(optionWidth) else Modifier.weight(1f))
                        .height(56.dp).clickable { onSelected(option) }
                        .testTag("study_move_option_${ply}_${option.nodeId}"),
                    color = when {
                        active -> colors.highlight
                        option.isCurrent && options.size > 1 -> colors.highlightSoft
                        else -> MaterialTheme.colorScheme.surface
                    },
                ) {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(notation(option), style = TextStyleDataMove, color = textColor,
                            modifier = Modifier.width(moveWidth), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(if (options.size > 1) Modifier.width(12.dp) else Modifier.weight(1f))
                        Text((option.evalState as? StudyEvalState.Value)?.label?.text
                                ?: stringResource(Res.string.study_branch_eval_unknown),
                            style = TextStyleDataMove.copy(fontFamily = IbmPlexMonoFamily), color = textColor,
                            modifier = Modifier.width(evalWidth), textAlign = TextAlign.End,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    HorizontalDivider(color = colors.line)
}

/** baseSfen から moves を steps 手だけ進めた局面の SFEN を返す（1手でも失敗したらそこで打ち切る）。 */
private fun sfenAfterMoves(baseSfen: String, moves: List<String>, steps: Int): String {
    val board = runCatching { ShogiBoard.fromSfen(baseSfen) }.getOrElse { ShogiBoard() }
    for (i in 0 until steps) {
        runCatching { board.push(ShogiMove.fromUsi(moves[i])) }.onFailure { return board.toSfen() }
    }
    return board.toSfen()
}
