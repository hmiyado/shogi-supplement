package dev.miyado.shogisupplement.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.text.AppStrings

/**
 * 解析失敗時のエラー画面。VM/Android 非依存の純Composable。
 */
@Composable
fun ErrorScreen(
    message: String,
    pastGames: List<GameRecord>,
    onRetry: () -> Unit,
    onOpenKif: () -> Unit,
    onGameClick: (GameRecord) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(AppStrings.errorMessage(message), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRetry) { Text(AppStrings.DRILL_BACK_HOME) }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onOpenKif) { Text(AppStrings.HOME_OPEN_KIF) }
        if (pastGames.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(AppStrings.HOME_PAST_ANALYSES, style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(pastGames) { game ->
                    GameCard(game = game, onClick = { onGameClick(game) })
                }
            }
        }
    }
}
