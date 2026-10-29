package dev.miyado.shogisupplement.ui.report

import org.jetbrains.compose.resources.stringResource
import dev.miyado.shogisupplement.ui.generated.resources.*

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.ui.common.exposeTestTags

/** 対局情報ダイアログから開く、対局者名だけの編集ダイアログ。 */
@Composable
internal fun EditPlayersDialog(
    show: Boolean,
    senteName: String?,
    goteName: String?,
    onConfirm: (senteName: String?, goteName: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return
    // showの再トリガーで前回未保存の入力を破棄し、保存済みの値へ初期化し直す。
    var sente by remember(show) { mutableStateOf(senteName.orEmpty()) }
    var gote by remember(show) { mutableStateOf(goteName.orEmpty()) }

    AlertDialog(
        modifier = Modifier.exposeTestTags(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.edit_players_dialog_title)) },
        text = {
            Column {
                // 値が入るとラベルが枠線へ浮き、ラベルの文字をタップしても入力欄へ
                // フォーカスできない。識別子でしか指せないためtestTagを置く。
                OutlinedTextField(
                    value = sente,
                    onValueChange = { sente = it },
                    label = { Text(stringResource(Res.string.edit_players_sente_label)) },
                    singleLine = true,
                    modifier = Modifier.testTag("edit_players_sente"),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = gote,
                    onValueChange = { gote = it },
                    label = { Text(stringResource(Res.string.edit_players_gote_label)) },
                    singleLine = true,
                    modifier = Modifier.testTag("edit_players_gote"),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(sente.trim().ifBlank { null }, gote.trim().ifBlank { null }) }) {
                Text(stringResource(Res.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}
