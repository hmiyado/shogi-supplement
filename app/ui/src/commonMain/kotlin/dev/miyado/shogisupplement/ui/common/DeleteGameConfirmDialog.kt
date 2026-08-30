package dev.miyado.shogisupplement.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.upload.DeleteGameOutcome

@Composable
fun DeleteGameConfirmDialog(
    show: Boolean,
    canDeleteServer: Boolean,
    count: Int = 1,
    onConfirm: (deleteServer: Boolean, onResult: (DeleteGameOutcome) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return

    var deleteServerChecked by remember(show) { mutableStateOf(false) }
    var isDeleting by remember(show) { mutableStateOf(false) }
    var errorText by remember(show) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        title = { Text(AppStrings.gameDeleteDialogTitle(count)) },
        text = {
            Column {
                Text(
                    if (deleteServerChecked) {
                        AppStrings.GAME_DELETE_DIALOG_TEXT_WITH_SERVER
                    } else {
                        AppStrings.GAME_DELETE_DIALOG_TEXT_DEVICE_ONLY
                    },
                )
                if (canDeleteServer) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isDeleting) {
                                deleteServerChecked = !deleteServerChecked
                            },
                    ) {
                        Checkbox(
                            checked = deleteServerChecked,
                            onCheckedChange = { deleteServerChecked = it },
                            enabled = !isDeleting,
                        )
                        Text(AppStrings.GAME_DELETE_SERVER_CHECKBOX_LABEL)
                    }
                }
                if (errorText != null) {
                    Text(
                        text = errorText.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    isDeleting = true
                    errorText = null
                    onConfirm(deleteServerChecked) { outcome ->
                        isDeleting = false
                        when (outcome) {
                            DeleteGameOutcome.Success -> onDismiss()
                            DeleteGameOutcome.ServerFailed -> {
                                errorText = AppStrings.GAME_DELETE_SERVER_ERROR
                            }
                        }
                    }
                },
                enabled = !isDeleting,
            ) {
                Text(
                    text = AppStrings.GAME_DELETE_CONFIRM,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDeleting) {
                Text(AppStrings.CANCEL)
            }
        },
    )
}
