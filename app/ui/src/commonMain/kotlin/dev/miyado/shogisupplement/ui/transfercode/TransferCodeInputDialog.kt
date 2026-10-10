package dev.miyado.shogisupplement.ui.transfercode

import dev.miyado.shogisupplement.ui.text.UiStrings

import org.jetbrains.compose.resources.stringResource
import dev.miyado.shogisupplement.ui.generated.resources.*

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
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/** 引き継ぎコード入力ダイアログ。NeedsConfirmationでは確認ダイアログへ切り替える。 */
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
        // 無言で閉じると切り替えとキャンセルを区別できないため、切り替えを明示して閉じる。
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.transfer_code_input_success)) },
            text = null,
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.transfer_code_input_success_close))
                }
            },
        )
        return
    }

    var code by remember { mutableStateOf("") }
    val isRestoring = state is TransferCodeInputUiState.Restoring

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.transfer_code_input_title)) },
        text = {
            Column {
                Text(stringResource(Res.string.transfer_code_input_description), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text(stringResource(Res.string.transfer_code_input_field_label)) },
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
                Text(stringResource(Res.string.transfer_code_input_submit))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isRestoring) {
                Text(stringResource(Res.string.cancel))
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
        title = { Text(stringResource(Res.string.transfer_code_input_confirm_title)) },
        text = { Text(UiStrings.TRANSFER_CODE_INPUT_CONFIRM_TEXT) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.transfer_code_input_confirm_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
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
            state = TransferCodeInputUiState.Error(stringResource(Res.string.transfer_code_input_error_not_found)),
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
