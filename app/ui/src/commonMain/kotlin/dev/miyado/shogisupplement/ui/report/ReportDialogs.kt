package dev.miyado.shogisupplement.ui.report

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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.db.PositionEvalRow
import dev.miyado.shogisupplement.db.openingTagList
import dev.miyado.shogisupplement.kifu.TimeControlKind
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.common.formatDateTime
import dev.miyado.shogisupplement.ui.theme.IbmPlexMonoFamily
import dev.miyado.shogisupplement.ui.theme.shogiColors

@Composable
internal fun GameInfoDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    game: GameRecord,
    playersLine: AnnotatedString,
) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.GAME_INFO_DIALOG_TITLE) },
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
                        "${AppStrings.GAME_INFO_OPENING_STYLE}：${openingStyles.joinToString("・")}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (game.openingCastle != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${AppStrings.GAME_INFO_OPENING_CASTLE}：${game.openingCastle}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                val timeControlLine = buildTimeControlLine(
                    game.timeControlKind,
                    game.timeControlBaseMinutes,
                    game.timeControlIncrementSeconds,
                )
                if (timeControlLine != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        buildAnnotatedString {
                            append("${AppStrings.GAME_INFO_TIME_CONTROL}：")
                            append(timeControlLine)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(AppStrings.GAME_INFO_CLOSE)
            }
        },
    )
}

/** 対局情報ダイアログの持ち時間行。判定できない（kind・baseMinutes未確定）場合はnull。 */
private fun buildTimeControlLine(kind: String?, baseMinutes: Long?, incrementSeconds: Long?): AnnotatedString? {
    if (baseMinutes == null) return null
    val timeControlKind = TimeControlKind.entries.firstOrNull { it.wireValue == kind } ?: return null
    if (timeControlKind != TimeControlKind.SUDDEN_DEATH && incrementSeconds == null) return null
    return buildAnnotatedString {
        fun AnnotatedString.Builder.appendMono(value: Long) {
            withStyle(SpanStyle(fontFamily = IbmPlexMonoFamily)) { append(value.toString()) }
        }
        fun AnnotatedString.Builder.appendMono(text: String) {
            withStyle(SpanStyle(fontFamily = IbmPlexMonoFamily)) { append(text) }
        }
        when (timeControlKind) {
            TimeControlKind.FISCHER -> {
                appendMono(baseMinutes)
                append(AppStrings.TIME_CONTROL_FISCHER_MID)
                appendMono("+${incrementSeconds!!}")
                append(AppStrings.TIME_CONTROL_FISCHER_SUFFIX)
            }
            TimeControlKind.SUDDEN_DEATH -> {
                appendMono(baseMinutes)
                append(AppStrings.TIME_CONTROL_SUDDEN_DEATH_SUFFIX)
            }
            TimeControlKind.BYOYOMI -> {
                if (baseMinutes > 0) {
                    appendMono(baseMinutes)
                    append(AppStrings.TIME_CONTROL_BYOYOMI_MID)
                } else {
                    append(AppStrings.TIME_CONTROL_BYOYOMI_ONLY_PREFIX)
                }
                appendMono(incrementSeconds!!)
                append(AppStrings.TIME_CONTROL_SECONDS_SUFFIX)
            }
        }
    }
}

@Composable
internal fun StudyPromoteDialog(show: Boolean, onDecision: (Boolean) -> Unit) {
    if (!show) return
    AlertDialog(
        onDismissRequest = { onDecision(false) },
        title = { Text(AppStrings.DRILL_PROMOTE_TITLE) },
        confirmButton = {
            TextButton(onClick = { onDecision(true) }) { Text(AppStrings.DRILL_PROMOTE_YES) }
        },
        dismissButton = {
            TextButton(onClick = { onDecision(false) }) { Text(AppStrings.DRILL_PROMOTE_NO) }
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
