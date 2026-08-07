package dev.miyado.shogisupplement.ui.transfercode

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * 引き継ぎコード入力ダイアログ（[TransferCodeInputUiState] 駆動）。
 *
 * [state] が [TransferCodeInputUiState.NeedsConfirmation] のときは入力欄を隠し
 * [TransferCodeSwitchConfirmDialog] を代わりに表示する（呼び出し側で2枚重ねる必要はない。
 * このComposable自体が状態に応じて出し分ける）。
 */
@Composable
fun TransferCodeInputDialog(
    state: TransferCodeInputUiState,
    onSubmit: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancelConfirmation: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (state is TransferCodeInputUiState.NeedsConfirmation) {
        TransferCodeSwitchConfirmDialog(onConfirm = onConfirm, onDismiss = onCancelConfirmation)
        return
    }
    if (state is TransferCodeInputUiState.Success) {
        // ここで無言で閉じると、復元先アカウントに切り替わったのか単にキャンセルしたのか
        // 見分けが付かない（ホームのデータはこの機能単独では変わらない。ダウンロード導線が
        // 無いため。復元アカウントの棋譜再取得は別タスク）。切り替わった事実だけは
        // 明示してから閉じる。
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(AppStrings.TRANSFER_CODE_INPUT_SUCCESS) },
            text = null,
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(AppStrings.TRANSFER_CODE_INPUT_SUCCESS_CLOSE)
                }
            },
        )
        return
    }

    var code by remember { mutableStateOf("") }
    val isRestoring = state is TransferCodeInputUiState.Restoring

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.TRANSFER_CODE_INPUT_TITLE) },
        text = {
            Column {
                Text(AppStrings.TRANSFER_CODE_INPUT_DESCRIPTION, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text(AppStrings.TRANSFER_CODE_INPUT_FIELD_LABEL) },
                    singleLine = true,
                    enabled = !isRestoring,
                )
                if (state is TransferCodeInputUiState.Error) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (isRestoring) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(code) }, enabled = !isRestoring && code.isNotBlank()) {
                Text(AppStrings.TRANSFER_CODE_INPUT_SUBMIT)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isRestoring) {
                Text(AppStrings.CANCEL)
            }
        },
    )
}

/**
 * 既にログイン中のアカウントがある状態で入力した場合の切替確認ダイアログ。
 * 破壊的操作（DESIGN.mdのloss=朱）ではない: 今のアカウントはサーバー上に残り、消えない。
 * そのため確認ボタンにerror色は使わず既定色のままにする。
 */
@Composable
fun TransferCodeSwitchConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.TRANSFER_CODE_INPUT_CONFIRM_TITLE) },
        text = { Text(AppStrings.TRANSFER_CODE_INPUT_CONFIRM_TEXT) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(AppStrings.TRANSFER_CODE_INPUT_CONFIRM_BUTTON)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(AppStrings.CANCEL)
            }
        },
    )
}

// ─── Preview ─────────────────────────────────────────────────────────────────

@Preview
@Composable
private fun PreviewTransferCodeInputDialog() {
    ShogiTheme {
        TransferCodeInputDialog(
            state = TransferCodeInputUiState.Idle,
            onSubmit = {},
            onConfirm = {},
            onCancelConfirmation = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun PreviewTransferCodeInputDialogError() {
    ShogiTheme {
        TransferCodeInputDialog(
            state = TransferCodeInputUiState.Error(AppStrings.TRANSFER_CODE_INPUT_ERROR_NOT_FOUND),
            onSubmit = {},
            onConfirm = {},
            onCancelConfirmation = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun PreviewTransferCodeInputDialogSuccess() {
    ShogiTheme {
        TransferCodeInputDialog(
            state = TransferCodeInputUiState.Success,
            onSubmit = {},
            onConfirm = {},
            onCancelConfirmation = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun PreviewTransferCodeSwitchConfirmDialog() {
    ShogiTheme {
        TransferCodeSwitchConfirmDialog(onConfirm = {}, onDismiss = {})
    }
}
