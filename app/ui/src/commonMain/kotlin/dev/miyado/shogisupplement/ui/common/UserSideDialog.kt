package dev.miyado.shogisupplement.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.text.AppStrings

/**
 * 自分の側選択ダイアログ（KIF/SFEN取込フロー共通）。
 * アプリ（Android/iOS）とWeb検討ページの双方から使う。棋力設定が済んでいる場合にのみ呼ばれ、
 * 「自分の側」だけを確認する。
 */
@Composable
fun UserSideDialog(
    senteName: String?,
    goteName: String?,
    savedUserSide: String?,
    onConfirm: (userSide: String?, skipNext: Boolean) -> Unit,
    onDismiss: () -> Unit,
    /** アカウント名一致時のみ true（「次回から省略」チェックボックスを表示）。アプリ専用。 */
    showSkipOption: Boolean = false,
    confirmText: String = AppStrings.START_ANALYSIS,
) {
    // 初期値が null（未選択）の場合は確定操作を無効化する
    var selection by remember { mutableStateOf(savedUserSide) }
    var skipNext by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.SIDE_DIALOG_TITLE) },
        text = {
            Column {
                if (senteName != null || goteName != null) {
                    Text(
                        AppStrings.playersLine(senteName, goteName),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { selection = "sente" },
                ) {
                    RadioButton(selected = selection == "sente", onClick = { selection = "sente" })
                    Text(AppStrings.sideSente(senteName))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { selection = "gote" },
                ) {
                    RadioButton(selected = selection == "gote", onClick = { selection = "gote" })
                    Text(AppStrings.sideGote(goteName))
                }
                if (showSkipOption) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { skipNext = !skipNext },
                    ) {
                        Checkbox(checked = skipNext, onCheckedChange = { skipNext = it })
                        Text(
                            AppStrings.SKIP_SIDE_CONFIRM_CHECKBOX,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selection, skipNext) },
                enabled = selection != null,
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(AppStrings.CANCEL)
            }
        },
    )
}
