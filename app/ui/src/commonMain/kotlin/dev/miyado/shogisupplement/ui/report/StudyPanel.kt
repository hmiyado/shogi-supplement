package dev.miyado.shogisupplement.ui.report

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.notation.JapaneseNotation
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.theme.ShipporiMinchoFamily
import dev.miyado.shogisupplement.ui.theme.TextStyleDataMove
import dev.miyado.shogisupplement.ui.theme.shogiColors

/**
 * 検討モードのパネル（罫線から下の「グラフ＋サマリー」領域を検討中はこれに丸ごと入れ替える）。
 *
 * 外形は呼び出し側の `Modifier.fillMaxSize()` で非検討時の SUMMARY 領域と同じ高さになる
 * （高さを個別に計算して揃えているのではなく、同じ排他スロットを共有することで構造的に
 * 一致させている。DESIGN.md No-jitter原則）。
 *
 * 内部は上から: 見出し（「検討中」＋終了ボタン）／分岐元行／手順チップ列（横スクロール・
 * 固定高さ）／可変の空白／評価スロット（下端固定・固定高さ）。手順チップ列を横スクロール
 * にしているのは、折り返し（FlowRow）だと分岐の増減や手数でパネルの外形自体が変わって
 * しまうため（「パネル外形は不変」要件を満たすには行数が変わらないことが必須）。
 */
@Composable
internal fun StudyPanel(
    studyState: StudyState,
    onEnd: () -> Unit,
    onChipTapped: (Int) -> Unit,
    onBranchChipTapped: (Int) -> Unit,
    onBranchPopupDismiss: () -> Unit,
    onBranchOptionSelected: (depth: Int, moveUsi: String) -> Unit,
    onAnalyze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shogiColors = MaterialTheme.shogiColors
    val notations = remember(studyState.baseSfen, studyState.moves) {
        buildMoveNotations(studyState.baseSfen, studyState.moves)
    }

    Card(
        modifier = modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            // ── 見出し: 「検討中」（Mincho・primary）＋ 終了ボタン ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = AppStrings.STUDY_PANEL_TITLE,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = ShipporiMinchoFamily,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onEnd) { Text(AppStrings.STUDY_END) }
            }

            Spacer(Modifier.height(8.dp))

            // ── 分岐元行: 「42手目 ▲３四飛（−320）から分岐」 ──
            Text(
                text = AppStrings.studyOriginLine(studyState.origin.label),
                style = MaterialTheme.typography.bodySmall,
                color = shogiColors.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(8.dp))

            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .horizontalScroll(scrollState),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                studyState.moves.forEachIndexed { depth, moveUsi ->
                    val isCurrent = depth == studyState.moves.lastIndex
                    val hasBranch = studyState.branchFlags.getOrNull(depth) == true
                    Box {
                        StudyMoveChip(
                            label = notations.getOrElse(depth) { moveUsi },
                            isCurrent = isCurrent,
                            hasBranch = hasBranch,
                            onClick = {
                                if (hasBranch) onBranchChipTapped(depth) else onChipTapped(depth + 1)
                            },
                        )
                        DropdownMenu(
                            expanded = studyState.openBranchPopupDepth == depth,
                            onDismissRequest = onBranchPopupDismiss,
                        ) {
                            studyState.branchPopupOptions.forEach { option ->
                                val optionEvalText = (option.evalState as? StudyEvalState.Value)
                                    ?.label?.text ?: AppStrings.STUDY_BRANCH_EVAL_UNKNOWN
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "${option.moveUsi}  $optionEvalText" +
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

            // 可変の空白: 評価スロットを下端に固定するための伸縮領域（margin-top:autoに相当）。
            Spacer(Modifier.weight(1f))

            HorizontalDivider(color = shogiColors.line)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                when (val es = studyState.evalState) {
                    StudyEvalState.None, StudyEvalState.Error -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (es is StudyEvalState.Error) {
                                Text(
                                    AppStrings.evalSuffix(AppStrings.EVAL_UNAVAILABLE),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = shogiColors.ink2,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            OutlinedButton(
                                onClick = onAnalyze,
                                enabled = studyState.moves.isNotEmpty(),
                            ) { Text(AppStrings.STUDY_ANALYZE_BUTTON) }
                        }
                    }
                    StudyEvalState.Loading -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(AppStrings.EVAL_LOADING, style = MaterialTheme.typography.bodySmall, color = shogiColors.ink2)
                        }
                    }
                    is StudyEvalState.Value -> {
                        val diff = es.userCp?.let { cur -> studyState.origin.userCp?.let { origin -> cur - origin } }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column {
                                Text(
                                    text = es.label.text,
                                    style = TextStyleDataMove,
                                    color = if (es.label.sign > 0) MaterialTheme.colorScheme.primary else if (es.label.sign < 0) shogiColors.loss else shogiColors.ink2,
                                )
                                Text(
                                    AppStrings.STUDY_EVAL_CURRENT_CAPTION,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = shogiColors.ink3,
                                )
                            }
                            if (diff != null) {
                                Column {
                                    Text(
                                        text = AppStrings.cpSignedLabel(diff),
                                        style = TextStyleDataMove,
                                        color = if (diff > 0) MaterialTheme.colorScheme.primary else if (diff < 0) shogiColors.loss else shogiColors.ink2,
                                    )
                                    Text(
                                        AppStrings.STUDY_EVAL_DIFF_CAPTION,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = shogiColors.ink3,
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
 * 手順チップ1つ（現在手=highlight背景、分岐あり=primary枠＋末尾に下向きチェブロン）。
 *
 * 分岐マークは絵文字/記号（⑂等）ではなく Material の KeyboardArrowDown アイコンを使う
 * （miyadoさん指定: フォントカバレッジの差でiOS/Androidの見た目が割れるのを避けるため）。
 * ▽▼は後手記号と衝突するため使わない。
 */
@Composable
private fun StudyMoveChip(
    label: String,
    isCurrent: Boolean,
    hasBranch: Boolean,
    onClick: () -> Unit,
) {
    val shogiColors = MaterialTheme.shogiColors
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
            Text(text = label, style = TextStyleDataMove, maxLines = 1)
            if (hasBranch) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = AppStrings.STUDY_BRANCH_ICON_DESC,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp).padding(start = 2.dp),
                )
            }
        }
    }
}

/**
 * baseSfen から moves を順に指したときの各手の日本語表記を返す（moves と同じ長さ）。
 * ReportScreen.buildCurrentMoveLabel と同じ変換（JapaneseNotation.format）を手順全体に適用する。
 * 失敗した手は USI 文字列のままフォールバックする。
 */
private fun buildMoveNotations(baseSfen: String, moves: List<String>): List<String> {
    val board = runCatching { ShogiBoard.fromSfen(baseSfen) }.getOrNull() ?: return moves
    val result = mutableListOf<String>()
    for (usi in moves) {
        val notation = runCatching { JapaneseNotation.format(usi, board) }.getOrElse { usi }
        result.add(notation)
        runCatching { board.push(ShogiMove.fromUsi(usi)) }.onFailure { return result + moves.drop(result.size) }
    }
    return result
}
