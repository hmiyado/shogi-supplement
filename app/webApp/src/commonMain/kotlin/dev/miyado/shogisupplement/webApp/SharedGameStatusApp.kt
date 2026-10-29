package dev.miyado.shogisupplement.webApp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.ui.common.ShogiThinTopBar
import dev.miyado.shogisupplement.ui.common.adaptiveContentWidth
import dev.miyado.shogisupplement.ui.generated.resources.Res
import dev.miyado.shogisupplement.ui.generated.resources.game_share_retry
import dev.miyado.shogisupplement.ui.generated.resources.shared_game_not_found
import dev.miyado.shogisupplement.ui.generated.resources.shared_game_invalid_url
import dev.miyado.shogisupplement.ui.generated.resources.shared_game_load_failed
import dev.miyado.shogisupplement.ui.generated.resources.shared_game_title
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import dev.miyado.shogisupplement.ui.theme.shogiColors
import org.jetbrains.compose.resources.stringResource

@Composable
fun SharedGameStatusApp(
    state: SharedGameLoadState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    ShogiTheme {
        WebAppSurface {
            val title = stringResource(Res.string.shared_game_title)
            Column(Modifier.fillMaxSize()) {
                ShogiThinTopBar(title = title, onBack = onBack)
                when (state) {
                    SharedGameLoadState.Loading -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                    SharedGameLoadState.Missing -> Message(stringResource(Res.string.shared_game_not_found))
                    SharedGameLoadState.Invalid -> Message(stringResource(Res.string.shared_game_invalid_url))
                    SharedGameLoadState.Failed -> Column(
                        Modifier.fillMaxSize().adaptiveContentWidth().padding(16.dp),
                    ) {
                        Text(
                            stringResource(Res.string.shared_game_load_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.shogiColors.ink2,
                        )
                        TextButton(onClick = onRetry) {
                            Text(stringResource(Res.string.game_share_retry))
                        }
                    }
                    is SharedGameLoadState.Ready -> Unit
                }
            }
        }
    }
}

@Composable
private fun Message(message: String) {
    Box(Modifier.fillMaxSize().adaptiveContentWidth().padding(16.dp)) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.shogiColors.ink2,
        )
    }
}
