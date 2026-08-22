package dev.miyado.shogisupplement.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import dev.miyado.shogisupplement.text.AppStrings

@Composable
fun DeleteGameConfirmDialog(
    show: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppStrings.GAME_DELETE_DIALOG_TITLE) },
        text = { Text(AppStrings.GAME_DELETE_DIALOG_TEXT) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = AppStrings.GAME_DELETE_CONFIRM,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(AppStrings.CANCEL)
            }
        },
    )
}
