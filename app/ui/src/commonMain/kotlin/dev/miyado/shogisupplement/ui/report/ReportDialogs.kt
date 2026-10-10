package dev.miyado.shogisupplement.ui.report

import org.jetbrains.compose.resources.stringResource
import dev.miyado.shogisupplement.ui.generated.resources.*

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.db.PositionEvalRow
import dev.miyado.shogisupplement.db.openingTagList
import dev.miyado.shogisupplement.ui.common.buildTimeControlLine
import dev.miyado.shogisupplement.ui.common.formatDateTime
import dev.miyado.shogisupplement.ui.theme.shogiColors

@Composable
internal fun GameInfoDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    game: GameRecord,
    playersLine: AnnotatedString,
    /** Web版はローカル保存を持たず編集を保存できないためnullで渡し、ボタン自体を出さない。 */
    onEditPlayers: (() -> Unit)? = null,
) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.game_info_dialog_title)) },
        text = {
            Column {
                // ファイル名（クリップボード取込は「クリップボード 2026-07-15 09:08」形式＝取込元を兼ねる）
                Text(game.fileName, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                // 解析日時（GameCard と同じ formatDateTime(analyzedAt)を使う）。
                // 0は「日時不明」のセンチネル。1970年扱いで表示すると誤情報になるため
                // 行ごと出さない。
                if (game.analyzedAt != 0L) {
                    Text(
                        formatDateTime(game.analyzedAt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.shogiColors.ink2,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(playersLine, style = MaterialTheme.typography.bodyMedium)
                // 1局に複数の戦型が付く（角換わり＋棒銀など）。代表だけ出すと残りが
                // どこからも見えなくなるので、保存した順のまま並べて出す。
                val openingStyles = game.openingTagList().ifEmpty { listOfNotNull(game.openingStyle) }
                if (openingStyles.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${stringResource(Res.string.game_info_opening_style)}：${openingStyles.joinToString("・")}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (game.openingCastle != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${stringResource(Res.string.game_info_opening_castle)}：${game.openingCastle}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                val timeControlLine = buildTimeControlLine(
                    game.sourcePlace,
                    game.timeControlRaw,
                    game.timeControlByoyomiRaw,
                )
                if (timeControlLine != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        buildAnnotatedString {
                            append("${stringResource(Res.string.game_info_time_control)}：")
                            append(timeControlLine)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.game_info_close))
            }
        },
        dismissButton = if (onEditPlayers != null) {
            {
                TextButton(onClick = onEditPlayers) {
                    Text(stringResource(Res.string.game_info_edit_players))
                }
            }
        } else {
            null
        },
    )
}

@Composable
internal fun StudyPromoteDialog(show: Boolean, onDecision: (Boolean) -> Unit) {
    if (!show) return
    AlertDialog(
        onDismissRequest = { onDecision(false) },
        title = { Text(stringResource(Res.string.drill_promote_title)) },
        confirmButton = {
            TextButton(onClick = { onDecision(true) }) { Text(stringResource(Res.string.drill_promote_yes)) }
        },
        dismissButton = {
            TextButton(onClick = { onDecision(false) }) { Text(stringResource(Res.string.drill_promote_no)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoveListBottomSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    sheetMaxHeight: Dp,
    moves: List<String>,
    currentPly: Int,
    positionEvals: List<PositionEvalRow>,
    evalDisplay: String,
    userIsGote: Boolean,
    onSelectPly: (Int) -> Unit,
) {
    if (!show) return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Box(Modifier.heightIn(max = sheetMaxHeight)) {
            MoveListSheet(
                moves = moves,
                currentPly = currentPly,
                positionEvals = positionEvals,
                evalDisplay = evalDisplay,
                userIsGote = userIsGote,
                onSelectPly = onSelectPly,
            )
        }
    }
}
