package dev.miyado.shogisupplement.ui.restore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.theme.IbmPlexMonoFamily
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import dev.miyado.shogisupplement.ui.theme.shogiColors
import org.jetbrains.compose.ui.tooling.preview.Preview

/** 状態が入れ替わってもレイアウト高さが動かないための固定スロット高（DESIGN.md No-jitter原則）。 */
private val STATUS_SLOT_HEIGHT = 56.dp

/**
 * 復元成功画面（引き継ぎコード復元後に遷移）。
 *
 * 見出しは引き継ぎコード入力成功と同じ文言（[AppStrings.TRANSFER_CODE_INPUT_SUCCESS]）を
 * 再利用する。件数表示・進捗・完了はすべて[STATUS_SLOT_HEIGHT]の固定スロット内で
 * 排他的に入れ替える。ボタン行は常に「ホームへ」（ghost・常時操作可）と主ボタン
 * （件数確認中/復元完了以外は棋譜を復元する or 再試行）の2つを固定表示する。
 */
@Composable
fun GameRestoreScreen(
    state: GameRestoreUiState,
    onStart: () -> Unit = {},
    onRetry: () -> Unit = {},
    onFinish: () -> Unit = {},
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = AppStrings.TRANSFER_CODE_INPUT_SUCCESS,
                style = MaterialTheme.typography.headlineSmall,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(STATUS_SLOT_HEIGHT)
                    .testTag("game_restore_status_slot"),
                contentAlignment = Alignment.CenterStart,
            ) {
                StatusSlotContent(state)
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onFinish,
                    enabled = state !is GameRestoreUiState.Downloading,
                    modifier = Modifier.testTag("game_restore_home_button"),
                ) {
                    Text(AppStrings.DRILL_GO_HOME)
                }
                Button(
                    onClick = {
                        when (state) {
                            is GameRestoreUiState.Ready -> onStart()
                            is GameRestoreUiState.Error -> onRetry()
                            else -> Unit
                        }
                    },
                    enabled = primaryButtonEnabled(state),
                    modifier = Modifier.weight(1f).testTag("game_restore_primary_button"),
                ) {
                    Text(if (state is GameRestoreUiState.Error) AppStrings.GAME_RESTORE_RETRY_BUTTON else AppStrings.GAME_RESTORE_BUTTON)
                }
            }
        }
    }
}

private fun primaryButtonEnabled(state: GameRestoreUiState): Boolean = when (state) {
    is GameRestoreUiState.Ready -> state.count > 0
    is GameRestoreUiState.Error -> true
    else -> false
}

@Composable
private fun StatusSlotContent(state: GameRestoreUiState) {
    when (state) {
        GameRestoreUiState.Loading -> LabeledSpinner(AppStrings.GAME_RESTORE_LOADING_NOTE)
        is GameRestoreUiState.Ready -> {
            if (state.count > 0) {
                MonoStatusText(AppStrings.gameRestoreCount(state.count))
            } else {
                Text(AppStrings.GAME_RESTORE_EMPTY_NOTE, style = MaterialTheme.typography.bodyMedium)
            }
        }
        is GameRestoreUiState.Downloading ->
            LabeledSpinner(AppStrings.gameRestoreProgress(state.done, state.total), mono = true)
        is GameRestoreUiState.Completed -> {
            val text = if (state.failed > 0) {
                AppStrings.gameRestoreCompletedPartial(state.succeeded, state.failed)
            } else {
                AppStrings.gameRestoreCompletedAll(state.succeeded)
            }
            MonoStatusText(text, color = if (state.failed > 0) MaterialTheme.shogiColors.loss else null)
        }
        is GameRestoreUiState.Error ->
            Text(state.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.shogiColors.loss)
    }
}

@Composable
private fun LabeledSpinner(text: String, mono: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        if (mono) MonoStatusText(text) else Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MonoStatusText(text: String, color: Color? = null) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        fontFamily = IbmPlexMonoFamily,
        color = color ?: MaterialTheme.colorScheme.onSurface,
    )
}

// ─── Preview ─────────────────────────────────────────────────────────────────

@Preview
@Composable
private fun PreviewGameRestoreScreenReady() {
    ShogiTheme {
        GameRestoreScreen(state = GameRestoreUiState.Ready(count = 12))
    }
}

@Preview
@Composable
private fun PreviewGameRestoreScreenLoading() {
    ShogiTheme {
        GameRestoreScreen(state = GameRestoreUiState.Loading)
    }
}

@Preview
@Composable
private fun PreviewGameRestoreScreenDownloading() {
    ShogiTheme {
        GameRestoreScreen(state = GameRestoreUiState.Downloading(done = 3, total = 12))
    }
}

@Preview
@Composable
private fun PreviewGameRestoreScreenCompleted() {
    ShogiTheme {
        GameRestoreScreen(state = GameRestoreUiState.Completed(total = 12, succeeded = 12, failed = 0))
    }
}

@Preview
@Composable
private fun PreviewGameRestoreScreenCompletedPartial() {
    ShogiTheme {
        GameRestoreScreen(state = GameRestoreUiState.Completed(total = 12, succeeded = 10, failed = 2))
    }
}

@Preview
@Composable
private fun PreviewGameRestoreScreenEmpty() {
    ShogiTheme {
        GameRestoreScreen(state = GameRestoreUiState.Ready(count = 0))
    }
}

@Preview
@Composable
private fun PreviewGameRestoreScreenError() {
    ShogiTheme {
        GameRestoreScreen(state = GameRestoreUiState.Error(AppStrings.GAME_RESTORE_ERROR_NETWORK))
    }
}
