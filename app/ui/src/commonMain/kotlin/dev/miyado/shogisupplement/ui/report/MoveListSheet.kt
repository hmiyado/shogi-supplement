package dev.miyado.shogisupplement.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.blunder.PositionEvalDisplay
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.db.PositionEvalRow
import dev.miyado.shogisupplement.notation.JapaneseNotation
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.theme.IbmPlexMonoFamily
import dev.miyado.shogisupplement.ui.theme.shogiColors

/** 棋譜リストシート。全手を和式表記で並べ、現在手を強調して局面移動を提供する。 */
@Composable
fun MoveListSheet(
    moves: List<String>,
    currentPly: Int,
    /** 全局面評価値（先手視点 cp・ply昇順）。空 = 評価値表示なし。 */
    positionEvals: List<PositionEvalRow> = emptyList(),
    /** 形勢の表示単位（"cp" or "wp"）。 */
    evalDisplay: String = "cp",
    /** ユーザーが後手なら true（PositionEvalDisplay の符号反転用）。 */
    userIsGote: Boolean = false,
    onSelectPly: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(currentPly) {
        if (currentPly > 0 && moves.isNotEmpty()) {
            listState.scrollToItem((currentPly - 1).coerceIn(0, moves.lastIndex))
        }
    }
    val shogiColors = MaterialTheme.shogiColors
    Column {
        Text(
            AppStrings.MOVE_LIST_TITLE,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            itemsIndexed(moves) { idx, usiStr ->
                val ply = idx + 1
                val isCurrentPly = ply == currentPly
                val prevSfen = computeSfenAtStep(null, moves, idx)
                val notation = runCatching {
                    JapaneseNotation.format(usiStr, ShogiBoard.fromSfen(prevSfen))
                }.getOrElse { usiStr }
                val bgColor = if (isCurrentPly) shogiColors.highlightSoft else Color.Transparent
                // 各手の評価値ラベル（その手を指した後の局面 = ply と同じ）
                val evalLabel = remember(ply, positionEvals, evalDisplay, userIsGote) {
                    positionEvals.firstOrNull { it.ply == ply }?.let { row ->
                        PositionEvalDisplay.format(
                            scoreCp = row.scoreCp,
                            mateIn = row.mateIn,
                            userIsGote = userIsGote,
                            evalDisplay = evalDisplay,
                            ply = ply,
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bgColor)
                        .clickable { onSelectPly(ply) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${ply}手目",
                        style = MaterialTheme.typography.labelSmall,
                        color = shogiColors.ink3,
                        modifier = Modifier.width(48.dp),
                    )
                    Text(
                        notation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isCurrentPly) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    if (evalLabel != null) {
                        Text(
                            evalLabel.text,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = IbmPlexMonoFamily,
                            ),
                            color = when {
                                evalLabel.sign > 0 -> MaterialTheme.colorScheme.primary
                                evalLabel.sign < 0 -> shogiColors.loss
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
                HorizontalDivider(color = shogiColors.line.copy(alpha = 0.5f))
            }
        }
    }
}
