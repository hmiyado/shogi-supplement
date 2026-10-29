package dev.miyado.shogisupplement.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.ui.generated.resources.Res
import dev.miyado.shogisupplement.ui.generated.resources.game_share_active_note
import dev.miyado.shogisupplement.ui.generated.resources.game_share_create_button
import dev.miyado.shogisupplement.ui.generated.resources.game_share_source_missing
import dev.miyado.shogisupplement.ui.generated.resources.game_share_create_failed
import dev.miyado.shogisupplement.ui.generated.resources.game_share_creating
import dev.miyado.shogisupplement.ui.generated.resources.game_share_description
import dev.miyado.shogisupplement.ui.generated.resources.game_share_privacy_note
import dev.miyado.shogisupplement.ui.generated.resources.game_share_retry
import dev.miyado.shogisupplement.ui.generated.resources.game_share_share
import dev.miyado.shogisupplement.ui.generated.resources.game_share_title
import dev.miyado.shogisupplement.ui.generated.resources.game_share_url_label
import dev.miyado.shogisupplement.ui.generated.resources.close
import dev.miyado.shogisupplement.ui.theme.shogiColors
import org.jetbrains.compose.resources.stringResource

@Composable
fun GameShareDialog(
    state: GameShareUiState,
    onCreate: () -> Unit,
    onShare: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.game_share_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(Res.string.game_share_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.shogiColors.ink2,
                )
                Text(
                    stringResource(Res.string.game_share_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.shogiColors.ink2,
                )
                when (state.phase) {
                    GameSharePhase.Creating -> BusyLine(stringResource(Res.string.game_share_creating))
                    GameSharePhase.None -> Unit
                    GameSharePhase.CreateFailed -> ErrorText(stringResource(Res.string.game_share_create_failed))
                    GameSharePhase.SourceMissing -> ErrorText(stringResource(Res.string.game_share_source_missing))
                    GameSharePhase.Active -> {
                        state.url?.let { ShareUrl(it) }
                        Text(stringResource(Res.string.game_share_active_note), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            when (state.phase) {
                GameSharePhase.None -> TextButton(
                    onClick = onCreate,
                    modifier = Modifier.testTag("game_share_create"),
                ) {
                    Text(stringResource(Res.string.game_share_create_button))
                }
                GameSharePhase.CreateFailed, GameSharePhase.SourceMissing -> TextButton(onClick = onCreate) {
                    Text(stringResource(Res.string.game_share_retry))
                }
                GameSharePhase.Active -> state.url?.let { url ->
                    TextButton(onClick = { onShare(url) }) {
                        Text(stringResource(Res.string.game_share_share))
                    }
                }
                else -> Unit
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) }
        },
    )
}

@Composable
private fun BusyLine(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.padding(2.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun ShareUrl(url: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(Res.string.game_share_url_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.shogiColors.ink2,
        )
        SelectionContainer {
            Text(
                url,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("game_share_url"),
            )
        }
    }
}
